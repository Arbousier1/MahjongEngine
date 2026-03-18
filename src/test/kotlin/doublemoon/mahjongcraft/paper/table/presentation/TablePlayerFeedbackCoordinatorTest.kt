package doublemoon.mahjongcraft.paper.table.presentation

import doublemoon.mahjongcraft.paper.bootstrap.MahjongPaperPlugin
import doublemoon.mahjongcraft.paper.i18n.MessageService
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.UUID
import kotlin.test.Test

class TablePlayerFeedbackCoordinatorTest {
    @Test
    fun `settlement ui only opens for seated human players`() {
        val session = mock(MahjongTableSession::class.java)
        val plugin = mock(MahjongPaperPlugin::class.java)
        val messages = mock(MessageService::class.java)
        val coordinator = TablePlayerFeedbackCoordinator(session)

        val eastId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val botId = UUID.fromString("00000000-0000-0000-0000-000000000202")
        val southId = UUID.fromString("00000000-0000-0000-0000-000000000303")
        val spectatorId = UUID.fromString("00000000-0000-0000-0000-000000000404")

        val east = mock(Player::class.java)
        val south = mock(Player::class.java)
        val spectator = mock(Player::class.java)

        `when`(session.seatIds()).thenReturn(listOf(eastId, botId, southId))
        `when`(session.isBot(botId)).thenReturn(true)
        `when`(session.plugin()).thenReturn(plugin)
        `when`(plugin.messages()).thenReturn(messages)
        `when`(session.isRoundFinished()).thenReturn(false)

        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Player?> { Bukkit.getPlayer(eastId) }.thenReturn(east)
            bukkit.`when`<Player?> { Bukkit.getPlayer(botId) }.thenReturn(null)
            bukkit.`when`<Player?> { Bukkit.getPlayer(southId) }.thenReturn(south)
            bukkit.`when`<Player?> { Bukkit.getPlayer(spectatorId) }.thenReturn(spectator)

            invokePrivate(coordinator, "openSettlementForPlayers")
        }

        verify(session).openSettlementUi(east)
        verify(session).openSettlementUi(south)
        verify(session, never()).openSettlementUi(spectator)
        verify(messages).send(east, "table.round_finished_ready")
        verify(messages).send(south, "table.round_finished_ready")
        verify(messages, never()).send(spectator, "table.round_finished_ready")
    }

    private fun invokePrivate(target: Any, methodName: String) {
        val method = target.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(target)
    }
}
