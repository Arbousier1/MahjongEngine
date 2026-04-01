package top.ellan.mahjong.table.core

import top.ellan.mahjong.model.SeatWind
import top.ellan.mahjong.table.core.round.TableRoundController
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.UUID
import kotlin.test.Test

class SessionRoundFlowCoordinatorTest {
    @Test
    fun `startRound recreates controller when seat assignments changed`() {
        val session = mock(SessionState::class.java)
        val existing = mock(TableRoundController::class.java)
        val replacement = mock(TableRoundController::class.java)
        val coordinator = SessionRoundFlowCoordinator(session)

        val east = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val south = UUID.fromString("00000000-0000-0000-0000-000000000102")
        val west = UUID.fromString("00000000-0000-0000-0000-000000000103")
        val north = UUID.fromString("00000000-0000-0000-0000-000000000104")

        `when`(session.isRoundStartInProgress()).thenReturn(false)
        `when`(session.size()).thenReturn(4)
        `when`(session.playerAt(SeatWind.EAST)).thenReturn(east)
        `when`(session.playerAt(SeatWind.SOUTH)).thenReturn(south)
        `when`(session.playerAt(SeatWind.WEST)).thenReturn(west)
        `when`(session.playerAt(SeatWind.NORTH)).thenReturn(north)
        `when`(session.isReady(east)).thenReturn(true)
        `when`(session.isReady(south)).thenReturn(true)
        `when`(session.isReady(west)).thenReturn(true)
        `when`(session.isReady(north)).thenReturn(true)
        `when`(session.roundControllerInternal()).thenReturn(existing)
        `when`(session.currentVariant()).thenReturn(MahjongVariant.RIICHI)
        `when`(existing.gameFinished()).thenReturn(false)
        `when`(existing.variant()).thenReturn(MahjongVariant.RIICHI)
        `when`(session.seatAssignmentsMatchControllerInternal(existing)).thenReturn(false)
        `when`(session.createRoundControllerInternal()).thenReturn(replacement)
        `when`(session.shouldAnimateOpeningDiceInternal()).thenReturn(false)

        coordinator.startRound()

        verify(session).setRoundControllerInternal(replacement)
        verify(replacement).setPendingDiceRoll(any())
        verify(session).completeRoundStartInternal()
        verifyNoMoreInteractions(replacement)
    }
}


