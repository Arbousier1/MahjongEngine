package top.ellan.mahjong.gb.runtime

import top.ellan.mahjong.gb.jni.GbFanEntry
import top.ellan.mahjong.gb.jni.GbScoreDelta
import top.ellan.mahjong.gb.jni.GbTingCandidate
import top.ellan.mahjong.gb.jni.GbTingRequest
import top.ellan.mahjong.gb.jni.GbTingResponse
import top.ellan.mahjong.gb.jni.GbWinRequest
import top.ellan.mahjong.gb.jni.GbWinResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GbNativeRulesGatewayTest {
    @Test
    fun `evaluate ting uses cache for identical request`() {
        var nativeCalls = 0
        val gateway = object : GbNativeRulesGateway(16, 16) {
            override fun isAvailable(): Boolean = true

            override fun evaluateTingNative(request: GbTingRequest): GbTingResponse {
                nativeCalls++
                return GbTingResponse(
                    valid = true,
                    waits = listOf(GbTingCandidate("M1", 8, listOf(GbFanEntry("TEST", 8)))),
                    error = null
                )
            }
        }
        val request = tingRequest("M1")

        gateway.evaluateTing(request)
        gateway.evaluateTing(request)

        assertEquals(1, nativeCalls)
    }

    @Test
    fun `evaluate win uses cache for identical request`() {
        var nativeCalls = 0
        val gateway = object : GbNativeRulesGateway(16, 16) {
            override fun isAvailable(): Boolean = true

            override fun evaluateWinNative(request: GbWinRequest): GbWinResponse {
                nativeCalls++
                return GbWinResponse(
                    valid = true,
                    title = "WIN",
                    totalFan = 8,
                    fans = listOf(GbFanEntry("TEST", 8)),
                    scoreDeltas = listOf(GbScoreDelta("EAST", 8)),
                    error = null
                )
            }
        }
        val request = winRequest("M1")

        gateway.evaluateWin(request)
        gateway.evaluateWin(request)

        assertEquals(1, nativeCalls)
    }

    @Test
    fun `ting cache evicts oldest entry with lru policy`() {
        var nativeCalls = 0
        val gateway = object : GbNativeRulesGateway(2, 2) {
            override fun isAvailable(): Boolean = true

            override fun evaluateTingNative(request: GbTingRequest): GbTingResponse {
                nativeCalls++
                return GbTingResponse(
                    valid = true,
                    waits = listOf(GbTingCandidate(request.handTiles.first(), 8, listOf(GbFanEntry("TEST", 8)))),
                    error = null
                )
            }
        }
        val first = tingRequest("M1")
        val second = tingRequest("M2")
        val third = tingRequest("M3")

        gateway.evaluateTing(first)
        gateway.evaluateTing(second)
        gateway.evaluateTing(third)
        gateway.evaluateTing(first)

        assertEquals(4, nativeCalls)
    }

    @Test
    fun `ting failure response uses sanitized message`() {
        val gateway = object : GbNativeRulesGateway(16, 16) {
            override fun isAvailable(): Boolean = true

            override fun evaluateTingNative(request: GbTingRequest): GbTingResponse {
                throw IllegalStateException("native error detail should stay internal")
            }
        }

        val response = gateway.evaluateTing(tingRequest("M1"))

        assertFalse(response.valid)
        assertEquals("GB Mahjong native ting evaluation failed.", response.error)
        assertTrue(response.error?.contains("internal") == false)
    }

    @Test
    fun `win failure response uses sanitized message`() {
        val gateway = object : GbNativeRulesGateway(16, 16) {
            override fun isAvailable(): Boolean = true

            override fun evaluateWinNative(request: GbWinRequest): GbWinResponse {
                throw IllegalStateException("native win details should stay internal")
            }
        }

        val response = gateway.evaluateWin(winRequest("M1"))

        assertFalse(response.valid)
        assertEquals("GB Mahjong native win evaluation failed.", response.error)
        assertTrue(response.error?.contains("internal") == false)
    }

    private fun tingRequest(tile: String): GbTingRequest = GbTingRequest(
        handTiles = listOf(tile, "M2", "M3", "M4", "M5", "P1", "P2", "P3", "S1", "S2", "S3", "EAST", "SOUTH"),
        melds = emptyList(),
        seatWind = "EAST",
        roundWind = "EAST",
        flowerTiles = emptyList(),
        flags = emptyList()
    )

    private fun winRequest(tile: String): GbWinRequest = GbWinRequest(
        handTiles = listOf(tile, "M2", "M3", "M4", "M5", "P1", "P2", "P3", "S1", "S2", "S3", "EAST", "SOUTH"),
        melds = emptyList(),
        winningTile = tile,
        winType = "SELF_DRAW",
        winnerSeat = "EAST",
        discarderSeat = null,
        seatWind = "EAST",
        roundWind = "EAST",
        seatPoints = emptyList(),
        flowerTiles = emptyList(),
        flags = emptyList()
    )
}
