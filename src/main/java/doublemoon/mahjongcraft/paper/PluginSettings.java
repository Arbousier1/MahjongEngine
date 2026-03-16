package doublemoon.mahjongcraft.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class PluginSettings {
    private final ConfigurationSection debugSection;
    private final ConfigurationSection databaseSection;
    private final ConfigurationSection craftEngineSection;
    private final boolean databaseFailOnError;
    private final boolean tablePersistenceEnabled;
    private final String tablePersistenceFile;

    private PluginSettings(
        ConfigurationSection debugSection,
        ConfigurationSection databaseSection,
        ConfigurationSection craftEngineSection,
        boolean databaseFailOnError,
        boolean tablePersistenceEnabled,
        String tablePersistenceFile
    ) {
        this.debugSection = debugSection;
        this.databaseSection = databaseSection;
        this.craftEngineSection = craftEngineSection;
        this.databaseFailOnError = databaseFailOnError;
        this.tablePersistenceEnabled = tablePersistenceEnabled;
        this.tablePersistenceFile = tablePersistenceFile;
    }

    public static PluginSettings from(FileConfiguration config) {
        ConfigurationSection databaseSection = config.getConfigurationSection("database");
        ConfigurationSection tablePersistenceSection = config.getConfigurationSection("tablePersistence");
        return new PluginSettings(
            config.getConfigurationSection("debug"),
            databaseSection,
            config.getConfigurationSection("craftengine"),
            databaseSection != null && databaseSection.getBoolean("failOnError", false),
            tablePersistenceSection == null || tablePersistenceSection.getBoolean("enabled", true),
            tablePersistenceSection == null ? "tables.yml" : tablePersistenceSection.getString("file", "tables.yml")
        );
    }

    public ConfigurationSection debugSection() {
        return this.debugSection;
    }

    public ConfigurationSection databaseSection() {
        return this.databaseSection;
    }

    public ConfigurationSection craftEngineSection() {
        return this.craftEngineSection;
    }

    public boolean databaseFailOnError() {
        return this.databaseFailOnError;
    }

    public boolean tablePersistenceEnabled() {
        return this.tablePersistenceEnabled;
    }

    public String tablePersistenceFile() {
        return this.tablePersistenceFile;
    }
}
