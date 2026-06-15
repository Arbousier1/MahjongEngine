package top.ellan.mahjong.table.core.round

import top.ellan.mahjong.gb.jni.GbTingCandidate
import top.ellan.mahjong.model.MahjongTile
import top.ellan.mahjong.model.SeatWind
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SichuanRulesEngineTest {
    private val engine: SichuanRulesEngine = DefaultSichuanRulesEngine()

    @Test
    fun `evaluates standard fan list through rules engine`() {
        val result = engine.evaluateFan(
            listOf(
                MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
                MahjongTile.M2, MahjongTile.M3, MahjongTile.M4,
                MahjongTile.M5, MahjongTile.M6, MahjongTile.M7,
                MahjongTile.P2, MahjongTile.P3, MahjongTile.P4,
                MahjongTile.P9
            ),
            emptyList(),
            MahjongTile.P9,
            "DISCARD",
            emptyList(),
            false
        )

        assertTrue(result.valid)
        assertEquals(1, result.totalFan)
        assertContains(result.fans.map { it.name }, "PING_HU")
    }

    @Test
    fun `seven pairs is layered by roots and never double counts gen`() {
        // Plain seven pairs: no four-of-a-kind, so QI_DUI with no separate GEN entry.
        val plain = sevenPairs(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3, MahjongTile.M4, MahjongTile.P6, MahjongTile.P7)
        assertContains(plain.fans.map { it.name }, "QI_DUI")
        assertFalse(plain.fans.any { it.name == "GEN" }, "Seven pairs must not also emit GEN")

        // One four-of-a-kind upgrades to dragon seven pairs (3 fan), still no GEN.
        val dragon = sevenPairs(MahjongTile.M1, MahjongTile.M1, MahjongTile.M3, MahjongTile.M4, MahjongTile.P6, MahjongTile.P7)
        val dragonFan = dragon.fans.first { it.name == "LONG_QI_DUI" }
        assertEquals(3, dragonFan.fan)
        assertFalse(dragon.fans.any { it.name == "GEN" })

        // Two four-of-a-kind => double dragon seven pairs (4 fan).
        val doubleDragon = sevenPairs(MahjongTile.M1, MahjongTile.M1, MahjongTile.M3, MahjongTile.M3, MahjongTile.P6, MahjongTile.P7)
        assertContains(doubleDragon.fans.map { it.name }, "SHUANG_LONG_QI_DUI")

        // Three four-of-a-kind => deluxe dragon seven pairs (5 fan).
        val deluxe = sevenPairs(MahjongTile.M1, MahjongTile.M1, MahjongTile.M3, MahjongTile.M3, MahjongTile.M5, MahjongTile.M5)
        assertContains(deluxe.fans.map { it.name }, "HAO_HUA_LONG_QI_DUI")
    }

    @Test
    fun `seven pairs of all 2-5-8 tiles adds jiang dui`() {
        // All pairs come from 2/5/8 ranks within two suits (one rank is a kong that
        // counts as two pairs) => "将七对" adds JIANG_DUI on top of the layered seven-pair fan.
        val concealed = listOf(
            MahjongTile.M2, MahjongTile.M2,
            MahjongTile.M5, MahjongTile.M5,
            MahjongTile.M8, MahjongTile.M8,
            MahjongTile.P2, MahjongTile.P2,
            MahjongTile.P5, MahjongTile.P5,
            MahjongTile.P8, MahjongTile.P8,
            MahjongTile.P8
        )
        val result = engine.evaluateFan(concealed, emptyList(), MahjongTile.P8, "DISCARD", emptyList(), false)
        val names = result.fans.map { it.name }
        assertContains(names, "JIANG_DUI")
    }

    /**
     * Builds a 14-tile seven-pair hand from six fully-formed pair ranks plus a
     * seventh pair completed by [pairTile] (its matching tile is the winning tile).
     */
    private fun sevenPairs(
        a: MahjongTile,
        b: MahjongTile,
        c: MahjongTile,
        d: MahjongTile,
        e: MahjongTile,
        f: MahjongTile,
        pairTile: MahjongTile = MahjongTile.P9
    ): SichuanRulesEngine.FanResult {
        val concealed = listOf(a, a, b, b, c, c, d, d, e, e, f, f, pairTile)
        return engine.evaluateFan(concealed, emptyList(), pairTile, "DISCARD", emptyList(), false)
    }

    @Test
    fun `missing one suit is required for Sichuan hands`() {
        assertTrue(engine.isMissingOneSuit(listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.P1)))
        assertFalse(engine.isMissingOneSuit(listOf(MahjongTile.M1, MahjongTile.P1, MahjongTile.S1)))
    }

    @Test
    fun `winning deltas handle self draw and discard win`() {
        assertEquals(
            mapOf("EAST" to 4, "SOUTH" to -2, "WEST" to -2),
            engine.winDeltas(SeatWind.EAST, null, "SELF_DRAW", 2, listOf(SeatWind.SOUTH, SeatWind.WEST))
                .associate { it.seat to it.delta }
        )
        assertEquals(
            mapOf("EAST" to 2, "SOUTH" to -2),
            engine.winDeltas(SeatWind.EAST, SeatWind.SOUTH, "DISCARD", 2, listOf(SeatWind.SOUTH, SeatWind.WEST))
                .associate { it.seat to it.delta }
        )
    }

    @Test
    fun `kan deltas support concealed direct and added kong payouts`() {
        assertEquals(
            mapOf("EAST" to 6, "SOUTH" to -2, "WEST" to -2, "NORTH" to -2),
            engine.kanDeltas(SeatWind.EAST, listOf(SeatWind.SOUTH, SeatWind.WEST, SeatWind.NORTH), 2)
                .associate { it.seat to it.delta }
        )
        assertEquals(
            mapOf("EAST" to 3, "SOUTH" to -1, "WEST" to -1, "NORTH" to -1),
            engine.kanDeltas(SeatWind.EAST, listOf(SeatWind.SOUTH, SeatWind.WEST, SeatWind.NORTH), 1)
                .associate { it.seat to it.delta }
        )
    }

    @Test
    fun `exhaustive draw deltas include hua zhu and cha jiao penalties`() {
        val deltas = engine.exhaustiveDrawDeltas(
            listOf(SeatWind.EAST, SeatWind.SOUTH, SeatWind.WEST, SeatWind.NORTH),
            mapOf(
                SeatWind.EAST to listOf(MahjongTile.M1, MahjongTile.P1, MahjongTile.S1),
                SeatWind.SOUTH to listOf(MahjongTile.M1, MahjongTile.P1),
                SeatWind.WEST to listOf(MahjongTile.M2, MahjongTile.P2),
                SeatWind.NORTH to listOf(MahjongTile.M3, MahjongTile.P3)
            ),
            setOf(SeatWind.SOUTH),
            mapOf(SeatWind.SOUTH to engine.bestReadyUnit(listOf(GbTingCandidate("W1", 3)))),
            16
        ).groupingBy { it.seat }.fold(0) { total, delta -> total + delta.delta }

        assertEquals(-48, deltas.getValue("EAST"))
        assertEquals(32, deltas.getValue("SOUTH"))
        assertEquals(8, deltas.getValue("WEST"))
        assertEquals(8, deltas.getValue("NORTH"))
    }
}
