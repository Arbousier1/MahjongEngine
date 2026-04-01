package top.ellan.mahjong

import top.ellan.mahjong.config.PluginSettings
import top.ellan.mahjong.table.core.MahjongVariant
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginSettingsTest {
    @Test
    fun `from applies defaults and clamps startup rebuild batch size`() {
        val config = YamlConfiguration()
        config.set("tables.startupRebuildBatchSize", 0)
        config.set("ranking.enabled", false)

        val settings = PluginSettings.from(config)

        assertEquals(1, settings.tableStartupRebuildBatchSize())
        assertFalse(settings.tableFreeMoveDuringRound())
        assertFalse(settings.rankingEnabled())
        assertTrue(settings.tablePersistenceEnabled())
        assertEquals("tables.yml", settings.tablePersistenceFile())
        assertEquals("mahjongpaper:", settings.craftEngineTileItemIdPrefix())
        assertEquals("mahjongpaper:", settings.craftEngineRiichiTileItemIdPrefix())
        assertEquals("mahjongpaper:", settings.craftEngineGbTileItemIdPrefix())
        assertEquals("mahjongpaper:", settings.craftEngineTileItemIdPrefix(MahjongVariant.RIICHI))
        assertEquals("mahjongpaper:", settings.craftEngineTileItemIdPrefix(MahjongVariant.GB))
        assertEquals("mahjongpaper:", settings.craftEngineTileItemIdPrefix(MahjongVariant.SICHUAN))
        assertEquals("mahjongpaper:table_visual", settings.craftEngineTableFurnitureId())
        assertEquals("mahjongpaper:seat_chair", settings.craftEngineSeatFurnitureId())
        assertEquals("SILVER", settings.rankingEastRoom())
        assertEquals("GOLD", settings.rankingSouthRoom())
    }

    @Test
    fun `from supports legacy aliases for table persistence and batch size`() {
        val config = YamlConfiguration()
        config.set("tablePersistence.enabled", false)
        config.set("tablePersistence.file", "custom.yml")
        config.set("tables.startup-rebuild-batch-size", 7)
        config.set("tables.allow-free-move-during-round", true)
        config.set("craftengine.items.tile-item-id-prefix", "custom:tile_")
        config.set("craftengine.furniture.table-furniture-id", "custom:table")
        config.set("craftengine.furniture.seat-furniture-id", "custom:chair")
        config.set("ranking.eastRoom", "jade")
        config.set("ranking.southRoom", "throne")

        val settings = PluginSettings.from(config)

        assertFalse(settings.tablePersistenceEnabled())
        assertEquals("custom.yml", settings.tablePersistenceFile())
        assertEquals(7, settings.tableStartupRebuildBatchSize())
        assertTrue(settings.tableFreeMoveDuringRound())
        assertEquals("custom:tile_", settings.craftEngineTileItemIdPrefix())
        assertEquals("custom:tile_", settings.craftEngineRiichiTileItemIdPrefix())
        assertEquals("custom:tile_", settings.craftEngineGbTileItemIdPrefix())
        assertEquals("custom:table", settings.craftEngineTableFurnitureId())
        assertEquals("custom:chair", settings.craftEngineSeatFurnitureId())
        assertEquals("jade", settings.rankingEastRoom())
        assertEquals("throne", settings.rankingSouthRoom())
    }

    @Test
    fun `from supports separate riichi and gb tile item prefixes`() {
        val config = YamlConfiguration()
        config.set("integrations.craftengine.items.tileItemIdPrefix", "shared:")
        config.set("integrations.craftengine.items.riichiTileItemIdPrefix", "riichi:")
        config.set("integrations.craftengine.items.gbTileItemIdPrefix", "gb:")

        val settings = PluginSettings.from(config)

        assertEquals("shared:", settings.craftEngineTileItemIdPrefix())
        assertEquals("riichi:", settings.craftEngineTileItemIdPrefix(MahjongVariant.RIICHI))
        assertEquals("gb:", settings.craftEngineTileItemIdPrefix(MahjongVariant.GB))
        assertEquals("gb:", settings.craftEngineTileItemIdPrefix(MahjongVariant.SICHUAN))
    }

    @Test
    fun `from exposes grouped strong typed snapshots`() {
        val config = YamlConfiguration()
        config.set("debug.enabled", true)
        config.set("debug.categories", listOf("database", "render"))
        config.set("database.connection.type", "mariadb")
        config.set("database.connection.host", "db.local")
        config.set("database.connection.port", 3307)
        config.set("database.connection.name", "mahjong")
        config.set("database.credentials.username", "mahjong")
        config.set("database.credentials.password", "secret")
        config.set("tables.persistence.enabled", false)
        config.set("tables.persistence.file", "persist.yml")
        config.set("integrations.craftengine.bundle.folder", "pack-a")
        config.set("integrations.craftengine.compatibility.injectAntiCheatPacketEventsMappings", false)
        config.set("integrations.craftengine.furniture.preferHitboxInteraction", false)

        val settings = PluginSettings.from(config)

        assertTrue(settings.debug().enabled())
        assertEquals(listOf("database", "render"), settings.debug().categories())
        assertEquals("mariadb", settings.database().type())
        assertEquals("db.local", settings.database().connection().host())
        assertEquals(3307, settings.database().connection().port())
        assertEquals("mahjong", settings.database().connection().name())
        assertEquals("mahjong", settings.database().credentials().username())
        assertEquals("secret", settings.database().credentials().password())
        assertFalse(settings.tables().persistence().enabled())
        assertEquals("persist.yml", settings.tables().persistence().file())
        assertEquals("pack-a", settings.craftEngine().bundleFolder())
        assertFalse(settings.craftEngine().injectAntiCheatPacketEventsMappings())
        assertFalse(settings.craftEngine().furniture().preferHitboxInteraction())
    }
}

