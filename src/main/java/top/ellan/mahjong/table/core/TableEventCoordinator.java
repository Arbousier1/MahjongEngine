package top.ellan.mahjong.table.core;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.DisplayClickAction.ActionType;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.display.TableDisplayRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

final class TableEventCoordinator {
    private static final long DUPLICATE_DISPLAY_ACTION_WINDOW_NANOS = 40_000_000L;
    private final MahjongTableManager manager;
    private final Map<UUID, RecentDisplayAction> recentDisplayActions = new ConcurrentHashMap<>();

    TableEventCoordinator(MahjongTableManager manager) {
        this.manager = manager;
    }

    void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.recentDisplayActions.remove(playerId);
        this.manager.clearRecentHandInput(playerId);
        this.manager.leave(playerId);
    }

    void onJoin(PlayerJoinEvent event) {
        this.manager.pluginRef().scheduler().runEntity(event.getPlayer(), () -> this.manager.pluginRef().craftEngine().syncTrackedEntitiesFor(event.getPlayer()));
    }

    void onSeatMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DisplayClickAction action = this.manager.seatCoordinatorRef().seatAction(event.getMount());
        if (action == null || action.actionType() != ActionType.JOIN_SEAT) {
            return;
        }
        MahjongTableSession currentSeat = this.manager.tableFor(player.getUniqueId());
        if (MahjongTableManager.isSameSeatJoin(currentSeat, player.getUniqueId(), action)) {
            return;
        }
        MahjongTableSession session = this.manager.join(player, action.tableId(), action.seatWind());
        if (session != null) {
            this.manager.pluginRef().messages().send(player, "command.joined_table", this.manager.pluginRef().messages().tag("table_id", session.id()));
            this.manager.seatCoordinatorRef().requestSeatRestore(player, session, action.seatWind());
            return;
        }
        event.setCancelled(true);
        this.manager.pluginRef().messages().actionBar(player, "command.join_failed");
    }

    void onSeatDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DisplayClickAction action = this.manager.seatCoordinatorRef().seatAction(event.getDismounted());
        if (action == null) {
            return;
        }
        if (this.manager.seatCoordinatorRef().consumeDismountBypass(player.getUniqueId())) {
            return;
        }
        MahjongTableSession session = this.manager.resolveTableById(action.tableId());
        if (session != null && session.isStarted()) {
            MahjongTableManager.LeaveResult result = this.manager.leave(player.getUniqueId());
            event.setCancelled(true);
            this.manager.seatCoordinatorRef().startSeatWatchdog(session, player.getUniqueId(), action.seatWind());
            this.manager.seatCoordinatorRef().requestSeatRestore(player, session, action.seatWind());
            this.manager.pluginRef().messages().actionBar(player, result.status() == MahjongTableManager.LeaveStatus.DEFERRED ? "command.leave_deferred" : "command.leave_blocked_started");
            return;
        }
        this.manager.pluginRef().scheduler().runEntity(player, () -> this.manager.leave(player.getUniqueId()));
    }

    void onSeatSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        MahjongTableSession session = this.manager.tableFor(player.getUniqueId());
        if (session == null || !session.isStarted() || !player.isInsideVehicle()) {
            return;
        }
        if (this.manager.seatCoordinatorRef().seatAction(player.getVehicle()) == null) {
            return;
        }
        MahjongTableManager.LeaveResult result = this.manager.leave(player.getUniqueId());
        event.setCancelled(true);
        this.manager.seatCoordinatorRef().startSeatWatchdog(session, player.getUniqueId(), session.seatOf(player.getUniqueId()));
        this.manager.pluginRef().messages().actionBar(player, result.status() == MahjongTableManager.LeaveStatus.DEFERRED ? "command.leave_deferred" : "command.leave_blocked_started");
    }

    void onDisplayInteract(PlayerInteractEntityEvent event) {
        this.handleDisplayInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    void onDisplayInteractAt(PlayerInteractAtEntityEvent event) {
        this.handleDisplayInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    void onProtectedDisplayDamage(EntityDamageEvent event) {
        if (this.isProtectedTableEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void handleDisplayInteract(Player player, Entity clickedEntity, org.bukkit.event.Cancellable event) {
        if (player == null || clickedEntity == null || event == null) {
            return;
        }
        DisplayClickAction action = TableDisplayRegistry.get(clickedEntity.getEntityId());
        if (action == null) {
            return;
        }
        if (this.manager.pluginRef().craftEngine() != null && this.manager.pluginRef().craftEngine().isFurnitureEntity(clickedEntity)) {
            return;
        }

        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        if (this.isDuplicateDisplayAction(playerId, action)) {
            return;
        }
        this.rememberDisplayAction(playerId, action);
        boolean accepted = this.manager.handleDisplayAction(player, action);
        if (!accepted) {
            if (action.actionType() == ActionType.HAND_TILE) {
                this.manager.pluginRef().messages().actionBar(player, "packet.cannot_click_tile");
            } else {
                this.manager.pluginRef().messages().actionBar(player, "command.join_failed");
            }
        }
    }

    private boolean isProtectedTableEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (DisplayEntities.isManagedEntity(this.manager.pluginRef(), entity)) {
            return true;
        }
        if (TableDisplayRegistry.get(entity.getEntityId()) != null || this.manager.seatCoordinatorRef().seatAction(entity) != null) {
            return true;
        }
        if (this.manager.pluginRef().craftEngine() == null) {
            return false;
        }
        if (this.manager.pluginRef().craftEngine().isSeatEntity(entity)) {
            Entity furnitureEntity = this.manager.pluginRef().craftEngine().furnitureEntityForSeat(entity);
            if (furnitureEntity != null && this.manager.pluginRef().craftEngine().isManagedFurnitureEntity(furnitureEntity)) {
                return true;
            }
        }
        if (this.manager.pluginRef().craftEngine().isFurnitureEntity(entity)) {
            return this.manager.pluginRef().craftEngine().isManagedFurnitureEntity(entity);
        }
        return false;
    }

    private boolean isDuplicateDisplayAction(UUID playerId, DisplayClickAction action) {
        RecentDisplayAction recent = this.recentDisplayActions.get(playerId);
        if (recent == null || !recent.matches(action)) {
            return false;
        }
        return System.nanoTime() - recent.timestampNanos() <= DUPLICATE_DISPLAY_ACTION_WINDOW_NANOS;
    }

    private void rememberDisplayAction(UUID playerId, DisplayClickAction action) {
        if (playerId == null || action == null) {
            return;
        }
        this.recentDisplayActions.put(playerId, new RecentDisplayAction(action, System.nanoTime()));
    }

    private record RecentDisplayAction(DisplayClickAction action, long timestampNanos) {
        private boolean matches(DisplayClickAction candidate) {
            return MahjongTableManager.sameDisplayAction(this.action, candidate);
        }
    }
}

