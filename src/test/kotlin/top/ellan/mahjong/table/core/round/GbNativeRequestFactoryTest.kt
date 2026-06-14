package top.ellan.mahjong.table.core.round

import top.ellan.mahjong.model.MahjongTile
import top.ellan.mahjong.model.SeatWind
import kotlin.test.Test
import kotlin.test.assertEquals

class GbNativeRequestFactoryTest {
    private val factory = GbNativeRequestFactory()

    @Test
    fun `fan request encodes concealed hand melds flowers and flags`() {
        val request = factory.buildFanRequest(
            listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3, MahjongTile.P5),
            true,
            listOf(TestMeld("PUNG", listOf(MahjongTile.S1, MahjongTile.S1, MahjongTile.S1), MahjongTile.S1, SeatWind.SOUTH)),
            MahjongTile.P5,
            GbNativeRequestFactory.WIN_TYPE_SELF_DRAW,
            SeatWind.EAST,
            SeatWind.SOUTH,
            listOf(MahjongTile.PLUM),
            listOf("AFTER_KONG")
        )

        assertEquals("GB_MAHJONG", request.ruleProfile)
        assertEquals(listOf("W1", "W2", "W3"), request.handTiles)
        assertEquals("B5", request.winningTile)
        assertEquals("SELF_DRAW", request.winType)
        assertEquals("EAST", request.seatWind)
        assertEquals("SOUTH", request.roundWind)
        assertEquals(listOf("a"), request.flowerTiles)
        assertEquals(listOf("AFTER_KONG"), request.flags)
        assertEquals("PUNG", request.melds.single().type)
        assertEquals(listOf("T1", "T1", "T1"), request.melds.single().tiles)
        assertEquals("T1", request.melds.single().claimedTile)
        assertEquals("LEFT", request.melds.single().fromSeat)
        assertEquals(true, request.melds.single().open)
    }

    @Test
    fun `win request encodes discarder and seat points`() {
        val request = factory.buildWinRequest(
            listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3, MahjongTile.P5),
            false,
            emptyList(),
            MahjongTile.P5,
            "DISCARD",
            SeatWind.SOUTH,
            SeatWind.WEST,
            SeatWind.EAST,
            listOf(
                GbNativeRequestFactory.SeatPointsView(SeatWind.EAST, 25000),
                GbNativeRequestFactory.SeatPointsView(SeatWind.SOUTH, 26000)
            ),
            emptyList(),
            listOf("LAST_TILE")
        )

        assertEquals(listOf("W1", "W2", "W3", "B5"), request.handTiles)
        assertEquals("SOUTH", request.winnerSeat)
        assertEquals("WEST", request.discarderSeat)
        assertEquals("SOUTH", request.seatWind)
        assertEquals("EAST", request.roundWind)
        assertEquals(listOf("LAST_TILE"), request.flags)
        assertEquals("EAST", request.seatPoints[0].seat)
        assertEquals(25000, request.seatPoints[0].points)
        assertEquals("SOUTH", request.seatPoints[1].seat)
        assertEquals(26000, request.seatPoints[1].points)
    }

    @Test
    fun `ting request uses caller supplied concealed hand`() {
        val request = factory.buildTingRequest(
            listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3),
            listOf(TestMeld("CONCEALED_KONG", listOf(MahjongTile.P1, MahjongTile.P1, MahjongTile.P1, MahjongTile.P1), null, null, false)),
            SeatWind.NORTH,
            SeatWind.WEST,
            listOf(MahjongTile.WINTER)
        )

        assertEquals(listOf("W1", "W2", "W3"), request.handTiles)
        assertEquals("NORTH", request.seatWind)
        assertEquals("WEST", request.roundWind)
        assertEquals(listOf("h"), request.flowerTiles)
        assertEquals("CONCEALED_KONG", request.melds.single().type)
        assertEquals(null, request.melds.single().claimedTile)
        assertEquals(null, request.melds.single().fromSeat)
        assertEquals(false, request.melds.single().open)
    }

    private data class TestMeld(
        private val type: String,
        private val tiles: List<MahjongTile>,
        private val claimedTile: MahjongTile?,
        private val fromSeat: SeatWind?,
        private val open: Boolean = true
    ) : GbNativeRequestFactory.MeldStateView {
        override fun tiles(): List<MahjongTile> = tiles

        override fun claimedTile(): MahjongTile? = claimedTile

        override fun fromSeat(): SeatWind? = fromSeat

        override fun open(): Boolean = open

        override fun nativeType(): String = type
    }
}
