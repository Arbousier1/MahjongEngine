package top.ellan.mahjong.table.core

import top.ellan.mahjong.model.SeatWind
import top.ellan.mahjong.render.display.DisplayClickAction
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MahjongTableManagerTest {
    @Test
    fun `same display action matches toggle ready duplicates`() {
        val left = DisplayClickAction.toggleReady("TABLE01", SeatWind.EAST)
        val right = DisplayClickAction.toggleReady("TABLE01", SeatWind.EAST)

        assertTrue(MahjongTableManager.sameDisplayAction(left, right))
    }

    @Test
    fun `different display action does not match different ready seats`() {
        val left = DisplayClickAction.toggleReady("TABLE01", SeatWind.EAST)
        val right = DisplayClickAction.toggleReady("TABLE01", SeatWind.SOUTH)

        assertFalse(MahjongTableManager.sameDisplayAction(left, right))
    }

    @Test
    fun `same seat join action is treated as an existing binding`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000021")
        val session = mock(MahjongTableSession::class.java)
        val action = DisplayClickAction.joinSeat("TABLE01", SeatWind.SOUTH)

        `when`(session.id()).thenReturn("TABLE01")
        `when`(session.seatOf(playerId)).thenReturn(SeatWind.SOUTH)

        assertTrue(MahjongTableManager.isSameSeatJoin(session, playerId, action))
    }

    @Test
    fun `different seat join action is not treated as an existing binding`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000022")
        val session = mock(MahjongTableSession::class.java)
        val action = DisplayClickAction.joinSeat("TABLE01", SeatWind.WEST)

        `when`(session.id()).thenReturn("TABLE01")
        `when`(session.seatOf(playerId)).thenReturn(SeatWind.NORTH)

        assertFalse(MahjongTableManager.isSameSeatJoin(session, playerId, action))
    }
}

