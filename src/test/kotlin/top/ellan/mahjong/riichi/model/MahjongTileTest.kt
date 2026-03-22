package top.ellan.mahjong.riichi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MahjongTileTest {
    @Test
    fun `item model path matches lower-case tile name`() {
        assertEquals("mahjong_tile/east", MahjongTile.EAST.itemModelPath())
        assertEquals("mahjong_tile/m5_red", MahjongTile.M5_RED.itemModelPath())
    }

    @Test
    fun `riichi walls keep 136 tiles`() {
        assertEquals(136, MahjongTile.normalWall.size)
        assertEquals(136, MahjongTile.redFive3Wall.size)
        assertEquals(136, MahjongTile.redFive4Wall.size)
    }

    @Test
    fun `red five walls contain expected aka dora counts`() {
        assertEquals(1, MahjongTile.redFive3Wall.count { it == MahjongTile.M5_RED })
        assertEquals(1, MahjongTile.redFive3Wall.count { it == MahjongTile.P5_RED })
        assertEquals(1, MahjongTile.redFive3Wall.count { it == MahjongTile.S5_RED })
        assertEquals(2, MahjongTile.redFive4Wall.count { it == MahjongTile.P5_RED })
        assertTrue(MahjongTile.redFive4Wall.contains(MahjongTile.M5_RED))
        assertTrue(MahjongTile.redFive4Wall.contains(MahjongTile.S5_RED))
    }
}

