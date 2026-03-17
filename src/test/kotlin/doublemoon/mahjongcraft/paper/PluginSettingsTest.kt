package doublemoon.mahjongcraft.paper

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
        assertFalse(settings.rankingEnabled())
        assertTrue(settings.tablePersistenceEnabled())
        assertEquals("tables.yml", settings.tablePersistenceFile())
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
        config.set("craftengine.furniture.seat-furniture-id", "custom:chair")
        config.set("ranking.eastRoom", "jade")
        config.set("ranking.southRoom", "throne")

        val settings = PluginSettings.from(config)

        assertFalse(settings.tablePersistenceEnabled())
        assertEquals("custom.yml", settings.tablePersistenceFile())
        assertEquals(7, settings.tableStartupRebuildBatchSize())
        assertEquals("custom:chair", settings.craftEngineSeatFurnitureId())
        assertEquals("jade", settings.rankingEastRoom())
        assertEquals("throne", settings.rankingSouthRoom())
    }
}
