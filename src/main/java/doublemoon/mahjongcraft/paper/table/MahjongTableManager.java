package doublemoon.mahjongcraft.paper.table;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.DisplayClickAction;
import doublemoon.mahjongcraft.paper.render.DisplayClickAction.ActionType;
import doublemoon.mahjongcraft.paper.render.DisplayEntities;
import doublemoon.mahjongcraft.paper.render.TableDisplayRegistry;
import doublemoon.mahjongcraft.paper.ui.SettlementUi;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class MahjongTableManager implements Listener {
    private static final long DUPLICATE_HAND_TILE_CLICK_WINDOW_NANOS = 40_000_000L;
    private static final double PERSISTED_TABLE_CLEANUP_RADIUS_XZ = 4.5D;
    private static final double PERSISTED_TABLE_CLEANUP_RADIUS_Y = 3.5D;
    private static final double ADMIN_NEAREST_TABLE_RADIUS = 4.5D;
    private final MahjongPaperPlugin plugin;
    private final PersistentTableStore persistentTableStore;
    private final int startupRebuildBatchSize;
    private final Map<String, MahjongTableSession> tables = new LinkedHashMap<>();
    private final Map<UUID, String> playerTables = new LinkedHashMap<>();
    private final Map<UUID, String> spectatorTables = new LinkedHashMap<>();
    private final Map<TableChunkKey, Set<String>> tablesByChunk = new LinkedHashMap<>();
    private final Set<String> pendingArtifactCleanupTableIds = new HashSet<>();
    private final StartupRefreshQueue startupRefreshQueue = new StartupRefreshQueue();
    private final Map<UUID, RecentHandTileClick> recentHandTileClicks = new HashMap<>();
    private final Set<UUID> seatDismountBypass = new HashSet<>();
    private final BukkitTask tableTickTask;
    private BukkitTask startupRefreshTask;

    public MahjongTableManager(MahjongPaperPlugin plugin) {
        this.plugin = plugin;
        this.persistentTableStore = new PersistentTableStore(plugin);
        this.startupRebuildBatchSize = Math.max(1, plugin.settings().tableStartupRebuildBatchSize());
        this.tableTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> this.tables.values().forEach(MahjongTableSession::tick), 20L, 20L);
    }

    public MahjongTableSession createTable(Player owner) {
        if (this.isViewingAnyTable(owner.getUniqueId())) {
            return this.sessionForViewer(owner.getUniqueId());
        }
        String id = this.nextId();
        MahjongTableSession session = new MahjongTableSession(this.plugin, id, owner.getLocation().toCenterLocation(), owner);
        this.registerTable(session);
        this.playerTables.put(owner.getUniqueId(), id);
        session.render();
        this.persistTables();
        this.plugin.debug().log("table", "Created table " + id + " for " + owner.getName());
        this.sendReadyPrompt(owner);
        return session;
    }

    public MahjongTableSession createBotMatch(Player owner, String preset) {
        if (this.isViewingAnyTable(owner.getUniqueId())) {
            return null;
        }

        String id = this.nextId();
        MahjongTableSession session = new MahjongTableSession(this.plugin, id, owner.getLocation().toCenterLocation(), owner, false);
        this.registerTable(session);
        this.playerTables.put(owner.getUniqueId(), id);

        if (preset != null && !preset.isBlank()) {
            session.applyRulePreset(preset);
        }

        while (session.size() < 4) {
            session.addBot();
        }
        session.removePlayer(owner.getUniqueId());
        this.playerTables.remove(owner.getUniqueId());
        while (session.size() < 4) {
            session.addBot();
        }
        session.addSpectator(owner);
        this.spectatorTables.put(owner.getUniqueId(), id);
        session.startRound();
        this.plugin.debug().log("table", "Created 4-bot match " + id + " for spectator " + owner.getName());
        return session;
    }

    public MahjongTableSession join(Player player, String tableId) {
        MahjongTableSession session = this.tables.get(tableId.toUpperCase(Locale.ROOT));
        if (session == null) {
            return null;
        }
        if (this.isViewingAnyTable(player.getUniqueId())) {
            return this.sessionForViewer(player.getUniqueId());
        }
        if (!session.addPlayer(player)) {
            return null;
        }
        this.cleanupLoadedTableArtifactsIfNeeded(session);
        this.playerTables.put(player.getUniqueId(), session.id());
        this.plugin.debug().log("table", player.getName() + " joined table " + session.id());
        session.render();
        this.sendReadyPrompt(player);
        return session;
    }

    public MahjongTableSession join(Player player, String tableId, SeatWind wind) {
        MahjongTableSession session = this.tables.get(tableId.toUpperCase(Locale.ROOT));
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
        this.cleanupLoadedTableArtifactsIfNeeded(session);
        this.spectatorTables.remove(playerId);
        session.removeSpectator(playerId);
        this.playerTables.put(playerId, session.id());
        this.plugin.debug().log("table", player.getName() + " joined table " + session.id() + " at " + wind.name());
        session.render();
        this.sendReadyPrompt(player);
        return session;
    }

    public MahjongTableSession spectate(Player player, String tableId) {
        MahjongTableSession session = this.tables.get(tableId.toUpperCase(Locale.ROOT));
        if (session == null) {
            return null;
        }
        if (this.isViewingAnyTable(player.getUniqueId())) {
            return this.sessionForViewer(player.getUniqueId());
        }
        if (!session.addSpectator(player)) {
            return null;
        }
        this.cleanupLoadedTableArtifactsIfNeeded(session);
        this.spectatorTables.put(player.getUniqueId(), session.id());
        this.plugin.debug().log("table", player.getName() + " is spectating table " + session.id());
        session.render();
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
        this.ejectSeatOccupant(playerId);
        if (!session.removePlayer(playerId)) {
            return new LeaveResult(LeaveStatus.BLOCKED, session);
        }
        this.playerTables.remove(playerId);
        this.plugin.debug().log("table", "Player " + playerId + " left table " + session.id());
        this.handleSeatMembershipChanged(session, true);
        return new LeaveResult(LeaveStatus.LEFT, session);
    }

    public MahjongTableSession unspectate(UUID playerId) {
        String tableId = this.spectatorTables.remove(playerId);
        MahjongTableSession session = tableId == null ? null : this.tables.get(tableId);
        if (session == null) {
            return null;
        }
        session.removeSpectator(playerId);
        this.plugin.debug().log("table", "Spectator " + playerId + " left table " + session.id());
        session.render();
        return session;
    }

    public MahjongTableSession tableFor(UUID playerId) {
        String tableId = this.playerTables.get(playerId);
        return tableId == null ? null : this.tables.get(tableId);
    }

    public MahjongTableSession sessionForViewer(UUID playerId) {
        MahjongTableSession seated = this.tableFor(playerId);
        if (seated != null) {
            return seated;
        }
        String tableId = this.spectatorTables.get(playerId);
        return tableId == null ? null : this.tables.get(tableId);
    }

    public boolean isSpectating(UUID playerId) {
        return this.spectatorTables.containsKey(playerId);
    }

    public Collection<MahjongTableSession> tables() {
        return this.tables.values();
    }

    public Collection<String> tableIds() {
        return new ArrayList<>(this.tables.keySet());
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
        for (MahjongTableSession table : this.tables.values()) {
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
            if (this.tables.containsKey(id)) {
                this.plugin.getLogger().warning("Persistent table id " + id + " already exists in memory, deleting it before startup rebuild.");
                this.deleteTable(id);
            }
            this.createPersistentTable(loadedTable.id(), loadedTable.center(), loadedTable.rule());
            this.enqueueStartupRefresh(id);
            this.plugin.debug().log("table", "Queued persistent table " + id + " for startup rebuild");
        }
        this.persistTables();
    }

    public void persistTables() {
        this.persistentTableStore.save(this.tables.values());
    }

    public void refreshPersistentTablesAfterStartup() {
        for (MahjongTableSession session : this.tables.values()) {
            if (session.isPersistentRoom()) {
                this.enqueueStartupRefresh(session.id());
            }
        }
        this.scheduleStartupRefreshTask();
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
            this.ejectSeatOccupant(playerId);
            this.playerTables.remove(playerId);
        }
        for (UUID spectatorId : session.spectators()) {
            this.spectatorTables.remove(spectatorId);
        }
        this.pendingArtifactCleanupTableIds.remove(session.id());
        this.unindexTable(session);
        session.shutdown();
        this.cleanupTableArtifacts(center);
        this.tables.remove(session.id());
        this.persistTables();
        this.plugin.debug().log("table", "Deleted table " + session.id());
        return session;
    }

    public boolean clickTile(Player player, String tableId, UUID ownerId, int tileIndex) {
        MahjongTableSession session = this.tables.get(tableId);
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
            MahjongTableSession session = this.join(player, action.tableId(), action.seatWind());
            if (session == null) {
                return false;
            }
            this.plugin.messages().send(player, "command.joined_table", this.plugin.messages().tag("table_id", session.id()));
            return true;
        }
        if (action.actionType() == ActionType.TOGGLE_READY) {
            MahjongTableSession session = this.tables.get(action.tableId());
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
        if (this.startupRefreshTask != null) {
            this.startupRefreshTask.cancel();
            this.startupRefreshTask = null;
        }
        this.persistentTableStore.flush(this.tables.values());
        this.tables.values().forEach(MahjongTableSession::shutdown);
        this.tables.clear();
        this.playerTables.clear();
        this.spectatorTables.clear();
        this.tablesByChunk.clear();
        this.pendingArtifactCleanupTableIds.clear();
        this.startupRefreshQueue.clear();
        this.seatDismountBypass.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.recentHandTileClicks.remove(playerId);
        this.leave(playerId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.craftEngine().syncTrackedEntitiesFor(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSeatMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DisplayClickAction action = this.seatAction(event.getMount());
        if (action == null || action.actionType() != ActionType.JOIN_SEAT) {
            return;
        }
        MahjongTableSession session = this.join(player, action.tableId(), action.seatWind());
        if (session != null) {
            this.plugin.messages().send(player, "command.joined_table", this.plugin.messages().tag("table_id", session.id()));
            return;
        }
        event.setCancelled(true);
        this.plugin.messages().actionBar(player, "command.join_failed");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSeatDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DisplayClickAction action = this.seatAction(event.getDismounted());
        if (action == null) {
            return;
        }
        if (this.seatDismountBypass.remove(player.getUniqueId())) {
            return;
        }
        MahjongTableSession session = this.resolveTable(action.tableId());
        if (session != null && session.isStarted()) {
            event.setCancelled(true);
            this.plugin.messages().actionBar(player, "command.leave_after_round");
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> this.leave(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDisplayInteract(PlayerInteractEntityEvent event) {
        DisplayClickAction action = TableDisplayRegistry.get(event.getRightClicked().getEntityId());
        if (action == null) {
            return;
        }
        if (this.plugin.craftEngine() != null && this.plugin.craftEngine().isFurnitureEntity(event.getRightClicked())) {
            return;
        }

        event.setCancelled(true);
        boolean accepted = this.handleDisplayAction(event.getPlayer(), action);
        if (!accepted) {
            if (action.actionType() == ActionType.HAND_TILE) {
                this.plugin.messages().actionBar(event.getPlayer(), "packet.cannot_click_tile");
            } else {
                this.plugin.messages().actionBar(event.getPlayer(), "command.join_failed");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (String tableId : this.tableIdsNearChunk(event.getChunk())) {
            MahjongTableSession session = this.resolveTable(tableId);
            if (session == null || !this.affectsTableArea(event.getChunk(), session)) {
                continue;
            }
            if (!this.isTableAreaLoaded(session)) {
                continue;
            }
            this.cleanupLoadedTableArtifactsIfNeeded(session);
            session.render();
        }
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
        } while (this.tables.containsKey(id));
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

    private void cleanupLoadedTableArtifactsIfNeeded(MahjongTableSession session) {
        if (session == null || !this.pendingArtifactCleanupTableIds.remove(session.id())) {
            return;
        }
        this.refreshPersistentTableArtifacts(session);
        this.plugin.debug().log("table", "Deferred startup cleanup finished for persistent table " + session.id());
    }

    private void refreshPersistentTableArtifacts(MahjongTableSession session) {
        if (session == null) {
            return;
        }
        this.pendingArtifactCleanupTableIds.remove(session.id());
        Location center = session.center();
        session.clearDisplays();
        this.cleanupTableArtifacts(center);
    }

    private boolean affectsTableArea(Chunk chunk, MahjongTableSession session) {
        Location center = session.center();
        World world = center.getWorld();
        if (world == null || !world.equals(chunk.getWorld())) {
            return false;
        }
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        return Math.abs(centerChunkX - chunk.getX()) <= 1 && Math.abs(centerChunkZ - chunk.getZ()) <= 1;
    }

    private boolean isTableAreaLoaded(MahjongTableSession session) {
        if (session == null) {
            return false;
        }
        Location center = session.center();
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        Set<ChunkNeighborhood.ChunkKey> loadedChunks = new HashSet<>();
        for (ChunkNeighborhood.ChunkKey chunkKey : ChunkNeighborhood.around(world.getUID(), centerChunkX, centerChunkZ)) {
            if (world.isChunkLoaded(chunkKey.chunkX(), chunkKey.chunkZ())) {
                loadedChunks.add(chunkKey);
            }
        }
        return TableAreaChunks.allLoaded(world.getUID(), centerChunkX, centerChunkZ, loadedChunks);
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

    private void ejectSeatOccupant(UUID playerId) {
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

    private DisplayClickAction seatAction(Entity entity) {
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

    private MahjongTableSession resolveTable(String tableId) {
        if (tableId == null) {
            return null;
        }
        return this.tables.get(tableId.toUpperCase(Locale.ROOT));
    }

    private boolean isViewingAnyTable(UUID playerId) {
        return this.playerTables.containsKey(playerId) || this.spectatorTables.containsKey(playerId);
    }

    private MahjongTableSession createPersistentTable(String tableId, Location center, doublemoon.mahjongcraft.paper.riichi.model.MahjongRule rule) {
        String normalizedId = tableId.toUpperCase(Locale.ROOT);
        MahjongTableSession session = new MahjongTableSession(this.plugin, normalizedId, center, rule, true);
        this.registerTable(session);
        this.pendingArtifactCleanupTableIds.add(normalizedId);
        return session;
    }

    private void registerTable(MahjongTableSession session) {
        this.tables.put(session.id(), session);
        this.indexTable(session);
    }

    private void enqueueStartupRefresh(String tableId) {
        this.startupRefreshQueue.enqueue(tableId);
    }

    private void scheduleStartupRefreshTask() {
        if (this.startupRefreshQueue.isEmpty()) {
            return;
        }
        if (this.startupRefreshTask != null && !this.startupRefreshTask.isCancelled()) {
            return;
        }
        this.startupRefreshTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::processStartupRefreshBatch, 1L, 1L);
    }

    private void processStartupRefreshBatch() {
        int processed = 0;
        while (processed < this.startupRebuildBatchSize && !this.startupRefreshQueue.isEmpty()) {
            String tableId = this.startupRefreshQueue.poll();
            MahjongTableSession session = this.resolveTable(tableId);
            if (session == null || !session.isPersistentRoom()) {
                continue;
            }
            if (!this.isTableAreaLoaded(session)) {
                this.plugin.debug().log("table", "Deferred startup cleanup is waiting for chunks around persistent table " + session.id());
                continue;
            }
            this.refreshPersistentTableArtifacts(session);
            session.render();
            processed++;
        }
        if (this.startupRefreshQueue.isEmpty() && this.startupRefreshTask != null) {
            this.startupRefreshTask.cancel();
            this.startupRefreshTask = null;
        }
    }

    private void indexTable(MahjongTableSession session) {
        this.tablesByChunk.computeIfAbsent(TableChunkKey.from(session.center()), ignored -> new HashSet<>()).add(session.id());
    }

    private void unindexTable(MahjongTableSession session) {
        Set<String> ids = this.tablesByChunk.get(TableChunkKey.from(session.center()));
        if (ids == null) {
            return;
        }
        ids.remove(session.id());
        if (ids.isEmpty()) {
            this.tablesByChunk.remove(TableChunkKey.from(session.center()));
        }
    }

    private Set<String> tableIdsNearChunk(Chunk chunk) {
        Set<String> candidates = new HashSet<>();
        for (ChunkNeighborhood.ChunkKey nearby : ChunkNeighborhood.around(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            Set<String> ids = this.tablesByChunk.get(new TableChunkKey(nearby.worldId(), nearby.chunkX(), nearby.chunkZ()));
            if (ids != null) {
                candidates.addAll(ids);
            }
        }
        return candidates;
    }

    public void finalizeDeferredLeaves(MahjongTableSession session, Collection<UUID> playerIds) {
        if (session == null || playerIds == null || playerIds.isEmpty()) {
            return;
        }
        for (UUID playerId : playerIds) {
            this.ejectSeatOccupant(playerId);
            this.playerTables.remove(playerId);
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
                session.spectators().forEach(this.spectatorTables::remove);
                this.unindexTable(session);
                session.shutdown();
                this.cleanupTableArtifacts(center);
                this.tables.remove(session.id());
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

    private record TableChunkKey(UUID worldId, int chunkX, int chunkZ) {
        private static TableChunkKey from(org.bukkit.Location location) {
            return new TableChunkKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }

        private static TableChunkKey from(Chunk chunk) {
            return new TableChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }
}
