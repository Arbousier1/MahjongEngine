package top.ellan.mahjong.model

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TileCatalogAlignmentTest {
    @Test
    fun `java and riichi tile catalogs stay aligned`() {
        val displayNames = MahjongTile.values().map { it.name }.toSet()
        val rulesNames = top.ellan.mahjong.riichi.model.MahjongTile.entries.map { it.name }.toSet()

        assertEquals(rulesNames, displayNames)
    }

    @Test
    fun `generated resource index is available on the classpath`() {
        val indexStream = javaClass.classLoader.getResourceAsStream("resourcepack/mahjong_tile_index.json")
        assertNotNull(indexStream)
        val indexText = indexStream.bufferedReader().use { it.readText() }

        assertContains(indexText, "\"name\": \"east\"")
        assertContains(indexText, "\"item\": \"mahjongcraft:mahjong_tile/east\"")
        assertContains(indexText, "\"name\": \"plum\"")
        assertContains(indexText, "\"item\": \"mahjongcraft:mahjong_tile/plum\"")
    }
}

