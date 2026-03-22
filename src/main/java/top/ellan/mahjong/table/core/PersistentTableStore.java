package top.ellan.mahjong.table.core;

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.riichi.model.MahjongRule;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class PersistentTableStore {
    private final MahjongPaperPlugin plugin;
    private final boolean enabled;
    private final File file;
    private final AtomicReference<List<TableSnapshot>> pendingSnapshot = new AtomicReference<>();
    private final AtomicBoolean asyncSaveScheduled = new AtomicBoolean();
    private final Object saveLock = new Object();

    PersistentTableStore(MahjongPaperPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.settings().tablePersistenceEnabled();
        this.file = new File(plugin.getDataFolder(), plugin.settings().tablePersistenceFile());
    }

    boolean isEnabled() {
        return this.enabled;
    }

    List<LoadedTable> load() {
        if (!this.enabled || !this.file.isFile()) {
            return List.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection tables = yaml.getConfigurationSection("tables");
        if (tables == null) {
            return List.of();
        }

        List<LoadedTable> loaded = new ArrayList<>();
        for (String key : tables.getKeys(false)) {
            ConfigurationSection table = tables.getConfigurationSection(key);
            if (table == null) {
                continue;
            }
            String worldName = table.getString("world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                this.plugin.getLogger().warning("Skipping persisted table " + key + " because world '" + worldName + "' is unavailable.");
                continue;
            }

            Location center = new Location(
                world,
                table.getDouble("x"),
                table.getDouble("y"),
                table.getDouble("z")
            );
            loaded.add(new LoadedTable(
                table.getString("id", key).toUpperCase(),
                center,
                this.loadVariant(table.getString("variant", "RIICHI")),
                this.loadRule(table.getConfigurationSection("rule")),
                table.getBoolean("botMatch", false)
            ));
        }
        return List.copyOf(loaded);
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
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection tables = yaml.createSection("tables");
        for (TableSnapshot snapshot : snapshots) {
            ConfigurationSection table = tables.createSection(snapshot.id());
            table.set("id", snapshot.id());
            table.set("world", snapshot.worldName());
            table.set("x", snapshot.x());
            table.set("y", snapshot.y());
            table.set("z", snapshot.z());
            table.set("variant", snapshot.variant().name());
            table.set("botMatch", snapshot.botMatch());
            this.saveRule(table.createSection("rule"), snapshot.rule());
        }
        this.file.getParentFile().mkdirs();
        synchronized (this.saveLock) {
            try {
                yaml.save(this.file);
            } catch (IOException ex) {
                this.plugin.getLogger().warning("Failed to save persistent tables: " + ex.getMessage());
            }
        }
    }

    private MahjongRule loadRule(ConfigurationSection section) {
        MahjongRule rule = new MahjongRule();
        if (section == null) {
            return rule;
        }
        rule.setLength(this.enumValue(section.getString("length"), MahjongRule.GameLength.class, rule.getLength()));
        rule.setThinkingTime(this.enumValue(section.getString("thinkingTime"), MahjongRule.ThinkingTime.class, rule.getThinkingTime()));
        rule.setStartingPoints(section.getInt("startingPoints", rule.getStartingPoints()));
        rule.setMinPointsToWin(section.getInt("minPointsToWin", rule.getMinPointsToWin()));
        rule.setMinimumHan(this.enumValue(section.getString("minimumHan"), MahjongRule.MinimumHan.class, rule.getMinimumHan()));
        rule.setSpectate(section.getBoolean("spectate", rule.getSpectate()));
        rule.setRedFive(this.enumValue(section.getString("redFive"), MahjongRule.RedFive.class, rule.getRedFive()));
        rule.setOpenTanyao(section.getBoolean("openTanyao", rule.getOpenTanyao()));
        rule.setLocalYaku(section.getBoolean("localYaku", rule.getLocalYaku()));
        return rule;
    }

    private MahjongVariant loadVariant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return MahjongVariant.RIICHI;
        }
        try {
            return MahjongVariant.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Invalid persisted variant '" + rawValue + "', using RIICHI instead.");
            return MahjongVariant.RIICHI;
        }
    }

    private <E extends Enum<E>> E enumValue(String rawValue, Class<E> enumType, E fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Invalid persisted rule value '" + rawValue + "' for " + enumType.getSimpleName() + ", using " + fallback.name() + " instead.");
            return fallback;
        }
    }

    private void saveRule(ConfigurationSection section, MahjongRule rule) {
        section.set("length", rule.getLength().name());
        section.set("thinkingTime", rule.getThinkingTime().name());
        section.set("startingPoints", rule.getStartingPoints());
        section.set("minPointsToWin", rule.getMinPointsToWin());
        section.set("minimumHan", rule.getMinimumHan().name());
        section.set("spectate", rule.getSpectate());
        section.set("redFive", rule.getRedFive().name());
        section.set("openTanyao", rule.getOpenTanyao());
        section.set("localYaku", rule.getLocalYaku());
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


