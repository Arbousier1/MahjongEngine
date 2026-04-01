package top.ellan.mahjong.config;

import top.ellan.mahjong.table.core.MahjongVariant;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class PluginSettings {
    private final DebugSettings debug;
    private final DatabaseSettings database;
    private final TablesSettings tables;
    private final RankingSettings ranking;
    private final CraftEngineSettings craftEngine;

    private PluginSettings(
        DebugSettings debug,
        DatabaseSettings database,
        TablesSettings tables,
        RankingSettings ranking,
        CraftEngineSettings craftEngine
    ) {
        this.debug = debug;
        this.database = database;
        this.tables = tables;
        this.ranking = ranking;
        this.craftEngine = craftEngine;
    }

    public static PluginSettings from(FileConfiguration config) {
        ConfigurationSection debugSection = ConfigAccess.firstSection(config, "debug");
        ConfigurationSection databaseSection = ConfigAccess.firstSection(config, "database");
        ConfigurationSection connectionSection = ConfigAccess.firstSection(config, "database.connection", "database");
        ConfigurationSection credentialsSection = ConfigAccess.firstSection(config, "database.credentials", "database");
        ConfigurationSection h2Section = ConfigAccess.firstSection(config, "database.h2");
        ConfigurationSection poolSection = ConfigAccess.firstSection(config, "database.pool");
        ConfigurationSection tablesSection = ConfigAccess.firstSection(config, "tables");
        ConfigurationSection tablePersistenceSection = ConfigAccess.firstSection(config, "tables.persistence", "tablePersistence");
        ConfigurationSection rankingSection = ConfigAccess.firstSection(config, "ranking");
        ConfigurationSection craftEngineSection = ConfigAccess.firstSection(config, "integrations.craftengine", "craftengine");
        ConfigurationSection craftEngineItemsSection = ConfigAccess.firstSection(config, "integrations.craftengine.items", "craftengine.items");
        ConfigurationSection craftEngineFurnitureSection = ConfigAccess.firstSection(config, "integrations.craftengine.furniture", "craftengine.furniture");
        ConfigurationSection craftEngineCompatibilitySection = ConfigAccess.firstSection(
            config,
            "integrations.craftengine.compatibility",
            "craftengine.compatibility"
        );
        ConfigurationSection craftEngineBundleSection = ConfigAccess.firstSection(config, "integrations.craftengine.bundle", "craftengine.bundle");

        String sharedTileItemIdPrefix = ConfigAccess.string(craftEngineItemsSection, "mahjongpaper:", "tileItemIdPrefix", "tile-item-id-prefix");
        DebugSettings debug = new DebugSettings(
            ConfigAccess.bool(debugSection, false, "enabled"),
            ConfigAccess.stringList(debugSection, "categories")
        );
        DatabaseSettings database = new DatabaseSettings(
            ConfigAccess.bool(databaseSection, true, "enabled"),
            ConfigAccess.bool(databaseSection, false, "failOnError"),
            ConfigAccess.string(connectionSection, "h2", "type", "connection.type").trim().toLowerCase(java.util.Locale.ROOT),
            new DatabaseConnectionSettings(
                ConfigAccess.string(connectionSection, "127.0.0.1", "host", "connection.host"),
                ConfigAccess.integer(connectionSection, 3306, "port", "connection.port"),
                ConfigAccess.string(connectionSection, "mahjongpaper", "name", "connection.name"),
                ConfigAccess.string(connectionSection, "useUnicode=true&characterEncoding=utf8&useSsl=false", "parameters", "connection.parameters")
            ),
            new DatabaseCredentialsSettings(
                ConfigAccess.string(credentialsSection, "root", "username"),
                ConfigAccess.string(credentialsSection, "change_me", "password")
            ),
            new DatabaseH2Settings(
                ConfigAccess.string(h2Section, "data/mahjongpaper", "path"),
                ConfigAccess.string(h2Section, "sa", "username"),
                ConfigAccess.string(h2Section, "", "password"),
                ConfigAccess.string(h2Section, "MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH", "parameters")
            ),
            new DatabasePoolSettings(
                ConfigAccess.integer(poolSection, 10, "maxSize", "maximumPoolSize"),
                ConfigAccess.integer(poolSection, 2, "minIdle", "minimumIdle"),
                ConfigAccess.longValue(poolSection, 10000L, "connectionTimeoutMillis")
            )
        );
        TablesSettings tables = new TablesSettings(
            Math.max(1, ConfigAccess.integer(tablesSection, 3, "startupRebuildBatchSize", "startup-rebuild-batch-size")),
            ConfigAccess.bool(tablesSection, false, "allowFreeMoveDuringRound", "allow-free-move-during-round"),
            new TablePersistenceSettings(
                ConfigAccess.bool(tablePersistenceSection, true, "enabled"),
                ConfigAccess.string(tablePersistenceSection, "tables.yml", "file")
            )
        );
        RankingSettings ranking = new RankingSettings(
            ConfigAccess.bool(rankingSection, true, "enabled"),
            ConfigAccess.string(rankingSection, "SILVER", "eastRoom"),
            ConfigAccess.string(rankingSection, "GOLD", "southRoom")
        );
        CraftEngineSettings craftEngine = new CraftEngineSettings(
            ConfigAccess.bool(craftEngineSection, true, "exportBundleOnEnable", "bundle.exportOnEnable"),
            ConfigAccess.string(craftEngineBundleSection, "mahjongpaper", "folder", "bundleFolder"),
            ConfigAccess.bool(
                craftEngineCompatibilitySection,
                true,
                "injectAntiCheatPacketEventsMappings",
                "compatibility.injectAntiCheatPacketEventsMappings"
            ),
            new CraftEngineItemsSettings(
                ConfigAccess.bool(craftEngineItemsSection, true, "preferCustomItems", "items.preferCustomItems"),
                sharedTileItemIdPrefix,
                ConfigAccess.string(craftEngineItemsSection, sharedTileItemIdPrefix, "riichiTileItemIdPrefix", "riichi-tile-item-id-prefix"),
                ConfigAccess.string(craftEngineItemsSection, sharedTileItemIdPrefix, "gbTileItemIdPrefix", "gb-tile-item-id-prefix")
            ),
            new CraftEngineFurnitureSettings(
                ConfigAccess.bool(craftEngineFurnitureSection, true, "preferHitboxInteraction", "furniture.preferHitboxInteraction"),
                ConfigAccess.string(craftEngineFurnitureSection, "mahjongpaper:table_visual", "tableFurnitureId", "table-furniture-id"),
                ConfigAccess.string(craftEngineFurnitureSection, "mahjongpaper:seat_chair", "seatFurnitureId", "seat-furniture-id")
            )
        );
        return new PluginSettings(debug, database, tables, ranking, craftEngine);
    }

    public DebugSettings debug() {
        return this.debug;
    }

    public DatabaseSettings database() {
        return this.database;
    }

    public TablesSettings tables() {
        return this.tables;
    }

    public RankingSettings ranking() {
        return this.ranking;
    }

    public CraftEngineSettings craftEngine() {
        return this.craftEngine;
    }

    public boolean databaseFailOnError() {
        return this.database.failOnError();
    }

    public boolean tablePersistenceEnabled() {
        return this.tables.persistence().enabled();
    }

    public String tablePersistenceFile() {
        return this.tables.persistence().file();
    }

    public int tableStartupRebuildBatchSize() {
        return this.tables.startupRebuildBatchSize();
    }

    public boolean tableFreeMoveDuringRound() {
        return this.tables.allowFreeMoveDuringRound();
    }

    public String craftEngineTileItemIdPrefix() {
        return this.craftEngine.items().tileItemIdPrefix();
    }

    public String craftEngineRiichiTileItemIdPrefix() {
        return this.craftEngine.items().riichiTileItemIdPrefix();
    }

    public String craftEngineGbTileItemIdPrefix() {
        return this.craftEngine.items().gbTileItemIdPrefix();
    }

    public String craftEngineTileItemIdPrefix(MahjongVariant variant) {
        if (variant == MahjongVariant.GB) {
            return this.craftEngine.items().gbTileItemIdPrefix();
        }
        return this.craftEngine.items().riichiTileItemIdPrefix();
    }

    public String craftEngineTableFurnitureId() {
        return this.craftEngine.furniture().tableFurnitureId();
    }

    public String craftEngineSeatFurnitureId() {
        return this.craftEngine.furniture().seatFurnitureId();
    }

    public boolean rankingEnabled() {
        return this.ranking.enabled();
    }

    public String rankingEastRoom() {
        return this.ranking.eastRoom();
    }

    public String rankingSouthRoom() {
        return this.ranking.southRoom();
    }

    public record DebugSettings(boolean enabled, java.util.List<String> categories) {
        public DebugSettings {
            categories = categories == null ? java.util.List.of() : java.util.List.copyOf(categories);
        }
    }

    public record DatabaseSettings(
        boolean enabled,
        boolean failOnError,
        String type,
        DatabaseConnectionSettings connection,
        DatabaseCredentialsSettings credentials,
        DatabaseH2Settings h2,
        DatabasePoolSettings pool
    ) {
    }

    public record DatabaseConnectionSettings(String host, int port, String name, String parameters) {
    }

    public record DatabaseCredentialsSettings(String username, String password) {
    }

    public record DatabaseH2Settings(String path, String username, String password, String parameters) {
    }

    public record DatabasePoolSettings(int maxSize, int minIdle, long connectionTimeoutMillis) {
    }

    public record TablesSettings(
        int startupRebuildBatchSize,
        boolean allowFreeMoveDuringRound,
        TablePersistenceSettings persistence
    ) {
    }

    public record TablePersistenceSettings(boolean enabled, String file) {
    }

    public record RankingSettings(boolean enabled, String eastRoom, String southRoom) {
    }

    public record CraftEngineSettings(
        boolean exportBundleOnEnable,
        String bundleFolder,
        boolean injectAntiCheatPacketEventsMappings,
        CraftEngineItemsSettings items,
        CraftEngineFurnitureSettings furniture
    ) {
    }

    public record CraftEngineItemsSettings(
        boolean preferCustomItems,
        String tileItemIdPrefix,
        String riichiTileItemIdPrefix,
        String gbTileItemIdPrefix
    ) {
    }

    public record CraftEngineFurnitureSettings(
        boolean preferHitboxInteraction,
        String tableFurnitureId,
        String seatFurnitureId
    ) {
    }
}
