package doublemoon.mahjongcraft.paper.table;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.DisplayClickAction;
import doublemoon.mahjongcraft.paper.render.DisplayClickAction.ActionType;
import doublemoon.mahjongcraft.paper.render.TableDisplayRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

final class TableSeatCoordinator {
    private static final long SEAT_WATCHDOG_PERIOD_TICKS = 2L;
    private static final long SEAT_WATCHDOG_DURATION_TICKS = 40L;
    private static final long SEAT_RESTORE_COOLDOWN_MILLIS = 150L;

    private final MahjongPaperPlugin plugin;
    private final MahjongTableManager tableManager;
    private final Map<UUID, SeatWatchdogBinding> seatWatchdogs = new HashMap<>();
    private final Map<UUID, Long> seatRestoreCooldownUntilMillis = new HashMap<>();
    private final Set<UUID> seatDismountBypass = new HashSet<>();
    private BukkitTask seatWatchdogTask;

    TableSeatCoordinator(MahjongPaperPlugin plugin, MahjongTableManager tableManager) {
        this.plugin = plugin;
        this.tableManager = tableManager;
    }

    void shutdown() {
        if (this.seatWatchdogTask != null) {
            this.seatWatchdogTask.cancel();
            this.seatWatchdogTask = null;
        }
        this.seatWatchdogs.clear();
        this.seatRestoreCooldownUntilMillis.clear();
        this.seatDismountBypass.clear();
    }

    DisplayClickAction seatAction(Entity entity) {
        if (entity == null || this.plugin.craftEngine() == null) {
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

    boolean consumeDismountBypass(UUID playerId) {
        return playerId != null && this.seatDismountBypass.remove(playerId);
    }

    void ejectSeatOccupant(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isInsideVehicle()) {
            return;
        }
        Entity vehicle = player.getVehicle();
        if (this.seatAction(vehicle) == null) {
            return;
        }
        this.seatDismountBypass.add(playerId);
        player.leaveVehicle();
    }

    void requestSeatRestore(Player player, MahjongTableSession session, SeatWind wind) {
        if (player == null || session == null || wind == null || this.plugin.craftEngine() == null) {
            return;
        }
        if (!this.tryEnterSeatRestoreCooldown(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (this.plugin.craftEngine() == null) {
                return;
            }
            if (this.tableManager.tableFor(player.getUniqueId()) != session) {
                return;
            }
            if (player.isInsideVehicle()) {
                return;
            }
            Entity furniture = this.findSeatFurniture(session, wind);
            if (furniture != null) {
                this.plugin.craftEngine().seatPlayerOnFurniture(furniture, player);
            }
        });
    }

    void movePlayerToSeatExit(UUID playerId, MahjongTableSession session, SeatWind wind) {
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
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && !player.isInsideVehicle()) {
                player.teleport(exit);
            }
        });
    }

    void startSeatWatchdog(MahjongTableSession session) {
        this.startSeatWatchdog(session, SEAT_WATCHDOG_DURATION_TICKS);
    }

    void startSeatWatchdog(MahjongTableSession session, long durationTicks) {
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

    void startSeatWatchdog(MahjongTableSession session, UUID playerId, SeatWind wind) {
        this.startSeatWatchdog(session, playerId, wind, SEAT_WATCHDOG_DURATION_TICKS);
    }

    void startSeatWatchdog(MahjongTableSession session, UUID playerId, SeatWind wind, long durationTicks) {
        if (session == null || playerId == null || wind == null || durationTicks <= 0L) {
            return;
        }
        long expiresAtTick = currentTick() + durationTicks;
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
        this.seatWatchdogTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::runSeatWatchdogs, 1L, SEAT_WATCHDOG_PERIOD_TICKS);
    }

    private void runSeatWatchdogs() {
        if (this.seatWatchdogs.isEmpty()) {
            this.stopSeatWatchdogTask();
            return;
        }
        long nowTick = currentTick();
        for (Map.Entry<UUID, SeatWatchdogBinding> entry : List.copyOf(this.seatWatchdogs.entrySet())) {
            UUID playerId = entry.getKey();
            SeatWatchdogBinding binding = entry.getValue();
            MahjongTableSession session = this.tableManager.resolveTableById(binding.tableId());
            if (session == null || !session.isStarted() || session.seatOf(playerId) != binding.wind() || binding.expiresAtTick() < nowTick) {
                this.seatWatchdogs.remove(playerId);
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                this.seatWatchdogs.remove(playerId);
                continue;
            }
            if (!this.isPlayerSeatedAt(player, session, binding.wind())) {
                this.requestSeatRestore(player, session, binding.wind());
            }
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

    private boolean isPlayerSeatedAt(Player player, MahjongTableSession session, SeatWind wind) {
        if (player == null || session == null || wind == null || !player.isInsideVehicle()) {
            return false;
        }
        DisplayClickAction action = this.seatAction(player.getVehicle());
        return action != null
            && action.seatWind() == wind
            && session.id().equals(action.tableId());
    }

    private Entity findSeatFurniture(MahjongTableSession session, SeatWind wind) {
        if (session == null || wind == null || this.plugin.craftEngine() == null) {
            return null;
        }
        Location anchor = session.seatAnchorLocation(wind);
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

    private static long currentTick() {
        return Bukkit.getCurrentTick();
    }

    private record SeatWatchdogBinding(String tableId, SeatWind wind, long expiresAtTick) {
    }
}
