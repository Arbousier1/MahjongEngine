package top.ellan.mahjong.table.core

import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHandSelectionCoordinatorTest {
    private val playerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000701")

    @Test
    fun `first valid click selects hand tile without discarding`() {
        val session = mock(TableSessionMutator::class.java)
        val coordinator = SessionHandSelectionCoordinator(session)

        `when`(session.canSelectHandTileInternal(playerId, 3)).thenReturn(true)

        assertTrue(coordinator.clickHandTile(playerId, 3, false))

        assertEquals(3, coordinator.selectedHandTileIndex(playerId))
        verify(session).refreshSelectedHandTileViewInternal(playerId)
        verify(session, never()).discard(playerId, 3)
    }

    @Test
    fun `second click on selected hand tile confirms discard`() {
        val session = mock(TableSessionMutator::class.java)
        val coordinator = SessionHandSelectionCoordinator(session)

        `when`(session.canSelectHandTileInternal(playerId, 5)).thenReturn(true)
        `when`(session.discard(playerId, 5)).thenReturn(true)

        assertTrue(coordinator.clickHandTile(playerId, 5, false))
        assertTrue(coordinator.clickHandTile(playerId, 5, false))

        verify(session, times(1)).discard(playerId, 5)
    }

    @Test
    fun `sneak click on selected hand tile cancels selection`() {
        val session = mock(TableSessionMutator::class.java)
        val coordinator = SessionHandSelectionCoordinator(session)

        `when`(session.canSelectHandTileInternal(playerId, 2)).thenReturn(true)

        assertTrue(coordinator.clickHandTile(playerId, 2, false))
        assertTrue(coordinator.clickHandTile(playerId, 2, true))

        assertEquals(-1, coordinator.selectedHandTileIndex(playerId))
        verify(session, times(2)).refreshSelectedHandTileViewInternal(playerId)
        verify(session, never()).discard(playerId, 2)
    }

    @Test
    fun `invalid click is rejected without changing selection`() {
        val session = mock(TableSessionMutator::class.java)
        val coordinator = SessionHandSelectionCoordinator(session)

        `when`(session.canSelectHandTileInternal(playerId, 1)).thenReturn(false)

        assertFalse(coordinator.clickHandTile(playerId, 1, false))

        assertEquals(-1, coordinator.selectedHandTileIndex(playerId))
        verify(session, never()).refreshSelectedHandTileViewInternal(playerId)
        verify(session, never()).discard(playerId, 1)
    }

    @Test
    fun `clicking a different valid hand tile moves selection`() {
        val session = mock(TableSessionMutator::class.java)
        val coordinator = SessionHandSelectionCoordinator(session)

        `when`(session.canSelectHandTileInternal(playerId, 1)).thenReturn(true)
        `when`(session.canSelectHandTileInternal(playerId, 4)).thenReturn(true)

        assertTrue(coordinator.clickHandTile(playerId, 1, false))
        assertTrue(coordinator.clickHandTile(playerId, 4, false))

        assertEquals(4, coordinator.selectedHandTileIndex(playerId))
        verify(session, times(2)).refreshSelectedHandTileViewInternal(playerId)
        verify(session, never()).discard(playerId, 1)
        verify(session, never()).discard(playerId, 4)
    }
}
