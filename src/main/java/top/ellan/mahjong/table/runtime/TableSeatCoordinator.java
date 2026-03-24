package top.ellan.mahjong.table.runtime;

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.DisplayClickAction.ActionType;
import top.ellan.mahjong.render.display.TableDisplayRegistry;
import top.ellan.mahjong.table.core.MahjongTableManager;
import top.ellan.mahjong.table.core.MahjongTableSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import top.ellan.mahjong.runtime.PluginTask;

public final class TableSeatCoordinator {
    private static final long SEAT_WATCHDOG_PERIOD_TICKS = 2L;
    private static final long SEAT_WATCHDOG_DURATION_TICKS = 40L;
    private static final long SEAT_RESTORE_COOLDOWN_MILLIS = 150L;
    private static final double SEAT_RESTORE_MAX_DISTANCE_SQUARED = 16.0D;

    private final MahjongPaperPlugin plugin;
    private final MahjongTableManager tableManager;
    private final Map<UUID, SeatWatchdogBinding> seatWatchdogs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> seatRestoreCooldownUntilMillis = new ConcurrentHashMap<>();
    private final Set<UUID> seatDismountBypass = ConcurrentHashMap.newKeySet();
    private final AtomicLong seatWatchdogClock = new AtomicLong();
    private PluginTask seatWatchdogTask;

    public TableSeatCoordinator(MahjongPaperPlugin plugin, MahjongTableManager tableManager) {
        this.plugin = plugin;
        this.tableManager = tableManager;
    }

    public void shutdown() {
        if (this.seatWatchdogTask != null) {
            this.seatWatchdogTask.cancel();
            this.seatWatchdogTask = null;
        }
        this.seatWatchdogs.clear();
        this.seatWatchdogClock.set(0L);
        this.seatRestoreCooldownUntilMillis.clear();
        this.seatDismountBypass.clear();
    }

    public DisplayClickAction seatAction(Entity entity) {
        if (entity == null) {
            return null;
        }
        DisplayClickAction direct = TableDisplayRegistry.get(entity.getEntityId());
        if (direct != null && (direct.actionType() == ActionType.JOIN_SEAT || direct.actionType() == ActionType.TOGGLE_READY)) {
            return direct;
        }
        if (this.plugin.craftEngine() == null) {
            return null;
        }
        Entity furniture = null;
        if (this.plugin.craftEngine().isFurnitureEntity(entity)) {
            furniture = entity;
        } else if (this.plugin.craftEngine().isSeatEntity(entity)) {
            furniture = this.plugin.craftEngine().furnitureEntityForSeat(entity);
        }
        if (furniture == null) {
            return null;
        }
        DisplayClickAction action = TableDisplayRegistry.get(furniture.getEntityId());
        if (action == null) {
            return null;
        }
        return switch (action.actionType()) {
            case JOIN_SEAT, TOGGLE_READY -> action;
            default -> null;
        };
    }

    public boolean consumeDismountBypass(UUID playerId) {
        return playerId != null && this.seatDismountBypass.remove(playerId);
    }

    public void ejectSeatOccupant(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        this.plugin.scheduler().runEntity(player, () -> {
            if (!player.isOnline() || !player.isInsideVehicle()) {
                return;
            }
            Entity vehicle = player.getVehicle();
            if (this.seatAction(vehicle) == null) {
                return;
            }
            this.seatDismountBypass.add(playerId);
            player.leaveVehicle();
        });
    }

    public void requestSeatRestore(Player player, MahjongTableSession session, SeatWind wind) {
        if (player == null || session == null || wind == null || this.plugin.craftEngine() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!this.tryEnterSeatRestoreCooldown(playerId)) {
            return;
        }
        this.plugin.scheduler().runEntity(player, () -> this.restoreSeatOnPlayerThread(player, playerId, session, wind));
    }

    public void movePlayerToSeatExit(UUID playerId, MahjongTableSession session, SeatWind wind) {
        if (playerId == null || session == null || wind == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        Location seatAnchor = session.seatAnchorLocation(wind);
        Location tableCenter = session.center();
        if (seatAnchor.getWorld() == null || tableCenter.getWorld() == null || !seatAnchor.getWorld().equals(tableCenter.getWorld())) {
            return;
        }
        double deltaX = seatAnchor.getX() - tableCenter.getX();
        double deltaZ = seatAnchor.getZ() - tableCenter.getZ();
        double horizontalLength = Math.hypot(deltaX, deltaZ);
        double offsetX = horizontalLength > 0.0001D ? deltaX / horizontalLength * 0.8D : 0.0D;
        double offsetZ = horizontalLength > 0.0001D ? deltaZ / horizontalLength * 0.8D : 0.0D;
        Location exit = seatAnchor.clone().add(offsetX, 1.05D, offsetZ);
        exit.setYaw(session.seatFacingYaw(wind));
        exit.setPitch(0.0F);
        this.plugin.scheduler().runEntity(player, () -> {
            if (player.isOnline() && !player.isInsideVehicle()) {
                this.plugin.scheduler().teleport(player, exit);
            }
        });
    }

    public void startSeatWatchdog(MahjongTableSession session) {
        this.startSeatWatchdog(session, SEAT_WATCHDOG_DURATION_TICKS);
    }

    public void startSeatWatchdog(MahjongTableSession session, long durationTicks) {
        if (session == null || durationTicks <= 0L) {
            return;
        }
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = session.playerAt(wind);
            if (playerId == null || session.isBot(playerId)) {
                continue;
            }
            this.startSeatWatchdog(session, playerId, wind, durationTicks);
        }
    }

    public void startSeatWatchdog(MahjongTableSession session, UUID playerId, SeatWind wind) {
        this.startSeatWatchdog(session, playerId, wind, SEAT_WATCHDOG_DURATION_TICKS);
    }

    public void startSeatWatchdog(MahjongTableSession session, UUID playerId, SeatWind wind, long durationTicks) {
        if (session == null || playerId == null || wind == null || durationTicks <= 0L) {
            return;
        }
        long expiresAtTick = this.seatWatchdogClock.get() + durationTicks;
        SeatWatchdogBinding existing = this.seatWatchdogs.get(playerId);
        if (existing == null || existing.expiresAtTick() < expiresAtTick) {
            this.seatWatchdogs.put(playerId, new SeatWatchdogBinding(session.id(), wind, expiresAtTick));
        }
        this.ensureSeatWatchdogTask();
    }

    private void ensureSeatWatchdogTask() {
        if (this.seatWatchdogTask != null && !this.seatWatchdogTask.isCancelled()) {
            return;
        }
        this.seatWatchdogTask = this.plugin.scheduler().runGlobalTimer(this::runSeatWatchdogs, 1L, SEAT_WATCHDOG_PERIOD_TICKS);
    }

    private void runSeatWatchdogs() {
        if (this.seatWatchdogs.isEmpty()) {
            this.stopSeatWatchdogTask();
            return;
        }
        long nowTick = this.seatWatchdogClock.updateAndGet(current -> current + SEAT_WATCHDOG_PERIOD_TICKS);
        for (Map.Entry<UUID, SeatWatchdogBinding> entry : this.seatWatchdogs.entrySet()) {
            UUID playerId = entry.getKey();
            SeatWatchdogBinding binding = entry.getValue();
            MahjongTableSession session = this.tableManager.resolveTableById(binding.tableId());
            if (session == null || !session.isStarted() || session.seatOf(playerId) != binding.wind() || binding.expiresAtTick() < nowTick) {
                this.seatWatchdogs.remove(playerId, binding);
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                this.seatWatchdogs.remove(playerId, binding);
                continue;
            }
            this.plugin.scheduler().runEntity(player, () -> this.inspectSeatWatchdogOnPlayerThread(player, playerId, binding));
        }
        if (this.seatWatchdogs.isEmpty()) {
            this.stopSeatWatchdogTask();
        }
    }

    private void stopSeatWatchdogTask() {
        if (this.seatWatchdogTask != null) {
            this.seatWatchdogTask.cancel();
            this.seatWatchdogTask = null;
        }
    }

    private boolean tryEnterSeatRestoreCooldown(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long cooldownUntil = this.seatRestoreCooldownUntilMillis.get(playerId);
        if (cooldownUntil != null && cooldownUntil > now) {
            return false;
        }
        this.seatRestoreCooldownUntilMillis.put(playerId, now + SEAT_RESTORE_COOLDOWN_MILLIS);
        return true;
    }

    private void inspectSeatWatchdogOnPlayerThread(Player player, UUID playerId, SeatWatchdogBinding binding) {
        if (player == null || playerId == null || binding == null) {
            return;
        }
        SeatWatchdogBinding currentBinding = this.seatWatchdogs.get(playerId);
        if (!binding.equals(currentBinding)) {
            return;
        }
        MahjongTableSession session = this.tableManager.resolveTableById(binding.tableId());
        long nowTick = this.seatWatchdogClock.get();
        if (session == null || !session.isStarted() || session.seatOf(playerId) != binding.wind() || binding.expiresAtTick() < nowTick) {
            this.seatWatchdogs.remove(playerId, binding);
            return;
        }
        if (!player.isOnline()) {
            this.seatWatchdogs.remove(playerId, binding);
            return;
        }
        if (!this.isPlayerSeatedAt(player, session, binding.wind()) && this.tryEnterSeatRestoreCooldown(playerId)) {
            this.restoreSeatOnPlayerThread(player, playerId, session, binding.wind());
        }
    }

    private boolean isPlayerSeatedAt(Player player, MahjongTableSession session, SeatWind wind) {
        if (player == null || session == null || wind == null || !player.isInsideVehicle()) {
            return false;
        }
        DisplayClickAction action = this.seatAction(player.getVehicle());
        return action != null
            && action.seatWind() == wind
            && session.id().equals(action.tableId());
    }

    private void restoreSeatOnPlayerThread(Player player, UUID playerId, MahjongTableSession session, SeatWind wind) {
        if (player == null || playerId == null || session == null || wind == null || this.plugin.craftEngine() == null) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        if (this.tableManager.tableFor(playerId) != session || player.isInsideVehicle()) {
            return;
        }
        Location seatAnchor = session.seatAnchorLocation(wind);
        if (!this.isSeatRestoreInRange(player, seatAnchor)) {
            return;
        }
        this.restoreSeatViaAnchorRegion(player, playerId, session, wind, seatAnchor);
    }

    private void restoreSeatViaAnchorRegion(Player player, UUID playerId, MahjongTableSession session, SeatWind wind, Location seatAnchor) {
        if (player == null || playerId == null || session == null || wind == null || seatAnchor == null) {
            return;
        }
        Location anchor = seatAnchor.clone();
        this.plugin.scheduler().runRegion(anchor, () -> {
            Entity furniture = this.findSeatFurnitureAtAnchor(session, wind, anchor);
            if (furniture == null) {
                return;
            }
            this.plugin.scheduler().runEntity(player, () -> this.mountPlayerIfStillEligible(player, playerId, session, wind, anchor, furniture));
        });
    }

    private void mountPlayerIfStillEligible(
        Player player,
        UUID playerId,
        MahjongTableSession session,
        SeatWind wind,
        Location anchor,
        Entity furniture
    ) {
        if (player == null || playerId == null || session == null || wind == null || anchor == null || furniture == null || this.plugin.craftEngine() == null) {
            return;
        }
        if (!player.isOnline() || player.isInsideVehicle()) {
            return;
        }
        if (this.tableManager.tableFor(playerId) != session || session.seatOf(playerId) != wind) {
            return;
        }
        if (!this.isSeatRestoreInRange(player, anchor)) {
            return;
        }
        this.plugin.craftEngine().seatPlayerOnFurniture(furniture, player);
    }

    private boolean isSeatRestoreInRange(Player player, Location seatAnchor) {
        if (player == null || seatAnchor == null || seatAnchor.getWorld() == null) {
            return false;
        }
        Location playerLocation = player.getLocation();
        World playerWorld = playerLocation.getWorld();
        if (playerWorld == null || !playerWorld.equals(seatAnchor.getWorld())) {
            return false;
        }
        return playerLocation.distanceSquared(seatAnchor) <= SEAT_RESTORE_MAX_DISTANCE_SQUARED;
    }

    private Entity findSeatFurnitureAtAnchor(MahjongTableSession session, SeatWind wind, Location anchor) {
        if (session == null || wind == null || anchor == null || this.plugin.craftEngine() == null) {
            return null;
        }
        World world = anchor.getWorld();
        if (world == null) {
            return null;
        }
        for (Entity entity : world.getNearbyEntities(anchor, 1.6D, 1.6D, 1.6D)) {
            if (!this.plugin.craftEngine().isFurnitureEntity(entity)) {
                continue;
            }
            DisplayClickAction action = TableDisplayRegistry.get(entity.getEntityId());
            if (action == null || !session.id().equals(action.tableId()) || action.seatWind() != wind) {
                continue;
            }
            if (action.actionType() == ActionType.JOIN_SEAT || action.actionType() == ActionType.TOGGLE_READY) {
                return entity;
            }
        }
        return null;
    }

    private record SeatWatchdogBinding(String tableId, SeatWind wind, long expiresAtTick) {
    }
}


