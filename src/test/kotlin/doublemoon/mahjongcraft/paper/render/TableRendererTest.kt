package doublemoon.mahjongcraft.paper.render

import doublemoon.mahjongcraft.paper.model.SeatWind
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
        assertEquals(SeatWind.EAST, WallLayout.wallSeat(16))
        assertEquals(SeatWind.SOUTH, WallLayout.wallSeat(17))
        assertEquals(SeatWind.WEST, WallLayout.wallSeat(34))
        assertEquals(SeatWind.NORTH, WallLayout.wallSeat(51))
        assertEquals(SeatWind.EAST, WallLayout.wallSeat(68))
        assertEquals(SeatWind.SOUTH, WallLayout.wallSeat(85))
        assertEquals(SeatWind.WEST, WallLayout.wallSeat(102))
        assertEquals(SeatWind.NORTH, WallLayout.wallSeat(119))
        assertEquals(SeatWind.NORTH, WallLayout.wallSeat(135))
    }

    @Test
    fun `wall indexing computes expected column and layer`() {
        assertEquals(0, WallLayout.wallColumn(0))
        assertEquals(16, WallLayout.wallColumn(16))
        assertEquals(0, WallLayout.wallColumn(17))
        assertEquals(16, WallLayout.wallColumn(135))
        assertEquals(0, WallLayout.wallLayer(0))
        assertEquals(0, WallLayout.wallLayer(67))
        assertEquals(1, WallLayout.wallLayer(68))
        assertEquals(1, WallLayout.wallLayer(135))
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
}
