package top.ellan.mahjong.table.runtime

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import top.ellan.mahjong.model.MahjongTile
import top.ellan.mahjong.model.SeatWind
import top.ellan.mahjong.table.core.MahjongTableSession
import top.ellan.mahjong.table.core.MahjongVariant
import top.ellan.mahjong.runtime.PluginTask
import top.ellan.mahjong.runtime.ServerScheduler
import org.bukkit.Location
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.UUID
import kotlin.test.Test

class BotActionSchedulerTest {
    @Test
    fun `non riichi variant uses gb style bot scheduling`() {
        val session = mock(MahjongTableSession::class.java)
        val plugin = mock(MahjongPaperPlugin::class.java)
        val scheduler = mock(ServerScheduler::class.java)
        val task = mock(PluginTask::class.java)
        val center = mock(Location::class.java)
        val botId = UUID.fromString("00000000-0000-0000-0000-00000000b001")

        `when`(session.currentVariant()).thenReturn(MahjongVariant.GB)
        `when`(session.isStarted()).thenReturn(true)
        `when`(session.players()).thenReturn(listOf(botId))
        `when`(session.isBot(botId)).thenReturn(true)
        `when`(session.availableReactions(botId)).thenReturn(null)
        `when`(session.currentSeat()).thenReturn(SeatWind.EAST)
        `when`(session.playerAt(SeatWind.EAST)).thenReturn(botId)
        `when`(session.plugin()).thenReturn(plugin)
        `when`(plugin.scheduler()).thenReturn(scheduler)
        `when`(session.center()).thenReturn(center)
        `when`(scheduler.runRegionDelayed(any(Location::class.java), any(Runnable::class.java), eq(20L))).thenReturn(task)

        BotActionScheduler.schedule(session)

        verify(session).setBotTask(task)
        verify(session, never()).engine()
    }

    @Test
    fun `gb bot turn falls back to selectable discard index`() {
        val session = mock(MahjongTableSession::class.java)
        val plugin = mock(MahjongPaperPlugin::class.java)
        val scheduler = mock(ServerScheduler::class.java)
        val task = mock(PluginTask::class.java)
        val center = mock(Location::class.java)
        val botId = UUID.fromString("00000000-0000-0000-0000-00000000b002")

        `when`(session.currentVariant()).thenReturn(MahjongVariant.GB)
        `when`(session.isStarted()).thenReturn(true)
        `when`(session.players()).thenReturn(listOf(botId))
        `when`(session.isBot(botId)).thenReturn(true)
        `when`(session.availableReactions(botId)).thenReturn(null)
        `when`(session.currentSeat()).thenReturn(SeatWind.EAST)
        `when`(session.playerAt(SeatWind.EAST)).thenReturn(botId)
        `when`(session.plugin()).thenReturn(plugin)
        `when`(plugin.scheduler()).thenReturn(scheduler)
        `when`(session.center()).thenReturn(center)
        `when`(scheduler.runRegionDelayed(any(Location::class.java), any(Runnable::class.java), eq(20L))).thenReturn(task)
        `when`(session.hasPendingReaction()).thenReturn(false)
        `when`(session.gbCanWinByTsumo(botId)).thenReturn(false)
        `when`(session.gbSuggestedKanTile(botId)).thenReturn(null)
        `when`(session.hand(botId)).thenReturn(listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3))
        `when`(session.gbSuggestedDiscardIndex(botId)).thenReturn(0)
        `when`(session.canSelectHandTile(botId, 0)).thenReturn(false)
        `when`(session.canSelectHandTile(botId, 1)).thenReturn(false)
        `when`(session.canSelectHandTile(botId, 2)).thenReturn(true)
        `when`(session.discard(botId, 2)).thenReturn(true)

        BotActionScheduler.schedule(session)

        val runnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        verify(scheduler).runRegionDelayed(any(Location::class.java), runnableCaptor.capture(), eq(20L))
        runnableCaptor.value.run()

        verify(session).discard(botId, 2)
        verify(session, never()).discard(botId, 0)
    }
}
