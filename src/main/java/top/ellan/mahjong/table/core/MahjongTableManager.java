package top.ellan.mahjong.table.core;

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.compat.CraftEngineService;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.DisplayClickAction.ActionType;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.table.runtime.ChunkNeighborhood;
import top.ellan.mahjong.table.runtime.TableRefreshCoordinator;
import top.ellan.mahjong.table.runtime.TableSeatCoordinator;
import top.ellan.mahjong.ui.SettlementUi;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import top.ellan.mahjong.runtime.PluginTask;

public final class MahjongTableManager implements Listener {
    private static final long DUPLICATE_HAND_TILE_CLICK_WINDOW_NANOS = 40_000_000L;
    private static final double PERSISTED_TABLE_CLEANUP_RADIUS_XZ = 4.5D;
    private static final double PERSISTED_TABLE_CLEANUP_RADIUS_Y = 3.5D;
    private static final double ADMIN_NEAREST_TABLE_RADIUS = 4.5D;
    private static final int PERSISTED_TABLE_CLEANUP_REMOVALS_PER_TICK = 8;
    private final MahjongPaperPlugin plugin;
    private final PersistentTableStore persistentTableStore;
    private final TableDirectory directory = new TableDirectory();
    private final Map<UUID, RecentHandTileClick> recentHandTileClicks = new ConcurrentHashMap<>();
    private final TableSeatCoordinator seatCoordinator;
    private final TableRefreshCoordinator refreshCoordinator;
    private final TableEventCoordinator eventCoordinator;
    private final TableMembershipCoordinator membershipCoordinator;
    private final PluginTask tableTickTask;

    public MahjongTableManager(MahjongPaperPlugin plugin) {
        this.plugin = plugin;
        this.persistentTableStore = new PersistentTableStore(plugin);
        this.seatCoordinator = new TableSeatCoordinator(plugin, this);
        this.refreshCoordinator = new TableRefreshCoordinator(plugin, this, this.seatCoordinator, plugin.settings().tableStartupRebuildBatchSize());
        this.eventCoordinator = new TableEventCoordinator(this);
        this.membershipCoordinator = new TableMembershipCoordinator(this);
        this.tableTickTask = plugin.scheduler().runGlobalTimer(this::dispatchTableTicks, 20L, 20L);
    }

    public MahjongTableSession createTable(Player owner) {
        if (this.isViewingAnyTable(owner.getUniqueId())) {
            return this.sessionForViewer(owner.getUniqueId());
        }
        String id = this.nextId();
        Location center = this.normalizedTableCenter(owner.getLocation());
        MahjongTableSession session = new MahjongTableSession(this.plugin, id, center, true);
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
        Location center = this.normalizedTableCenter(owner.getLocation());
        MahjongTableSession session = new MahjongTableSession(
            this.plugin,
            id,
            center,
            MahjongVariant.RIICHI,
            SessionRulePresetResolver.majsoulRule(top.ellan.mahjong.riichi.model.MahjongRule.GameLength.TWO_WIND),
            true,
            true
        );
        session.addPlayer(owner);
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
        this.persistTables();
        this.plugin.debug().log("table", "Created 4-bot match " + id + " for spectator " + owner.getName());
        return session;
    }

    public MahjongTableSession join(Player player, String tableId) {
        return this.membershipCoordinator.join(player, tableId);
    }

    public MahjongTableSession join(Player player, String tableId, SeatWind wind) {
        return this.membershipCoordinator.join(player, tableId, wind);
    }

    public MahjongTableSession spectate(Player player, String tableId) {
        return this.membershipCoordinator.spectate(player, tableId);
    }

    public LeaveResult leave(UUID playerId) {
        return this.membershipCoordinator.leave(playerId);
    }

    public MahjongTableSession unspectate(UUID playerId) {
        return this.membershipCoordinator.unspectate(playerId);
    }

    public MahjongTableSession tableFor(UUID playerId) {
        return this.directory.tableFor(playerId);
    }

    public MahjongTableSession sessionForViewer(UUID playerId) {
        return this.directory.sessionForViewer(playerId);
    }

    MahjongPaperPlugin pluginRef() {
        return this.plugin;
    }

    TableSeatCoordinator seatCoordinatorRef() {
        return this.seatCoordinator;
    }

    TableRefreshCoordinator refreshCoordinatorRef() {
        return this.refreshCoordinator;
    }

    TableDirectory directoryRef() {
        return this.directory;
    }

    void clearRecentHandInput(UUID playerId) {
        this.recentHandTileClicks.remove(playerId);
    }

    boolean isViewingAnyTableInternal(UUID playerId) {
        return this.isViewingAnyTable(playerId);
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
            this.createPersistentTable(loadedTable.id(), loadedTable.center(), loadedTable.variant(), loadedTable.rule(), loadedTable.botMatch());
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
        this.eventCoordinator.onQuit(event);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.eventCoordinator.onJoin(event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSeatMount(EntityMountEvent event) {
        this.eventCoordinator.onSeatMount(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSeatDismount(EntityDismountEvent event) {
        this.eventCoordinator.onSeatDismount(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSeatSneak(PlayerToggleSneakEvent event) {
        this.eventCoordinator.onSeatSneak(event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDisplayInteract(PlayerInteractEntityEvent event) {
        this.eventCoordinator.onDisplayInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedDisplayDamage(EntityDamageEvent event) {
        this.eventCoordinator.onProtectedDisplayDamage(event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDisplayInteractAt(PlayerInteractAtEntityEvent event) {
        this.eventCoordinator.onDisplayInteractAt(event);
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
        int scheduledFallbackRemovals = 0;
        CraftEngineService craftEngine = this.plugin.craftEngine();
        for (Entity entity : world.getNearbyEntities(center, PERSISTED_TABLE_CLEANUP_RADIUS_XZ, PERSISTED_TABLE_CLEANUP_RADIUS_Y, PERSISTED_TABLE_CLEANUP_RADIUS_XZ)) {
            boolean managedDisplay = DisplayEntities.isManagedEntity(this.plugin, entity);
            boolean managedCraftEngineFurniture = craftEngine != null && craftEngine.isManagedFurnitureEntity(entity);
            boolean mahjongCraftEngineFurniture = craftEngine != null && craftEngine.isMahjongFurnitureEntity(entity);
            boolean craftEngineCleanupCandidate = managedCraftEngineFurniture || mahjongCraftEngineFurniture;
            boolean removedByCraftEngine = craftEngineCleanupCandidate && craftEngine.removeFurniture(entity);
            if (!removedByCraftEngine && !managedDisplay && !craftEngineCleanupCandidate) {
                continue;
            }
            if (!removedByCraftEngine && entity.isValid()) {
                if (entity instanceof Interaction interaction) {
                    interaction.setResponsive(false);
                }
                long delayTicks = 1L + (scheduledFallbackRemovals / PERSISTED_TABLE_CLEANUP_REMOVALS_PER_TICK);
                this.plugin.scheduler().removeEntity(entity, delayTicks);
                scheduledFallbackRemovals++;
            }
            removed++;
        }
        if (removed > 0) {
            this.plugin.debug().log("table", "Cleaned " + removed + " table entities near " + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ());
        }
    }

    public void cleanupTableArtifactsNow(Location center) {
        this.cleanupTableArtifacts(center);
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
        UUID seatedPlayer = session.playerAt(wind);
        return seatedPlayer == null
            || (!session.isStarted() && !session.isRoundStartInProgress() && session.isBot(seatedPlayer));
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
        top.ellan.mahjong.riichi.model.MahjongRule rule,
        boolean botMatchRoom
    ) {
        String normalizedId = tableId.toUpperCase(Locale.ROOT);
        Location normalizedCenter = this.normalizedTableCenter(center);
        MahjongTableSession session = new MahjongTableSession(this.plugin, normalizedId, normalizedCenter, variant, rule, true, botMatchRoom);
        this.registerTable(session);
        if (botMatchRoom) {
            while (session.size() < 4) {
                if (!session.addBot()) {
                    break;
                }
            }
        }
        this.refreshCoordinator.markPendingArtifactCleanup(normalizedId);
        return session;
    }

    private void registerTable(MahjongTableSession session) {
        this.directory.registerTable(session);
    }

    public void finalizeDeferredLeaves(MahjongTableSession session, Map<UUID, SeatWind> playerSeats) {
        this.membershipCoordinator.finalizeDeferredLeaves(session, playerSeats);
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

    private Location normalizedTableCenter(Location source) {
        if (source == null || source.getWorld() == null) {
            return source;
        }
        return source.toCenterLocation();
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

}



