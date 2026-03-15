package doublemoon.mahjongcraft.paper.table;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.ui.SettlementUi;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class MahjongTableManager implements Listener {
    private final MahjongPaperPlugin plugin;
    private final PersistentTableStore persistentTableStore;
    private final Map<String, MahjongTableSession> tables = new LinkedHashMap<>();
    private final Map<UUID, String> playerTables = new LinkedHashMap<>();
    private final Map<UUID, String> spectatorTables = new LinkedHashMap<>();
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
        this.playerTables.put(owner.getUniqueId(), id);
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
        this.tables.put(id, session);
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

    public MahjongTableSession leave(UUID playerId) {
        MahjongTableSession session = this.tableFor(playerId);
        if (session == null) {
            return this.unspectate(playerId);
        }
        if (!session.removePlayer(playerId)) {
            return null;
        }
        this.playerTables.remove(playerId);
        this.plugin.debug().log("table", "Player " + playerId + " left table " + session.id());
        if (session.isEmpty()) {
            if (session.isPersistentRoom()) {
                session.render();
                this.plugin.debug().log("table", "Kept empty persistent table " + session.id());
            } else {
                session.spectators().forEach(this.spectatorTables::remove);
                session.shutdown();
                this.tables.remove(session.id());
                this.plugin.debug().log("table", "Removed empty table " + session.id());
            }
        } else {
            session.render();
        }
        this.persistTables();
        return session;
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

    public void loadPersistentTables() {
        for (PersistentTableStore.LoadedTable loadedTable : this.persistentTableStore.load()) {
            String id = loadedTable.id().toUpperCase(Locale.ROOT);
            if (this.tables.containsKey(id)) {
                this.plugin.getLogger().warning("Skipping duplicate persisted table id " + id + ".");
                continue;
            }
            MahjongTableSession session = new MahjongTableSession(this.plugin, id, loadedTable.center(), loadedTable.rule(), true);
            this.tables.put(id, session);
            session.render();
            this.plugin.debug().log("table", "Loaded persistent table " + id);
        }
        this.persistTables();
    }

    public void persistTables() {
        this.persistentTableStore.save(this.tables.values());
    }

    public void start(Player player) {
        MahjongTableSession session = this.tableFor(player.getUniqueId());
        if (session == null) {
            throw new IllegalStateException("Player is not in a table");
        }
        this.plugin.debug().log("table", player.getName() + " started table " + session.id());
        session.startRound();
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

    public void shutdown() {
        this.tableTickTask.cancel();
        this.persistTables();
        this.tables.values().forEach(MahjongTableSession::shutdown);
        this.tables.clear();
        this.playerTables.clear();
        this.spectatorTables.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        MahjongTableSession session = this.leave(playerId);
        if (session == null) {
            this.unspectate(playerId);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.craftEngine().syncTrackedEntitiesFor(event.getPlayer()));
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
}
