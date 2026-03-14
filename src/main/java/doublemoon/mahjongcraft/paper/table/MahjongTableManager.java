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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class MahjongTableManager implements Listener {
    private final MahjongPaperPlugin plugin;
    private final Map<String, MahjongTableSession> tables = new LinkedHashMap<>();
    private final Map<UUID, String> playerTables = new LinkedHashMap<>();
    private final Map<UUID, String> spectatorTables = new LinkedHashMap<>();
    private final BukkitTask tableTickTask;

    public MahjongTableManager(MahjongPaperPlugin plugin) {
        this.plugin = plugin;
        this.tableTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> this.tables.values().forEach(MahjongTableSession::tick), 20L, 20L);
    }

    public MahjongTableSession createTable(Player owner) {
        if (this.isViewingAnyTable(owner.getUniqueId())) {
            return this.sessionForViewer(owner.getUniqueId());
        }
        String id = nextId();
        MahjongTableSession session = new MahjongTableSession(this.plugin, id, owner.getLocation().toCenterLocation(), owner);
        this.tables.put(id, session);
        this.playerTables.put(owner.getUniqueId(), id);
        this.plugin.debug().log("table", "Created table " + id + " for " + owner.getName());
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
            session.spectators().forEach(this.spectatorTables::remove);
            session.shutdown();
            this.tables.remove(session.id());
            this.plugin.debug().log("table", "Removed empty table " + session.id());
        } else {
            session.render();
        }
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

    public void start(Player player) {
        MahjongTableSession session = this.tableFor(player.getUniqueId());
        if (session == null) {
            throw new IllegalStateException("Player is not in a table");
        }
        this.plugin.debug().log("table", player.getName() + " started table " + session.id());
        session.startRound();
    }

    public boolean clickTile(Player player, String tableId, UUID ownerId, int tileIndex) {
        MahjongTableSession session = this.tables.get(tableId);
        if (session == null || !session.contains(player.getUniqueId()) || !player.getUniqueId().equals(ownerId)) {
            return false;
        }
        this.plugin.debug().log("table", player.getName() + " clicked tile index " + tileIndex + " on table " + tableId);
        return session.discard(ownerId, tileIndex);
    }

    public void shutdown() {
        this.tableTickTask.cancel();
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

    private static String nextId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(6);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private boolean isViewingAnyTable(UUID playerId) {
        return this.playerTables.containsKey(playerId) || this.spectatorTables.containsKey(playerId);
    }
}
