package doublemoon.mahjongcraft.paper.config;

import doublemoon.mahjongcraft.paper.table.core.MahjongVariant;
import java.util.List;
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
    private final String craftEngineSharedTileItemIdPrefix;
    private final String craftEngineRiichiTileItemIdPrefix;
    private final String craftEngineGbTileItemIdPrefix;
    private final String craftEngineTableFurnitureId;
    private final String craftEngineSeatFurnitureId;
    private final List<String> craftEngineProtectedFurnitureIds;
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
        String craftEngineSharedTileItemIdPrefix,
        String craftEngineRiichiTileItemIdPrefix,
        String craftEngineGbTileItemIdPrefix,
        String craftEngineTableFurnitureId,
        String craftEngineSeatFurnitureId,
        List<String> craftEngineProtectedFurnitureIds,
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
        this.craftEngineSharedTileItemIdPrefix = craftEngineSharedTileItemIdPrefix;
        this.craftEngineRiichiTileItemIdPrefix = craftEngineRiichiTileItemIdPrefix;
        this.craftEngineGbTileItemIdPrefix = craftEngineGbTileItemIdPrefix;
        this.craftEngineTableFurnitureId = craftEngineTableFurnitureId;
        this.craftEngineSeatFurnitureId = craftEngineSeatFurnitureId;
        this.craftEngineProtectedFurnitureIds = List.copyOf(craftEngineProtectedFurnitureIds);
        this.rankingEnabled = rankingEnabled;
        this.rankingEastRoom = rankingEastRoom;
        this.rankingSouthRoom = rankingSouthRoom;
    }

    public static PluginSettings from(FileConfiguration config) {
        ConfigurationSection tablesSection = ConfigAccess.firstSection(config, "tables");
        ConfigurationSection databaseSection = ConfigAccess.firstSection(config, "database");
        ConfigurationSection rankingSection = ConfigAccess.firstSection(config, "ranking");
        ConfigurationSection craftEngineSection = ConfigAccess.firstSection(config, "integrations.craftengine", "craftengine");
        ConfigurationSection craftEngineItemsSection = ConfigAccess.firstSection(config, "integrations.craftengine.items", "craftengine.items");
        ConfigurationSection craftEngineFurnitureSection = ConfigAccess.firstSection(config, "integrations.craftengine.furniture", "craftengine.furniture");
        ConfigurationSection tablePersistenceSection = ConfigAccess.firstSection(config, "tables.persistence", "tablePersistence");
        String sharedTileItemIdPrefix = ConfigAccess.string(craftEngineItemsSection, "mahjongpaper:", "tileItemIdPrefix", "tile-item-id-prefix");
        return new PluginSettings(
            ConfigAccess.firstSection(config, "debug"),
            databaseSection,
            craftEngineSection,
            ConfigAccess.bool(databaseSection, false, "failOnError"),
            ConfigAccess.bool(tablePersistenceSection, true, "enabled"),
            ConfigAccess.string(tablePersistenceSection, "tables.yml", "file"),
            Math.max(1, ConfigAccess.integer(tablesSection, 3, "startupRebuildBatchSize", "startup-rebuild-batch-size")),
            sharedTileItemIdPrefix,
            ConfigAccess.string(craftEngineItemsSection, sharedTileItemIdPrefix, "riichiTileItemIdPrefix", "riichi-tile-item-id-prefix"),
            ConfigAccess.string(craftEngineItemsSection, sharedTileItemIdPrefix, "gbTileItemIdPrefix", "gb-tile-item-id-prefix"),
            ConfigAccess.string(craftEngineFurnitureSection, "mahjongpaper:table_visual", "tableFurnitureId", "table-furniture-id"),
            ConfigAccess.string(craftEngineFurnitureSection, "mahjongpaper:seat_chair", "seatFurnitureId", "seat-furniture-id"),
            ConfigAccess.stringList(craftEngineFurnitureSection, "protectedFurnitureIds", "protected-furniture-ids").stream()
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList(),
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

    public String craftEngineTileItemIdPrefix() {
        return this.craftEngineSharedTileItemIdPrefix;
    }

    public String craftEngineRiichiTileItemIdPrefix() {
        return this.craftEngineRiichiTileItemIdPrefix;
    }

    public String craftEngineGbTileItemIdPrefix() {
        return this.craftEngineGbTileItemIdPrefix;
    }

    public String craftEngineTileItemIdPrefix(MahjongVariant variant) {
        if (variant == MahjongVariant.GB) {
            return this.craftEngineGbTileItemIdPrefix;
        }
        return this.craftEngineRiichiTileItemIdPrefix;
    }

    public String craftEngineTableFurnitureId() {
        return this.craftEngineTableFurnitureId;
    }

    public String craftEngineSeatFurnitureId() {
        return this.craftEngineSeatFurnitureId;
    }

    public List<String> craftEngineProtectedFurnitureIds() {
        return this.craftEngineProtectedFurnitureIds;
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
