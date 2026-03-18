package doublemoon.mahjongcraft.paper.config

import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConfigTemplateParseTest {
    @Test
    fun `all bundled config templates parse as yaml`() {
        listOf("config.yml", "config_zh_CN.yml", "config_zh_TW.yml").forEach { resource ->
            val stream = javaClass.classLoader.getResourceAsStream(resource)
            assertNotNull(stream, "Missing config template resource: $resource")

            val yaml = YamlConfiguration()
            yaml.loadFromString(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })

            assertEquals("mahjongpaper:", yaml.getString("integrations.craftengine.items.tileItemIdPrefix"))
            assertEquals("mahjongpaper:", yaml.getString("integrations.craftengine.items.riichiTileItemIdPrefix"))
            assertEquals("mahjongpaper:", yaml.getString("integrations.craftengine.items.gbTileItemIdPrefix"))
        }
    }
}
