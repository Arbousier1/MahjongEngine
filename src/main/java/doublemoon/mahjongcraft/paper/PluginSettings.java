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
    private final int tableStartupRebuildBatchSize;
    private final String craftEngineSeatFurnitureId;
    private final boolean rankingEnabled;
    private final String rankingEastRoom;
    private final String rankingSouthRoom;

    private PluginSettings(
        ConfigurationSection debugSection,
        ConfigurationSection databaseSection,
        ConfigurationSection craftEngineSection,
        boolean databaseFailOnError,
        boolean tablePersistenceEnabled,
        String tablePersistenceFile,
        int tableStartupRebuildBatchSize,
        String craftEngineSeatFurnitureId,
        boolean rankingEnabled,
        String rankingEastRoom,
        String rankingSouthRoom
    ) {
        this.debugSection = debugSection;
        this.databaseSection = databaseSection;
        this.craftEngineSection = craftEngineSection;
        this.databaseFailOnError = databaseFailOnError;
        this.tablePersistenceEnabled = tablePersistenceEnabled;
        this.tablePersistenceFile = tablePersistenceFile;
        this.tableStartupRebuildBatchSize = tableStartupRebuildBatchSize;
        this.craftEngineSeatFurnitureId = craftEngineSeatFurnitureId;
        this.rankingEnabled = rankingEnabled;
        this.rankingEastRoom = rankingEastRoom;
        this.rankingSouthRoom = rankingSouthRoom;
    }

    public static PluginSettings from(FileConfiguration config) {
        ConfigurationSection tablesSection = ConfigAccess.firstSection(config, "tables");
        ConfigurationSection databaseSection = ConfigAccess.firstSection(config, "database");
        ConfigurationSection rankingSection = ConfigAccess.firstSection(config, "ranking");
        ConfigurationSection craftEngineSection = ConfigAccess.firstSection(config, "integrations.craftengine", "craftengine");
        ConfigurationSection craftEngineFurnitureSection = ConfigAccess.firstSection(config, "integrations.craftengine.furniture", "craftengine.furniture");
        ConfigurationSection tablePersistenceSection = ConfigAccess.firstSection(config, "tables.persistence", "tablePersistence");
        return new PluginSettings(
            ConfigAccess.firstSection(config, "debug"),
            databaseSection,
            craftEngineSection,
            ConfigAccess.bool(databaseSection, false, "failOnError"),
            ConfigAccess.bool(tablePersistenceSection, true, "enabled"),
            ConfigAccess.string(tablePersistenceSection, "tables.yml", "file"),
            Math.max(1, ConfigAccess.integer(tablesSection, 3, "startupRebuildBatchSize", "startup-rebuild-batch-size")),
            ConfigAccess.string(craftEngineFurnitureSection, "mahjongpaper:seat_chair", "seatFurnitureId", "seat-furniture-id"),
            ConfigAccess.bool(rankingSection, true, "enabled"),
            ConfigAccess.string(rankingSection, "SILVER", "eastRoom"),
            ConfigAccess.string(rankingSection, "GOLD", "southRoom")
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

    public int tableStartupRebuildBatchSize() {
        return this.tableStartupRebuildBatchSize;
    }

    public String craftEngineSeatFurnitureId() {
        return this.craftEngineSeatFurnitureId;
    }

    public boolean rankingEnabled() {
        return this.rankingEnabled;
    }

    public String rankingEastRoom() {
        return this.rankingEastRoom;
    }

    public String rankingSouthRoom() {
        return this.rankingSouthRoom;
    }
}
