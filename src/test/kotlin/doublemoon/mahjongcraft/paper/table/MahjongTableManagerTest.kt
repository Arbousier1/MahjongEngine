package doublemoon.mahjongcraft.paper.table.core

import doublemoon.mahjongcraft.paper.model.SeatWind
import doublemoon.mahjongcraft.paper.render.display.DisplayClickAction
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MahjongTableManagerTest {
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
