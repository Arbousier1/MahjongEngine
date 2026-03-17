package doublemoon.mahjongcraft.paper.compat

import doublemoon.mahjongcraft.paper.model.MahjongTile
import kotlin.test.Test
import kotlin.test.assertEquals

class CraftEngineTileItemResolverTest {
    @Test
    fun `resolve appends tile names to configured prefix`() {
        assertEquals("mahjongpaper:east", CraftEngineTileItemResolver.resolve("mahjongpaper:", MahjongTile.EAST, false))
        assertEquals("custom:tile_m1", CraftEngineTileItemResolver.resolve("custom:tile_", MahjongTile.M1, false))
    }

    @Test
    fun `resolve uses back item id for face down tiles`() {
        assertEquals("mahjongpaper:back", CraftEngineTileItemResolver.resolve("mahjongpaper:", MahjongTile.UNKNOWN, true))
        assertEquals("custom:tile_back", CraftEngineTileItemResolver.resolve("custom:tile_", MahjongTile.P5, true))
    }
}
