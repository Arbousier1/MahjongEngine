package doublemoon.mahjongcraft.paper.compat

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class CraftEngineBundleTest {
    @Test
    fun `generated craftengine items config is bundled`() {
        val stream = javaClass.classLoader.getResourceAsStream("craftengine/mahjongpaper/configuration/items/mahjong_tiles.yml")
        assertNotNull(stream)

        val text = stream.bufferedReader().use { it.readText() }
        assertContains(text, "items:")
        assertContains(text, "mahjongpaper:east:")
        assertContains(text, "item-model: mahjongcraft:mahjong_tile/east")
    }

    @Test
    fun `generated craftengine bundle index is bundled`() {
        val stream = javaClass.classLoader.getResourceAsStream("craftengine/mahjongpaper/_bundle_index.txt")
        assertNotNull(stream)

        val text = stream.bufferedReader().use { it.readText() }
        assertContains(text, "pack.yml")
        assertContains(text, "configuration/items/mahjong_tiles.yml")
        assertContains(text, "resourcepack/assets/mahjongcraft/items/mahjong_tile/east.json")
    }
}
