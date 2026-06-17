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
        val plain = sevenPairs(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3, MahjongTile.M4, MahjongTile.P6, MahjongTile.P7)
        assertContains(plain.fans.map { it.name }, "QI_DUI")
        assertFalse(plain.fans.any { it.name == "GEN" }, "Seven pairs must not also emit GEN")

        val dragon = sevenPairs(MahjongTile.M1, MahjongTile.M1, MahjongTile.M3, MahjongTile.M4, MahjongTile.P6, MahjongTile.P7)
        val dragonFan = dragon.fans.first { it.name == "LONG_QI_DUI" }
        assertEquals(3, dragonFan.fan)
        assertFalse(dragon.fans.any { it.name == "GEN" })

        val doubleDragon = sevenPairs(MahjongTile.M1, MahjongTile.M1, MahjongTile.M3, MahjongTile.M3, MahjongTile.P6, MahjongTile.P7)
        assertContains(doubleDragon.fans.map { it.name }, "SHUANG_LONG_QI_DUI")

        val deluxe = sevenPairs(MahjongTile.M1, MahjongTile.M1, MahjongTile.M3, MahjongTile.M3, MahjongTile.M5, MahjongTile.M5)
        assertContains(deluxe.fans.map { it.name }, "HAO_HUA_LONG_QI_DUI")
    }

    @Test
    fun `seven pairs of all 2-5-8 tiles stays a seven pairs hand`() {
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

        assertFalse("JIANG_DUI" in names)
        assertContains(names, "LONG_QI_DUI")
    }

    @Test
    fun `jiang dui is a standalone three fan hand`() {
        val concealed = listOf(
            MahjongTile.M2, MahjongTile.M2, MahjongTile.M2,
            MahjongTile.M5, MahjongTile.M5, MahjongTile.M5,
            MahjongTile.P2, MahjongTile.P2, MahjongTile.P2,
            MahjongTile.P5, MahjongTile.P5, MahjongTile.P5,
            MahjongTile.M8
        )

        val result = engine.evaluateFan(concealed, emptyList(), MahjongTile.M8, "DISCARD", emptyList(), false)

        assertTrue(result.valid)
        assertEquals(3, result.totalFan)
        assertContains(result.fans.map { it.name }, "JIANG_DUI")
        assertFalse(result.fans.any { it.name == "DUI_DUI_HU" })
    }

    @Test
    fun `sichuan score units use fan minus one exponent without five fan cap`() {
        assertEquals(1, engine.scoreUnit(1))
        assertEquals(2, engine.scoreUnit(2))
        assertEquals(4, engine.scoreUnit(3))
        assertEquals(16, engine.scoreUnit(5))
        assertEquals(32, engine.scoreUnit(6))
        assertEquals(32, engine.bestReadyUnit(listOf(GbTingCandidate("W1", 6))))
    }

    @Test
    fun `full flush deluxe dragon seven pairs keeps its full fan total`() {
        val concealed = listOf(
            MahjongTile.M1, MahjongTile.M1, MahjongTile.M1, MahjongTile.M1,
            MahjongTile.M2, MahjongTile.M2, MahjongTile.M2, MahjongTile.M2,
            MahjongTile.M3, MahjongTile.M3, MahjongTile.M3, MahjongTile.M3,
            MahjongTile.M4
        )

        val result = engine.evaluateFan(concealed, emptyList(), MahjongTile.M4, "DISCARD", emptyList(), false)

        assertTrue(result.valid)
        assertEquals(7, result.totalFan)
        assertContains(result.fans.map { it.name }, "HAO_HUA_LONG_QI_DUI")
        assertContains(result.fans.map { it.name }, "QING_YI_SE")
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
        assertEquals(24, deltas.getValue("SOUTH"))
        assertEquals(12, deltas.getValue("WEST"))
        assertEquals(12, deltas.getValue("NORTH"))
    }

    /**
     * Builds a 14-tile seven-pair hand from six fully formed pair ranks plus a
     * seventh pair completed by [pairTile].
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
}
