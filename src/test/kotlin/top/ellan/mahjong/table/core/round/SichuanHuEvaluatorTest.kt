package top.ellan.mahjong.table.core.round

import top.ellan.mahjong.model.MahjongTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SichuanHuEvaluatorTest {
    @Test
    fun `standard hand can win`() {
        val concealed = listOf(
            MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
            MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
            MahjongTile.M5, MahjongTile.M6, MahjongTile.M7,
            MahjongTile.P2, MahjongTile.P3, MahjongTile.P4,
            MahjongTile.S9
        )

        assertTrue(SichuanHuEvaluator.canWin(concealed, MahjongTile.S9, 0))
    }

    @Test
    fun `seven pairs hand can win`() {
        val concealed = listOf(
            MahjongTile.M1, MahjongTile.M1,
            MahjongTile.M2, MahjongTile.M2,
            MahjongTile.M3, MahjongTile.M3,
            MahjongTile.P4, MahjongTile.P4,
            MahjongTile.P5, MahjongTile.P5,
            MahjongTile.S6, MahjongTile.S6,
            MahjongTile.S7
        )

        assertTrue(SichuanHuEvaluator.canWin(concealed, MahjongTile.S7, 0))
        assertEquals(listOf(MahjongTile.S7), SichuanHuEvaluator.waitingTiles(concealed, 0))
    }

    @Test
    fun `honor tile is not a valid sichuan winning tile`() {
        val concealed = listOf(
            MahjongTile.M1, MahjongTile.M2, MahjongTile.M3,
            MahjongTile.M4, MahjongTile.M5, MahjongTile.M6,
            MahjongTile.M7, MahjongTile.M8, MahjongTile.M9,
            MahjongTile.P1, MahjongTile.P2, MahjongTile.P3,
            MahjongTile.P4
        )

        assertFalse(SichuanHuEvaluator.canWin(concealed, MahjongTile.EAST, 0))
    }
}
