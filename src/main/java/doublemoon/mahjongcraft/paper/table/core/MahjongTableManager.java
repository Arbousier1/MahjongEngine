package doublemoon.mahjongcraft.paper.table.core;

import doublemoon.mahjongcraft.paper.bootstrap.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.display.DisplayClickAction;
import doublemoon.mahjongcraft.paper.render.display.DisplayClickAction.ActionType;
import doublemoon.mahjongcraft.paper.render.display.DisplayEntities;
import doublemoon.mahjongcraft.paper.render.display.TableDisplayRegistry;
import doublemoon.mahjongcraft.paper.table.runtime.ChunkNeighborhood;
import doublemoon.mahjongcraft.paper.table.runtime.TableRefreshCoordinator;
import doublemoon.mahjongcraft.paper.table.runtime.TableSeatCoordinator;
import doublemoon.mahjongcraft.paper.ui.SettlementUi;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import doublemoon.mahjongcraft.paper.runtime.PluginTask;

public final class MahjongTableManager implements Listener {
    private static final long DUPLICATE_DISPLAY_ACTION_WINDOW_NANOS = 40_000_000L;
    private static final long DUPLICATE_HAND_TILE_CLICK_WINDOW_NANOS = 40_000_000L;
    private static final double PERSISTED_TABLE_CLEANUP_RADIUS_XZ = 4.5D;
    private static final double PERSISTED_TABLE_CLEANUP_RADIUS_Y = 3.5D;
    private static final double ADMIN_NEAREST_TABLE_RADIUS = 4.5D;
    private final MahjongPaperPlugin plugin;
    private final PersistentTableStore persistentTableStore;
    private final TableDirectory directory = new TableDirectory();
    private final Map<UUID, RecentDisplayAction> recentDisplayActions = new ConcurrentHashMap<>();
    private final Map<UUID, RecentHandTileClick> recentHandTileClicks = new ConcurrentHashMap<>();
    private final TableSeatCoordinator seatCoordinator;
    private final TableRefreshCoordinator refreshCoordinator;
    private final PluginTask tableTickTask;

    public MahjongTableManager(MahjongPaperPlugin plugin) {
        this.plugin = plugin;
        this.persistentTableStore = new PersistentTableStore(plugin);
        this.seatCoordinator = new TableSeatCoordinator(plugin, this);
        this.refreshCoordinator = new TableRefreshCoordinator(plugin, this, this.seatCoordinator, plugin.settings().tableStartupRebuildBatchSize());
        this.tableTickTask = plugin.scheduler().runGlobalTimer(this::dispatchTableTicks, 20L, 20L);
    }

    public MahjongTableSession createTable(Player owner) {
        if (this.isViewingAnyTable(owner.getUniqueId())) {
            return this.sessionForViewer(owner.getUniqueId());
        }
        String id = this.nextId();
        MahjongTableSession session = new MahjongTableSession(this.plugin, id, owner.getLocation().toCenterLocation(), true);
        this.registerTable(session);
        session.render();
        this.persistTables();
        this.plugin.debug().log("table", "Created table " + id + " for " + owner.getName());
        return session;
    }

    public MahjongTableSession createBotMatch(Player owner, String preset) {
        if (this.isViewingAnyTable(owner.getUniqueId())) {
            return null;
        }

        String id = this.nextId();
        MahjongTableSession session = new MahjongTableSession(this.plugin, id, owner.getLocation().toCenterLocation(), owner, false);
        if (preset != null && !preset.isBlank()) {
            if (!session.applyRulePreset(preset) || session.currentVariant() != MahjongVariant.RIICHI) {
                return null;
            }
        }
        this.registerTable(session);
        this.directory.assignPlayer(owner.getUniqueId(), id);

        while (session.size() < 4) {
            session.addBot();
        }
        session.removePlayer(owner.getUniqueId());
        this.directory.removePlayer(owner.getUniqueId());
        while (session.size() < 4) {
            session.addBot();
        }
        session.addSpectator(owner);
        this.directory.assignSpectator(owner.getUniqueId(), id);
        session.startRound();
        this.plugin.debug().log("table", "Created 4-bot match " + id + " for spectator " + owner.getName());
        return session;
    }

    public MahjongTableSession join(Player player, String tableId) {
        MahjongTableSession session = this.directory.resolveTable(tableId);
        if (session == null) {
            return null;
        }
        if (this.isViewingAnyTable(player.getUniqueId())) {
            return this.sessionForViewer(player.getUniqueId());
        }
        if (!session.addPlayer(player)) {
            return null;
        }
        this.refreshCoordinator.cleanupLoadedTableArtifactsIfNeeded(session);
        this.directory.assignPlayer(player.getUniqueId(), session.id());
        this.plugin.debug().log("table", player.getName() + " joined table " + session.id());
        session.render();
        this.syncCraftEngineTrackedEntities(player);
        this.sendReadyPrompt(player);
        return session;
    }

    public MahjongTableSession join(Player player, String tableId, SeatWind wind) {
        MahjongTableSession session = this.directory.resolveTable(tableId);
        if (session == null || wind == null) {
            return null;
        }

        UUID playerId = player.getUniqueId();
        MahjongTableSession currentSeat = this.tableFor(playerId);
        if (currentSeat != null) {
            return currentSeat == session && currentSeat.seatOf(playerId) == wind ? currentSeat : null;
        }

        MahjongTableSession currentView = this.sessionForViewer(playerId);
        if (currentView != null && currentView != session) {
            return null;
        }

        if (!session.addPlayer(player, wind)) {
            return null;
        }
        this.refreshCoordinator.cleanupLoadedTableArtifactsIfNeeded(session);
        this.directory.removeSpectator(playerId);
        session.removeSpectator(playerId);
        this.directory.assignPlayer(playerId, session.id());
        this.plugin.debug().log("table", player.getName() + " joined table " + session.id() + " at " + wind.name());
        session.render();
        this.syncCraftEngineTrackedEntities(player);
        this.sendReadyPrompt(player);
        return session;
    }

    public MahjongTableSession spectate(Player player, String tableId) {
        MahjongTableSession session = this.directory.resolveTable(tableId);
        if (session == null) {
            return null;
        }
        if (this.isViewingAnyTable(player.getUniqueId())) {
            return this.sessionForViewer(player.getUniqueId());
        }
        if (!session.addSpectator(player)) {
            return null;
        }
        this.refreshCoordinator.cleanupLoadedTableArtifactsIfNeeded(session);
        this.directory.assignSpectator(player.getUniqueId(), session.id());
        this.plugin.debug().log("table", player.getName() + " is spectating table " + session.id());
        session.render();
        this.syncCraftEngineTrackedEntities(player);
        return session;
    }

    public LeaveResult leave(UUID playerId) {
        MahjongTableSession session = this.tableFor(playerId);
        if (session == null) {
            MahjongTableSession spectatorSession = this.unspectate(playerId);
            return spectatorSession == null
                ? new LeaveResult(LeaveStatus.NOT_IN_TABLE, null)
                : new LeaveResult(LeaveStatus.UNSPECTATED, spectatorSession);
        }
        if (session.isStarted()) {
            return session.queueLeaveAfterRound(playerId)
                ? new LeaveResult(LeaveStatus.DEFERRED, session)
                : new LeaveResult(LeaveStatus.BLOCKED, session);
        }
        SeatWind seatWind = session.seatOf(playerId);
        this.seatCoordinator.ejectSeatOccupant(playerId);
        if (!session.removePlayer(playerId)) {
            return new LeaveResult(LeaveStatus.BLOCKED, session);
        }
        this.directory.removePlayer(playerId);
        this.seatCoordinator.movePlayerToSeatExit(playerId, session, seatWind);
        this.plugin.debug().log("table", "Player " + playerId + " left table " + session.id());
        this.handleSeatMembershipChanged(session, true);
        return new LeaveResult(LeaveStatus.LEFT, session);
    }

    public MahjongTableSession unspectate(UUID playerId) {
        String tableId = this.directory.removeSpectator(playerId);
        MahjongTableSession session = tableId == null ? null : this.directory.resolveTable(tableId);
        if (session == null) {
            return null;
        }
        session.removeSpectator(playerId);
        this.plugin.debug().log("table", "Spectator " + playerId + " left table " + session.id());
        session.render();
        return session;
    }

    public MahjongTableSession tableFor(UUID playerId) {
        return this.directory.tableFor(playerId);
    }

    public MahjongTableSession sessionForViewer(UUID playerId) {
        return this.directory.sessionForViewer(playerId);
    }

    private void syncCraftEngineTrackedEntities(Player player) {
        if (player == null || this.plugin.craftEngine() == null) {
            return;
        }
        this.plugin.scheduler().runEntity(player, () -> this.plugin.craftEngine().syncTrackedEntitiesFor(player));
    }

    public boolean isSpectating(UUID playerId) {
        return this.directory.isSpectating(playerId);
    }

    public Collection<MahjongTableSession> tables() {
        return this.directory.tables();
    }

    public Collection<String> tableIds() {
        return this.directory.tableIds();
    }

    public MahjongTableSession nearestTable(Location location) {
        return this.nearestTable(location, ADMIN_NEAREST_TABLE_RADIUS);
    }

    public MahjongTableSession nearestTable(Location location, double maxDistance) {
        if (location == null || location.getWorld() == null || maxDistance < 0.0D) {
            return null;
        }
        double maxDistanceSquared = maxDistance * maxDistance;
        MahjongTableSession nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (MahjongTableSession table : this.directory.tables()) {
            Location center = table.center();
            if (center.getWorld() == null || !center.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distanceSquared = center.distanceSquared(location);
            if (distanceSquared > maxDistanceSquared || distanceSquared >= nearestDistanceSquared) {
                continue;
            }
            nearest = table;
            nearestDistanceSquared = distanceSquared;
        }
        return nearest;
    }

    public void loadPersistentTables() {
        for (PersistentTableStore.LoadedTable loadedTable : this.persistentTableStore.load()) {
            String id = loadedTable.id().toUpperCase(Locale.ROOT);
            if (this.directory.containsTableId(id)) {
                this.plugin.getLogger().warning("Persistent table id " + id + " already exists in memory, deleting it before startup rebuild.");
                this.deleteTable(id);
            }
            this.createPersistentTable(loadedTable.id(), loadedTable.center(), loadedTable.variant(), loadedTable.rule());
            this.refreshCoordinator.enqueueStartupRefresh(id);
            this.plugin.debug().log("table", "Queued persistent table " + id + " for startup rebuild");
        }
        this.persistTables();
    }

    public void persistTables() {
        this.persistentTableStore.save(this.directory.tables());
    }

    public void refreshPersistentTablesAfterStartup() {
        for (MahjongTableSession session : this.directory.tables()) {
            if (session.isPersistentRoom()) {
                this.refreshCoordinator.enqueueStartupRefresh(session.id());
            }
        }
        this.refreshCoordinator.scheduleStartupRefreshTask();
    }

    public MahjongTableSession.ReadyResult start(Player player) {
        MahjongTableSession session = this.tableFor(player.getUniqueId());
        if (session == null) {
            throw new IllegalStateException("Player is not in a table");
        }
        MahjongTableSession.ReadyResult result = session.toggleReady(player.getUniqueId());
        this.plugin.debug().log("table", player.getName() + " toggled ready on table " + session.id() + " -> " + result.name());
        return result;
    }

    public MahjongTableSession forceEndTable(String tableId) {
        MahjongTableSession session = this.resolveTable(tableId);
        if (session == null) {
            return null;
        }
        this.plugin.debug().log("table", "Force-ended table " + session.id());
        session.forceEndMatch();
        return session;
    }

    public MahjongTableSession deleteTable(String tableId) {
        MahjongTableSession session = this.resolveTable(tableId);
        if (session == null) {
            return null;
        }
        Location center = session.center();

        for (UUID playerId : session.players()) {
            this.seatCoordinator.ejectSeatOccupant(playerId);
            this.directory.removePlayer(playerId);
        }
        for (UUID spectatorId : session.spectators()) {
            this.directory.removeSpectator(spectatorId);
        }
        this.refreshCoordinator.clearPendingArtifactCleanup(session.id());
        session.shutdown();
        this.cleanupTableArtifactsAt(center);
        this.directory.removeTable(session);
        this.persistTables();
        this.plugin.debug().log("table", "Deleted table " + session.id());
        return session;
    }

    public boolean clickTile(Player player, String tableId, UUID ownerId, int tileIndex) {
        MahjongTableSession session = this.directory.resolveTable(tableId);
        if (session == null || !session.contains(player.getUniqueId()) || !player.getUniqueId().equals(ownerId)) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (this.isDuplicateHandTileClick(playerId, tableId, ownerId, tileIndex)) {
            this.plugin.debug().log("table", player.getName() + " duplicate hand tile click ignored for tile index " + tileIndex + " on table " + tableId);
            return true;
        }
        this.plugin.debug().log("table", player.getName() + " clicked tile index " + tileIndex + " on table " + tableId);
        boolean accepted = session.clickHandTile(ownerId, tileIndex, player.isSneaking());
        if (accepted) {
            this.rememberHandTileClick(playerId, tableId, ownerId, tileIndex);
        }
        return accepted;
    }

    public boolean handleDisplayAction(Player player, DisplayClickAction action) {
        if (player == null || action == null) {
            return false;
        }
        if (action.actionType() == ActionType.HAND_TILE) {
            return this.clickTile(player, action.tableId(), action.ownerId(), action.tileIndex());
        }
        if (action.actionType() == ActionType.JOIN_SEAT) {
            MahjongTableSession currentSeat = this.tableFor(player.getUniqueId());
            if (isSameSeatJoin(currentSeat, player.getUniqueId(), action)) {
                this.seatCoordinator.requestSeatRestore(player, currentSeat, action.seatWind());
                return true;
            }
            MahjongTableSession session = this.join(player, action.tableId(), action.seatWind());
            if (session == null) {
                return false;
            }
            this.plugin.messages().send(player, "command.joined_table", this.plugin.messages().tag("table_id", session.id()));
            this.seatCoordinator.requestSeatRestore(player, session, action.seatWind());
            return true;
        }
        if (action.actionType() == ActionType.TOGGLE_READY) {
            MahjongTableSession session = this.directory.resolveTable(action.tableId());
            if (session == null || session.seatOf(player.getUniqueId()) != action.seatWind()) {
                return false;
            }
            this.sendReadyResult(player, session.toggleReady(player.getUniqueId()));
            return true;
        }
        return false;
    }

    public void shutdown() {
        this.tableTickTask.cancel();
        this.seatCoordinator.shutdown();
        this.refreshCoordinator.shutdown();
        this.persistentTableStore.flush(this.directory.tables());
        this.directory.tables().forEach(MahjongTableSession::shutdown);
        this.directory.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.recentDisplayActions.remove(playerId);
        this.recentHandTileClicks.remove(playerId);
        this.leave(playerId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.plugin.scheduler().runEntity(event.getPlayer(), () -> this.plugin.craftEngine().syncTrackedEntitiesFor(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSeatMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DisplayClickAction action = this.seatCoordinator.seatAction(event.getMount());
        if (action == null || action.actionType() != ActionType.JOIN_SEAT) {
            return;
        }
        MahjongTableSession currentSeat = this.tableFor(player.getUniqueId());
        if (isSameSeatJoin(currentSeat, player.getUniqueId(), action)) {
            return;
        }
        MahjongTableSession session = this.join(player, action.tableId(), action.seatWind());
        if (session != null) {
            this.plugin.messages().send(player, "command.joined_table", this.plugin.messages().tag("table_id", session.id()));
            this.seatCoordinator.requestSeatRestore(player, session, action.seatWind());
            return;
        }
        event.setCancelled(true);
        this.plugin.messages().actionBar(player, "command.join_failed");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSeatDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DisplayClickAction action = this.seatCoordinator.seatAction(event.getDismounted());
        if (action == null) {
            return;
        }
        if (this.seatCoordinator.consumeDismountBypass(player.getUniqueId())) {
            return;
        }
        MahjongTableSession session = this.resolveTable(action.tableId());
        if (session != null && session.isStarted()) {
            LeaveResult result = this.leave(player.getUniqueId());
            event.setCancelled(true);
            this.seatCoordinator.startSeatWatchdog(session, player.getUniqueId(), action.seatWind());
            this.seatCoordinator.requestSeatRestore(player, session, action.seatWind());
            this.plugin.messages().actionBar(player, result.status() == LeaveStatus.DEFERRED ? "command.leave_deferred" : "command.leave_blocked_started");
            return;
        }
        this.plugin.scheduler().runEntity(player, () -> this.leave(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSeatSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        MahjongTableSession session = this.tableFor(player.getUniqueId());
        if (session == null || !session.isStarted() || !player.isInsideVehicle()) {
            return;
        }
        if (this.seatCoordinator.seatAction(player.getVehicle()) == null) {
            return;
        }
        LeaveResult result = this.leave(player.getUniqueId());
        event.setCancelled(true);
        this.seatCoordinator.startSeatWatchdog(session, player.getUniqueId(), session.seatOf(player.getUniqueId()));
        this.plugin.messages().actionBar(player, result.status() == LeaveStatus.DEFERRED ? "command.leave_deferred" : "command.leave_blocked_started");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDisplayInteract(PlayerInteractEntityEvent event) {
        this.handleDisplayInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedDisplayDamage(EntityDamageEvent event) {
        if (this.isProtectedTableEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDisplayInteractAt(PlayerInteractAtEntityEvent event) {
        this.handleDisplayInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleDisplayInteract(Player player, Entity clickedEntity, org.bukkit.event.Cancellable event) {
        if (player == null || clickedEntity == null || event == null) {
            return;
        }
        DisplayClickAction action = TableDisplayRegistry.get(clickedEntity.getEntityId());
        if (action == null) {
            return;
        }
        if (this.plugin.craftEngine() != null && this.plugin.craftEngine().isFurnitureEntity(clickedEntity)) {
            return;
        }

        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        if (this.isDuplicateDisplayAction(playerId, action)) {
            return;
        }
        this.rememberDisplayAction(playerId, action);
        boolean accepted = this.handleDisplayAction(player, action);
        if (!accepted) {
            if (action.actionType() == ActionType.HAND_TILE) {
                this.plugin.messages().actionBar(player, "packet.cannot_click_tile");
            } else {
                this.plugin.messages().actionBar(player, "command.join_failed");
            }
        }
    }

    private boolean isProtectedTableEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (DisplayEntities.isManagedEntity(this.plugin, entity)) {
            return true;
        }
        if (TableDisplayRegistry.get(entity.getEntityId()) != null || this.seatCoordinator.seatAction(entity) != null) {
            return true;
        }
        if (this.plugin.craftEngine() == null) {
            return false;
        }
        if (this.plugin.craftEngine().isSeatEntity(entity)) {
            Entity furnitureEntity = this.plugin.craftEngine().furnitureEntityForSeat(entity);
            if (furnitureEntity != null && this.plugin.craftEngine().isManagedFurnitureEntity(furnitureEntity)) {
                return true;
            }
        }
        if (this.plugin.craftEngine().isFurnitureEntity(entity)) {
            if (this.plugin.craftEngine().isManagedFurnitureEntity(entity)) {
                return true;
            }
            return false;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        this.refreshCoordinator.handleChunkLoad(event.getChunk(), this.directory.tableIdsNearChunk(event.getChunk()));
    }

    @EventHandler
    public void onSettlementClick(InventoryClickEvent event) {
        if (SettlementUi.isSettlementInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSettlementDrag(InventoryDragEvent event) {
        if (SettlementUi.isSettlementInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    private boolean isDuplicateHandTileClick(UUID playerId, String tableId, UUID ownerId, int tileIndex) {
        RecentHandTileClick recent = this.recentHandTileClicks.get(playerId);
        if (recent == null || !recent.matches(tableId, ownerId, tileIndex)) {
            return false;
        }
        return System.nanoTime() - recent.timestampNanos() <= DUPLICATE_HAND_TILE_CLICK_WINDOW_NANOS;
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

    private void rememberHandTileClick(UUID playerId, String tableId, UUID ownerId, int tileIndex) {
        this.recentHandTileClicks.put(playerId, new RecentHandTileClick(tableId, ownerId, tileIndex, System.nanoTime()));
    }

    private String nextId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String id;
        do {
            StringBuilder builder = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            id = builder.toString();
        } while (this.directory.containsTableId(id));
        return id;
    }

    private void cleanupTableArtifacts(Location center) {
        World world = center == null ? null : center.getWorld();
        if (world == null) {
            return;
        }
        int removed = 0;
        for (Entity entity : world.getNearbyEntities(center, PERSISTED_TABLE_CLEANUP_RADIUS_XZ, PERSISTED_TABLE_CLEANUP_RADIUS_Y, PERSISTED_TABLE_CLEANUP_RADIUS_XZ)) {
            boolean managedDisplay = DisplayEntities.isManagedEntity(this.plugin, entity);
            boolean legacyVisual = entity instanceof Display || entity instanceof Interaction;
            boolean removedByCraftEngine = this.plugin.craftEngine() != null && this.plugin.craftEngine().removeFurniture(entity);
            if (!removedByCraftEngine && !managedDisplay && !legacyVisual) {
                continue;
            }
            if (!removedByCraftEngine && entity.isValid()) {
                entity.remove();
            }
            removed++;
        }
        if (removed > 0) {
            this.plugin.debug().log("table", "Cleaned " + removed + " table entities near " + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ());
        }
    }

    public void cleanupTableArtifactsAt(Location center) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        this.plugin.scheduler().runRegion(center, () -> this.cleanupTableArtifacts(center));
    }

    private void dispatchTableTicks() {
        for (MahjongTableSession session : this.directory.tables()) {
            this.plugin.scheduler().runRegion(session.center(), session::tick);
        }
    }

    private record RecentHandTileClick(String tableId, UUID ownerId, int tileIndex, long timestampNanos) {
        private boolean matches(String tableId, UUID ownerId, int tileIndex) {
            return this.tileIndex == tileIndex
                && this.tableId.equals(tableId)
                && this.ownerId.equals(ownerId);
        }
    }

    public boolean canUseSeat(Player player, String tableId, SeatWind wind) {
        if (player == null || tableId == null || wind == null) {
            return false;
        }
        MahjongTableSession session = this.resolveTable(tableId);
        if (session == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        MahjongTableSession currentSeat = this.tableFor(playerId);
        if (currentSeat != null) {
            return currentSeat == session && currentSeat.seatOf(playerId) == wind;
        }
        MahjongTableSession currentView = this.sessionForViewer(playerId);
        if (currentView != null && currentView != session) {
            return false;
        }
        return session.playerAt(wind) == null;
    }

    public void startSeatWatchdog(MahjongTableSession session, long durationTicks) {
        this.seatCoordinator.startSeatWatchdog(session, durationTicks);
    }

    public void startSeatWatchdog(MahjongTableSession session, UUID playerId, SeatWind wind, long durationTicks) {
        this.seatCoordinator.startSeatWatchdog(session, playerId, wind, durationTicks);
    }

    private MahjongTableSession resolveTable(String tableId) {
        return this.directory.resolveTable(tableId);
    }

    public MahjongTableSession resolveTableById(String tableId) {
        return this.resolveTable(tableId);
    }

    private boolean isViewingAnyTable(UUID playerId) {
        return this.directory.isViewingAnyTable(playerId);
    }

    private MahjongTableSession createPersistentTable(
        String tableId,
        Location center,
        MahjongVariant variant,
        doublemoon.mahjongcraft.paper.riichi.model.MahjongRule rule
    ) {
        String normalizedId = tableId.toUpperCase(Locale.ROOT);
        MahjongTableSession session = new MahjongTableSession(this.plugin, normalizedId, center, variant, rule, true);
        this.registerTable(session);
        this.refreshCoordinator.markPendingArtifactCleanup(normalizedId);
        return session;
    }

    private void registerTable(MahjongTableSession session) {
        this.directory.registerTable(session);
    }

    public void finalizeDeferredLeaves(MahjongTableSession session, Map<UUID, SeatWind> playerSeats) {
        if (session == null || playerSeats == null || playerSeats.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, SeatWind> entry : playerSeats.entrySet()) {
            UUID playerId = entry.getKey();
            SeatWind wind = entry.getValue();
            this.seatCoordinator.ejectSeatOccupant(playerId);
            this.directory.removePlayer(playerId);
            this.seatCoordinator.movePlayerToSeatExit(playerId, session, wind);
            this.plugin.debug().log("table", "Player " + playerId + " left table " + session.id() + " after round end");
        }
        this.handleSeatMembershipChanged(session, true);
    }

    private void handleSeatMembershipChanged(MahjongTableSession session, boolean renderAfter) {
        if (session.isEmpty()) {
            if (session.isPersistentRoom()) {
                if (renderAfter) {
                    session.render();
                }
                this.plugin.debug().log("table", "Kept empty persistent table " + session.id());
            } else {
                Location center = session.center();
                this.directory.removeSpectators(session.spectators());
                session.shutdown();
                this.cleanupTableArtifactsAt(center);
                this.directory.removeTable(session);
                this.plugin.debug().log("table", "Removed empty table " + session.id());
            }
        } else if (renderAfter) {
            session.render();
        }
        this.persistTables();
    }

    private void sendReadyPrompt(Player player) {
        this.plugin.messages().send(player, "command.ready_prompt");
    }

    static boolean isSameSeatJoin(MahjongTableSession currentSeat, UUID playerId, DisplayClickAction action) {
        return currentSeat != null
            && playerId != null
            && action != null
            && action.actionType() == ActionType.JOIN_SEAT
            && currentSeat.id().equals(action.tableId())
            && currentSeat.seatOf(playerId) == action.seatWind();
    }

    static boolean sameDisplayAction(DisplayClickAction left, DisplayClickAction right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.actionType() == right.actionType()
            && java.util.Objects.equals(left.tableId(), right.tableId())
            && java.util.Objects.equals(left.ownerId(), right.ownerId())
            && left.tileIndex() == right.tileIndex()
            && left.seatWind() == right.seatWind();
    }

    public void sendReadyResult(Player player, MahjongTableSession.ReadyResult result) {
        if (player == null || result == null) {
            return;
        }
        switch (result) {
            case READY -> this.plugin.messages().send(player, "command.ready_waiting");
            case UNREADY -> this.plugin.messages().send(player, "command.ready_cancelled");
            case STARTED -> this.plugin.messages().send(player, "command.round_started");
            case BLOCKED -> this.plugin.messages().send(player, "command.ready_blocked");
        }
    }

    public record LeaveResult(LeaveStatus status, MahjongTableSession session) {
    }

    public enum LeaveStatus {
        LEFT,
        DEFERRED,
        UNSPECTATED,
        NOT_IN_TABLE,
        BLOCKED
    }

    private record RecentDisplayAction(DisplayClickAction action, long timestampNanos) {
        private boolean matches(DisplayClickAction candidate) {
            return MahjongTableManager.sameDisplayAction(this.action, candidate);
        }
    }
}


