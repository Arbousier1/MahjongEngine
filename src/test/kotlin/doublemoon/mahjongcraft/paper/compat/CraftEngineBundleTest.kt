package doublemoon.mahjongcraft.paper.compat

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

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

    @Test
    fun `table hitbox stays centered 3x3 in craftengine bundle`() {
        val stream = javaClass.classLoader.getResourceAsStream("craftengine/mahjongpaper/configuration/items/mahjong_tiles.yml")
        assertNotNull(stream)

        val text = stream.bufferedReader().use { it.readText() }
        assertContains(text, "mahjongpaper:table_hitbox:")
        assertContains(text, "position: -1,-1,-1")
        assertContains(text, "position: -1,-1,0")
        assertContains(text, "position: -1,-1,1")
        assertContains(text, "position: 0,-1,-1")
        assertContains(text, "position: 0,-1,0")
        assertContains(text, "position: 0,-1,1")
        assertContains(text, "position: 1,-1,-1")
        assertContains(text, "position: 1,-1,0")
        assertContains(text, "position: 1,-1,1")
    }

    @Test
    fun `seat furniture hitboxes are lowered by half a block in craftengine bundle`() {
        val stream = javaClass.classLoader.getResourceAsStream("craftengine/mahjongpaper/configuration/items/mahjong_tiles.yml")
        assertNotNull(stream)

        val text = stream.bufferedReader().use { it.readText() }
        assertTrue(
            Regex(
                """mahjongpaper:seat_chair:.*?hitboxes:\s+- type: shulker\s+position: 0,-1\.5,0""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ).containsMatchIn(text)
        )
        assertTrue(
            Regex(
                """mahjongpaper:seat_hitbox:.*?hitboxes:\s+- type: shulker\s+position: 0,-1\.5,0""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ).containsMatchIn(text)
        )
    }

    @Test
    fun `hand tile hitbox width stays narrower than hand tile spacing`() {
        val stream = javaClass.classLoader.getResourceAsStream("craftengine/mahjongpaper/configuration/items/mahjong_tiles.yml")
        assertNotNull(stream)

        val text = stream.bufferedReader().use { it.readText() }
        val match = Regex(
            """mahjongpaper:hand_tile_hitbox:.*?hitboxes:\s+- type: interaction\s+position: 0,0,0\s+width: ([0-9.]+)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(text) ?: fail("Could not find hand tile hitbox width in generated CraftEngine bundle")

        val width = match.groupValues[1].toDouble()
        val handTileStep = 0.1125 + 0.0025
        assertTrue(width < handTileStep, "Expected hand tile hitbox width $width to stay below hand tile step $handTileStep")
    }
}
