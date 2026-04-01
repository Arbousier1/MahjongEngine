package top.ellan.mahjong.gb.jni

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GbNativeWarmupServiceTest {
    @Test
    fun `warmup exits early when native bridge is unavailable`() {
        val bridge = mock(GbMahjongNativeBridge::class.java)
        `when`(bridge.isAvailable()).thenReturn(false)
        `when`(bridge.availabilityDetail()).thenReturn("native disabled")

        val report = GbNativeWarmupService.warmup(bridge)

        assertFalse(report.available())
        assertTrue(report.detail().contains("disabled"))
        verify(bridge, never()).libraryVersion()
        verify(bridge, never()).ping()
        verify(bridge, never()).evaluateFan(any(GbFanRequest::class.java))
        verify(bridge, never()).evaluateTing(any(GbTingRequest::class.java))
        verify(bridge, never()).evaluateWin(any(GbWinRequest::class.java))
    }

    @Test
    fun `warmup benchmarks first and warm calls when native bridge is available`() {
        val bridge = mock(GbMahjongNativeBridge::class.java)
        `when`(bridge.isAvailable()).thenReturn(true)
        `when`(bridge.availabilityDetail()).thenReturn("loaded")
        `when`(bridge.libraryVersion()).thenReturn("1.0.0")
        `when`(bridge.ping()).thenReturn("ready")
        `when`(bridge.evaluateFan(any(GbFanRequest::class.java))).thenReturn(
            GbFanResponse(true, 8, listOf(GbFanEntry("TEST", 8)), null)
        )
        `when`(bridge.evaluateTing(any(GbTingRequest::class.java))).thenReturn(
            GbTingResponse(true, listOf(GbTingCandidate("W1", 8, listOf(GbFanEntry("TEST", 8)))), null)
        )
        `when`(bridge.evaluateWin(any(GbWinRequest::class.java))).thenReturn(
            GbWinResponse(true, "WIN", 8, listOf(GbFanEntry("TEST", 8)), emptyList(), null)
        )

        val report = GbNativeWarmupService.warmup(bridge)

        assertTrue(report.available())
        assertTrue(report.fanSuccess())
        assertTrue(report.tingSuccess())
        assertTrue(report.winSuccess())
        assertTrue(report.totalNanos() > 0L)
        assertTrue(report.fanFirstNanos() >= 0L)
        assertTrue(report.fanWarmNanos() >= 0L)
        assertTrue(report.tingFirstNanos() >= 0L)
        assertTrue(report.tingWarmNanos() >= 0L)
        assertTrue(report.winFirstNanos() >= 0L)
        assertTrue(report.winWarmNanos() >= 0L)

        verify(bridge, times(1)).libraryVersion()
        verify(bridge, times(1)).ping()
        verify(bridge, times(2)).evaluateFan(any(GbFanRequest::class.java))
        verify(bridge, times(2)).evaluateTing(any(GbTingRequest::class.java))
        verify(bridge, times(2)).evaluateWin(any(GbWinRequest::class.java))
    }
}
