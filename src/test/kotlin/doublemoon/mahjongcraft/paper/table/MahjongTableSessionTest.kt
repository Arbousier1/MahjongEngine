package doublemoon.mahjongcraft.paper.table.core

import doublemoon.mahjongcraft.paper.bootstrap.MahjongPaperPlugin
import doublemoon.mahjongcraft.paper.model.SeatWind
import doublemoon.mahjongcraft.paper.riichi.RiichiPlayerState
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine
import doublemoon.mahjongcraft.paper.table.core.round.RiichiTableRoundController
import org.bukkit.Location
import org.bukkit.entity.Player
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MahjongTableSessionTest {
    @Test
    fun `player removal after finished round ignores stale engine seats`() {
        val plugin = mock(MahjongPaperPlugin::class.java)
        val session = MahjongTableSession(plugin, "TABLE01", Location(null, 0.0, 64.0, 0.0), false)
        val eastId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val southId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val westId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val northId = UUID.fromString("00000000-0000-0000-0000-000000000004")

        session.addPlayer(mockPlayer(eastId), SeatWind.EAST)
        session.addPlayer(mockPlayer(southId), SeatWind.SOUTH)
        session.addPlayer(mockPlayer(westId), SeatWind.WEST)
        session.addPlayer(mockPlayer(northId), SeatWind.NORTH)
        attachEngine(
            session,
            started = false,
            seatIds = listOf(eastId, southId, westId, northId)
        )

        removeParticipant(session, eastId)
        assertFalse(session.contains(eastId))
        assertNull(session.playerAt(SeatWind.EAST))
        assertNull(session.seatOf(eastId))
        assertEquals(3, session.size())
    }

    @Test
    fun `active round still reads seat occupants from engine`() {
        val plugin = mock(MahjongPaperPlugin::class.java)
        val session = MahjongTableSession(plugin, "TABLE02", Location(null, 0.0, 64.0, 0.0), false)
        val eastId = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val southId = UUID.fromString("00000000-0000-0000-0000-000000000012")

        session.addPlayer(mockPlayer(eastId), SeatWind.EAST)
        session.addPlayer(mockPlayer(southId), SeatWind.SOUTH)
        attachEngine(
            session,
            started = true,
            seatIds = listOf(southId, eastId)
        )

        assertEquals(southId, session.playerAt(SeatWind.EAST))
        assertEquals(eastId, session.playerAt(SeatWind.SOUTH))
    }

    @Test
    fun `empty seat accessors stay null safe for render snapshots`() {
        val plugin = mock(MahjongPaperPlugin::class.java)
        val session = MahjongTableSession(plugin, "TABLE03", Location(null, 0.0, 64.0, 0.0), false)

        assertEquals(0, session.points(null))
        assertFalse(session.isRiichi(null))
        assertEquals(emptyList(), session.hand(null))
        assertEquals(emptyList(), session.discards(null))
        assertEquals(-1, session.riichiDiscardIndex(null))
        assertEquals(emptyList(), session.fuuro(null))
        assertEquals(emptyList(), session.scoringSticks(null))
    }

    @Test
    fun `gb preset switches table variant to gb`() {
        val plugin = mock(MahjongPaperPlugin::class.java)
        val session = MahjongTableSession(plugin, "TABLE04", Location(null, 0.0, 64.0, 0.0), false)

        assertTrue(session.applyRulePreset("GB"))
        assertEquals(MahjongVariant.GB, session.currentVariant())
    }

    @Test
    fun `gb helpers stay inactive before a gb round is created`() {
        val plugin = mock(MahjongPaperPlugin::class.java)
        val session = MahjongTableSession(plugin, "TABLE05", Location(null, 0.0, 64.0, 0.0), false)

        session.applyRulePreset("GB")

        assertFalse(session.hasRoundController())
        assertFalse(session.gbCanWinByTsumo(UUID.fromString("00000000-0000-0000-0000-000000000005")))
        assertFalse(session.gbTingOptions(UUID.fromString("00000000-0000-0000-0000-000000000005")).valid)
    }

    private fun attachEngine(session: MahjongTableSession, started: Boolean, seatIds: List<UUID>) {
        val engine = mock(RiichiRoundEngine::class.java)
        `when`(engine.started).thenReturn(started)
        `when`(engine.seats).thenReturn(seatIds.map(::mockSeatPlayer).toMutableList())
        val controllerField = MahjongTableSession::class.java.getDeclaredField("roundController")
        controllerField.isAccessible = true
        controllerField.set(session, RiichiTableRoundController(engine))
    }

    private fun mockSeatPlayer(playerId: UUID): RiichiPlayerState {
        return RiichiPlayerState(playerId.toString(), playerId.toString())
    }

    private fun removeParticipant(session: MahjongTableSession, playerId: UUID) {
        val participantsField = MahjongTableSession::class.java.getDeclaredField("participants")
        participantsField.isAccessible = true
        val participants = participantsField.get(session)
        val removePlayer = participants.javaClass.getDeclaredMethod("removePlayer", UUID::class.java)
        removePlayer.isAccessible = true
        removePlayer.invoke(participants, playerId)
    }

    private fun mockPlayer(playerId: UUID): Player {
        val player = mock(Player::class.java)
        `when`(player.uniqueId).thenReturn(playerId)
        return player
    }
}
