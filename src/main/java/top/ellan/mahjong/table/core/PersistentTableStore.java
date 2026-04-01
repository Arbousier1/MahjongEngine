package top.ellan.mahjong.table.core;

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.db.DatabaseService;
import top.ellan.mahjong.riichi.model.MahjongRule;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

final class PersistentTableStore {
    private final MahjongPaperPlugin plugin;
    private final boolean enabled;
    private final AtomicReference<List<TableSnapshot>> pendingSnapshot = new AtomicReference<>();
    private final AtomicBoolean asyncSaveScheduled = new AtomicBoolean();
    private final AtomicBoolean missingDatabaseWarningLogged = new AtomicBoolean();

    PersistentTableStore(MahjongPaperPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.settings().tablePersistenceEnabled();
    }

    boolean isEnabled() {
        return this.enabled;
    }

    List<LoadedTable> load() {
        if (!this.enabled) {
            return List.of();
        }
        DatabaseService database = this.plugin.database();
        if (database == null) {
            this.warnMissingDatabase();
            return List.of();
        }
        try {
            List<LoadedTable> loaded = new ArrayList<>();
            for (DatabaseService.PersistentTableRecord row : database.loadPersistentTables()) {
                if (row.id() == null || row.id().isBlank()) {
                    this.plugin.getLogger().warning("Skipping persisted table row with empty table_id.");
                    continue;
                }
                World world = row.worldName() == null ? null : Bukkit.getWorld(row.worldName());
                if (world == null) {
                    this.plugin.getLogger().warning("Skipping persisted table " + row.id() + " because world '" + row.worldName() + "' is unavailable.");
                    continue;
                }
                loaded.add(new LoadedTable(
                    row.id().toUpperCase(),
                    new Location(world, row.x(), row.y(), row.z()),
                    row.variant(),
                    copyRule(row.rule()),
                    row.botMatch()
                ));
            }
            return List.copyOf(loaded);
        } catch (SQLException ex) {
            this.plugin.getLogger().warning("Failed to load persistent tables from " + database.databaseType() + ": " + ex.getMessage());
            return List.of();
        }
    }

    void save(Collection<MahjongTableSession> sessions) {
        if (!this.enabled) {
            return;
        }
        this.pendingSnapshot.set(this.snapshotTables(sessions));
        this.scheduleAsyncSave();
    }

    void flush(Collection<MahjongTableSession> sessions) {
        if (!this.enabled) {
            return;
        }
        List<TableSnapshot> snapshot = this.snapshotTables(sessions);
        this.pendingSnapshot.set(null);
        this.writeSnapshot(snapshot);
    }

    private void scheduleAsyncSave() {
        if (!this.asyncSaveScheduled.compareAndSet(false, true)) {
            return;
        }
        this.plugin.async().execute("save-persistent-tables", this::drainPendingSaves);
    }

    private void drainPendingSaves() {
        try {
            while (true) {
                List<TableSnapshot> snapshot = this.pendingSnapshot.getAndSet(null);
                if (snapshot == null) {
                    return;
                }
                this.writeSnapshot(snapshot);
            }
        } finally {
            this.asyncSaveScheduled.set(false);
            if (this.pendingSnapshot.get() != null) {
                this.scheduleAsyncSave();
            }
        }
    }

    private List<TableSnapshot> snapshotTables(Collection<MahjongTableSession> sessions) {
        List<TableSnapshot> snapshots = new ArrayList<>();
        for (MahjongTableSession session : sessions) {
            if (!session.isPersistentRoom()) {
                continue;
            }
            Location center = session.center();
            snapshots.add(new TableSnapshot(
                session.id(),
                center.getWorld() == null ? null : center.getWorld().getName(),
                center.getX(),
                center.getY(),
                center.getZ(),
                session.configuredVariant(),
                session.configuredRuleSnapshot(),
                session.isBotMatchRoom()
            ));
        }
        return List.copyOf(snapshots);
    }

    private void writeSnapshot(List<TableSnapshot> snapshots) {
        DatabaseService database = this.plugin.database();
        if (database == null) {
            this.warnMissingDatabase();
            return;
        }
        try {
            List<DatabaseService.PersistentTableRecord> rows = snapshots.stream()
                .map(snapshot -> new DatabaseService.PersistentTableRecord(
                    snapshot.id(),
                    snapshot.worldName(),
                    snapshot.x(),
                    snapshot.y(),
                    snapshot.z(),
                    snapshot.variant(),
                    copyRule(snapshot.rule()),
                    snapshot.botMatch()
                ))
                .toList();
            database.replacePersistentTables(rows);
        } catch (SQLException ex) {
            this.plugin.getLogger().warning("Failed to save persistent tables to " + database.databaseType() + ": " + ex.getMessage());
        }
    }

    private void warnMissingDatabase() {
        if (this.missingDatabaseWarningLogged.compareAndSet(false, true)) {
            this.plugin.getLogger().warning("Table persistence is enabled but database is unavailable; persistent tables are temporarily disabled.");
        }
    }

    private static MahjongRule copyRule(MahjongRule rule) {
        if (rule == null) {
            return new MahjongRule();
        }
        return new MahjongRule(
            rule.getLength(),
            rule.getThinkingTime(),
            rule.getStartingPoints(),
            rule.getMinPointsToWin(),
            rule.getMinimumHan(),
            rule.getSpectate(),
            rule.getRedFive(),
            rule.getOpenTanyao(),
            rule.getLocalYaku(),
            rule.getRonMode(),
            rule.getRiichiProfile()
        );
    }

    record LoadedTable(String id, Location center, MahjongVariant variant, MahjongRule rule, boolean botMatch) {
    }

    private record TableSnapshot(
        String id,
        String worldName,
        double x,
        double y,
        double z,
        MahjongVariant variant,
        MahjongRule rule,
        boolean botMatch
    ) {
    }
}
