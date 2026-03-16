package doublemoon.mahjongcraft.paper.table;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.DisplayClickAction;
import doublemoon.mahjongcraft.paper.render.DisplayClickAction.ActionType;
import doublemoon.mahjongcraft.paper.render.TableDisplayRegistry;
import doublemoon.mahjongcraft.paper.ui.SettlementUi;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.Player;
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
    private final MahjongPaperPlugin plugin;
    private final PersistentTableStore persistentTableStore;
    private final Map<String, MahjongTableSession> tables = new LinkedHashMap<>();
    private final Map<UUID, String> playerTables = new LinkedHashMap<>();
    private final Map<UUID, String> spectatorTables = new LinkedHashMap<>();
    private final Map<TableChunkKey, Set<String>> tablesByChunk = new LinkedHashMap<>();
    private final BukkitTask tableTickTask;

    public MahjongTableManager(MahjongPaperPlugin plugin) {
        this.plugin = plugin;
        this.persistentTableStore = new PersistentTableStore(plugin);
        this.tableTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> this.tables.values().forEach(MahjongTableSession::tick), 20L, 20L);
    }

    public MahjongTableSession createTable(Player owner) {
        if (this.isViewingAnyTable(owner.getUniqueId())) {
            return this.sessionForViewer(owner.getUniqueId());
        }
        String id = this.nextId();
        MahjongTableSession session = new MahjongTableSession(this.plugin, id, owner.getLocation().toCenterLocation(), owner);
        this.tables.put(id, session);
        this.indexTable(session);
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
        this.tables.put(id, session);
        this.indexTable(session);
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

    public void loadPersistentTables() {
        for (PersistentTableStore.LoadedTable loadedTable : this.persistentTableStore.load()) {
            String id = loadedTable.id().toUpperCase(Locale.ROOT);
            if (this.tables.containsKey(id)) {
                this.plugin.getLogger().warning("Skipping duplicate persisted table id " + id + ".");
                continue;
            }
            MahjongTableSession session = new MahjongTableSession(this.plugin, id, loadedTable.center(), loadedTable.rule(), true);
            this.tables.put(id, session);
            this.indexTable(session);
            session.render();
            this.plugin.debug().log("table", "Loaded persistent table " + id);
        }
        this.persistTables();
    }

    public void persistTables() {
        this.persistentTableStore.save(this.tables.values());
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

        for (UUID playerId : session.players()) {
            this.playerTables.remove(playerId);
        }
        for (UUID spectatorId : session.spectators()) {
            this.spectatorTables.remove(spectatorId);
        }
        this.unindexTable(session);
        session.shutdown();
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
        this.plugin.debug().log("table", player.getName() + " clicked tile index " + tileIndex + " on table " + tableId);
        return session.clickHandTile(ownerId, tileIndex);
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
        this.persistTables();
        this.tables.values().forEach(MahjongTableSession::shutdown);
        this.tables.clear();
        this.playerTables.clear();
        this.spectatorTables.clear();
        this.tablesByChunk.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.leave(playerId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.craftEngine().syncTrackedEntitiesFor(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDisplayInteract(PlayerInteractEntityEvent event) {
        DisplayClickAction action = TableDisplayRegistry.get(event.getRightClicked().getEntityId());
        if (action == null) {
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
        Collection<String> tableIds = this.tablesByChunk.get(TableChunkKey.from(event.getChunk()));
        if (tableIds == null || tableIds.isEmpty()) {
            return;
        }
        for (String tableId : tableIds) {
            MahjongTableSession session = this.tables.get(tableId);
            if (session != null) {
                session.render();
            }
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

    private MahjongTableSession resolveTable(String tableId) {
        if (tableId == null) {
            return null;
        }
        return this.tables.get(tableId.toUpperCase(Locale.ROOT));
    }

    private boolean isViewingAnyTable(UUID playerId) {
        return this.playerTables.containsKey(playerId) || this.spectatorTables.containsKey(playerId);
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

    public void finalizeDeferredLeaves(MahjongTableSession session, Collection<UUID> playerIds) {
        if (session == null || playerIds == null || playerIds.isEmpty()) {
            return;
        }
        for (UUID playerId : playerIds) {
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
                session.spectators().forEach(this.spectatorTables::remove);
                this.unindexTable(session);
                session.shutdown();
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
