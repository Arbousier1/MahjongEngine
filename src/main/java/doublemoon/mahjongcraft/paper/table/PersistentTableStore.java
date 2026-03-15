package doublemoon.mahjongcraft.paper.table;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class PersistentTableStore {
    private final MahjongPaperPlugin plugin;
    private final boolean enabled;
    private final File file;

    PersistentTableStore(MahjongPaperPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tablePersistence");
        this.enabled = section == null || section.getBoolean("enabled", true);
        String fileName = section == null ? "tables.yml" : section.getString("file", "tables.yml");
        this.file = new File(plugin.getDataFolder(), fileName);
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
                this.loadRule(table.getConfigurationSection("rule"))
            ));
        }
        return List.copyOf(loaded);
    }

    void save(Collection<MahjongTableSession> sessions) {
        if (!this.enabled) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection tables = yaml.createSection("tables");
        for (MahjongTableSession session : sessions) {
            if (!session.isPersistentRoom()) {
                continue;
            }
            ConfigurationSection table = tables.createSection(session.id());
            Location center = session.center();
            table.set("id", session.id());
            table.set("world", center.getWorld() == null ? null : center.getWorld().getName());
            table.set("x", center.getX());
            table.set("y", center.getY());
            table.set("z", center.getZ());
            this.saveRule(table.createSection("rule"), session.configuredRuleSnapshot());
        }
        this.file.getParentFile().mkdirs();
        try {
            yaml.save(this.file);
        } catch (IOException ex) {
            this.plugin.getLogger().warning("Failed to save persistent tables: " + ex.getMessage());
        }
    }

    private MahjongRule loadRule(ConfigurationSection section) {
        MahjongRule rule = new MahjongRule();
        if (section == null) {
            return rule;
        }
        rule.setLength(MahjongRule.GameLength.valueOf(section.getString("length", rule.getLength().name())));
        rule.setThinkingTime(MahjongRule.ThinkingTime.valueOf(section.getString("thinkingTime", rule.getThinkingTime().name())));
        rule.setStartingPoints(section.getInt("startingPoints", rule.getStartingPoints()));
        rule.setMinPointsToWin(section.getInt("minPointsToWin", rule.getMinPointsToWin()));
        rule.setMinimumHan(MahjongRule.MinimumHan.valueOf(section.getString("minimumHan", rule.getMinimumHan().name())));
        rule.setSpectate(section.getBoolean("spectate", rule.getSpectate()));
        rule.setRedFive(MahjongRule.RedFive.valueOf(section.getString("redFive", rule.getRedFive().name())));
        rule.setOpenTanyao(section.getBoolean("openTanyao", rule.getOpenTanyao()));
        rule.setLocalYaku(section.getBoolean("localYaku", rule.getLocalYaku()));
        return rule;
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

    record LoadedTable(String id, Location center, MahjongRule rule) {
    }
}
