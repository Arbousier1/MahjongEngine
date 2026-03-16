package doublemoon.mahjongcraft.paper.render

import doublemoon.mahjongcraft.paper.model.MahjongTile
import doublemoon.mahjongcraft.paper.model.SeatWind
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick
import doublemoon.mahjongcraft.paper.table.MahjongTableSession
import java.util.EnumMap
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableRendererTest {
    private val tileWidth = 0.1125
    private val tileHeight = 0.15
    private val tilePadding = 0.01

    @Test
    fun `wall indexing stays within four seat winds`() {
        assertEquals(SeatWind.EAST, WallLayout.wallSeat(0))
        assertEquals(SeatWind.EAST, WallLayout.wallSeat(33))
        assertEquals(SeatWind.SOUTH, WallLayout.wallSeat(34))
        assertEquals(SeatWind.WEST, WallLayout.wallSeat(68))
        assertEquals(SeatWind.NORTH, WallLayout.wallSeat(102))
        assertEquals(SeatWind.NORTH, WallLayout.wallSeat(135))
    }

    @Test
    fun `wall indexing computes expected column and layer`() {
        assertEquals(0, WallLayout.wallColumn(0))
        assertEquals(0, WallLayout.wallColumn(1))
        assertEquals(8, WallLayout.wallColumn(16))
        assertEquals(8, WallLayout.wallColumn(17))
        assertEquals(16, WallLayout.wallColumn(32))
        assertEquals(16, WallLayout.wallColumn(33))
        assertEquals(0, WallLayout.wallColumn(34))
        assertEquals(16, WallLayout.wallColumn(135))
        assertEquals(1, WallLayout.wallLayer(0))
        assertEquals(0, WallLayout.wallLayer(1))
        assertEquals(1, WallLayout.wallLayer(34))
        assertEquals(0, WallLayout.wallLayer(135))
    }

    @Test
    fun `riichi discard uses sideways footprint and yaw`() {
        assertTrue(DiscardLayout.discardFootprint(tileWidth, tileHeight, true) > DiscardLayout.discardFootprint(tileWidth, tileHeight, false))
        assertEquals(-180.0f, DiscardLayout.discardYaw(SeatWind.EAST, true))
        assertEquals(-90.0f, DiscardLayout.discardYaw(SeatWind.SOUTH, true))
        assertEquals(0.0f, DiscardLayout.discardYaw(SeatWind.WEST, true))
        assertEquals(90.0f, DiscardLayout.discardYaw(SeatWind.NORTH, true))
    }

    @Test
    fun `discard row footprint accounts for sideways riichi tile`() {
        val plainRow = DiscardLayout.rowFootprint(tileWidth, tileHeight, tilePadding, 0, 6, -1)
        val riichiRow = DiscardLayout.rowFootprint(tileWidth, tileHeight, tilePadding, 0, 6, 2)
        assertTrue(riichiRow > plainRow)
    }

    @Test
    fun `async layout plan precomputes wall and hand placements`() {
        val snapshot = startedSnapshot()

        val plan = TableRenderLayout.precompute(snapshot)

        assertEquals(70 + 14 - 2, plan.wallTiles().size)
        assertEquals(2, plan.doraTiles().size)
        assertEquals(13, plan.seat(SeatWind.EAST).publicHandPoints().size)
        assertEquals(13, plan.seat(SeatWind.EAST).privateHandPoints().size)
        assertEquals(2, plan.seat(SeatWind.EAST).stickPlacements().size)
    }

    @Test
    fun `waiting snapshot skips live wall and dora layout`() {
        val snapshot = startedSnapshot().let {
            MahjongTableSession.RenderSnapshot(
                it.version(),
                it.cancellationNonce(),
                it.worldName(),
                it.centerX(),
                it.centerY(),
                it.centerZ(),
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                SeatWind.EAST,
                SeatWind.EAST,
                SeatWind.EAST,
                it.waitingDisplaySummary(),
                it.ruleDisplaySummary(),
                "waiting",
                null,
                null,
                emptyList(),
                it.seats()
            )
        }

        val plan = TableRenderLayout.precompute(snapshot)

        assertTrue(plan.wallTiles().isEmpty())
        assertTrue(plan.doraTiles().isEmpty())
    }

    private fun startedSnapshot(): MahjongTableSession.RenderSnapshot {
        val seats = EnumMap<SeatWind, MahjongTableSession.SeatRenderSnapshot>(SeatWind::class.java)
        val eastId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        seats[SeatWind.EAST] = seatSnapshot(
            wind = SeatWind.EAST,
            playerId = eastId,
            hand = List(13) { MahjongTile.M1 },
            discards = listOf(MahjongTile.EAST, MahjongTile.SOUTH),
            riichi = true,
            riichiDiscardIndex = 1,
            scoringSticks = listOf(ScoringStick.P1000),
            cornerSticks = listOf(ScoringStick.P100)
        )
        seats[SeatWind.SOUTH] = seatSnapshot(SeatWind.SOUTH, UUID.fromString("00000000-0000-0000-0000-000000000002"))
        seats[SeatWind.WEST] = seatSnapshot(SeatWind.WEST, UUID.fromString("00000000-0000-0000-0000-000000000003"))
        seats[SeatWind.NORTH] = seatSnapshot(SeatWind.NORTH, UUID.fromString("00000000-0000-0000-0000-000000000004"))
        return MahjongTableSession.RenderSnapshot(
            1L,
            0L,
            "world",
            0.0,
            64.0,
            0.0,
            true,
            false,
            70,
            0,
            6,
            0,
            1,
            SeatWind.EAST,
            SeatWind.SOUTH,
            SeatWind.EAST,
            "waiting",
            "rules",
            "center",
            null,
            null,
            listOf(MahjongTile.M1, MahjongTile.P1),
            seats
        )
    }

    private fun seatSnapshot(
        wind: SeatWind,
        playerId: UUID,
        hand: List<MahjongTile> = emptyList(),
        discards: List<MahjongTile> = emptyList(),
        riichi: Boolean = false,
        riichiDiscardIndex: Int = -1,
        scoringSticks: List<ScoringStick> = emptyList(),
        cornerSticks: List<ScoringStick> = emptyList()
    ) = MahjongTableSession.SeatRenderSnapshot(
        wind,
        playerId,
        wind.name.lowercase(),
        wind.name,
        25000,
        riichi,
        true,
        false,
        true,
        "",
        -1,
        riichiDiscardIndex,
        scoringSticks.size + cornerSticks.size,
        emptyList(),
        hand,
        discards,
        emptyList(),
        scoringSticks,
        cornerSticks
    )
}
