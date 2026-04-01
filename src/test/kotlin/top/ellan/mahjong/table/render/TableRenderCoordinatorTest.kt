package top.ellan.mahjong.table.render

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import top.ellan.mahjong.metrics.InMemoryMetricsCollector
import top.ellan.mahjong.runtime.PluginTask
import top.ellan.mahjong.runtime.ServerScheduler
import top.ellan.mahjong.table.core.MahjongTableSession
import org.bukkit.Location
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableRenderCoordinatorTest {
    @Test
    fun `render coalesces duplicate requests and records request metrics`() {
        val session = mock(MahjongTableSession::class.java)
        val plugin = mock(MahjongPaperPlugin::class.java)
        val scheduler = mock(ServerScheduler::class.java)
        val center = mock(Location::class.java)
        val metrics = InMemoryMetricsCollector()

        `when`(session.plugin()).thenReturn(plugin)
        `when`(session.center()).thenReturn(center)
        `when`(plugin.scheduler()).thenReturn(scheduler)
        `when`(plugin.metrics()).thenReturn(metrics)
        `when`(scheduler.runRegion(any(Location::class.java), any(Runnable::class.java))).thenReturn(mock(PluginTask::class.java))

        val coordinator = TableRenderCoordinator(session)
        coordinator.render()
        coordinator.render()

        verify(session, times(2)).prepareRenderRequest()
        verify(scheduler, times(1)).runRegion(any(Location::class.java), any(Runnable::class.java))
        assertEquals(2L, metrics.counterValue("table.render.request.calls"))
        assertEquals(1L, metrics.counterValue("table.render.request.scheduled"))
        assertEquals(1L, metrics.counterValue("table.render.request.coalesced"))
        assertTrue(metrics.timerCount("table.render.request.nanos") >= 2L)
    }
}
