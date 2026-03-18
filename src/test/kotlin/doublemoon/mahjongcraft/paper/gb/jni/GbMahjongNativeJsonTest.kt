package doublemoon.mahjongcraft.paper.gb.jni

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GbMahjongNativeJsonTest {
    @Test
    fun `fan request encodes expected fields`() {
        val request = GbFanRequest(
            handTiles = listOf("W1", "W1", "W1", "T2", "T3", "T4", "B5", "B5", "B5", "F1", "F1", "J1", "J1"),
            melds = listOf(
                GbMeldInput(
                    type = "PUNG",
                    tiles = listOf("W1", "W1", "W1"),
                    claimedTile = "W1",
                    fromSeat = "LEFT",
                    open = true
                )
            ),
            winningTile = "J1",
            winType = "DISCARD",
            seatWind = "EAST",
            roundWind = "EAST",
            flowerTiles = listOf("a", "h"),
            flags = listOf("LAST_TILE")
        )

        val encoded = GbMahjongNativeJson.encodeFanRequest(request)

        assert(encoded.contains("\"ruleProfile\":\"GB_MAHJONG\""))
        assert(encoded.contains("\"winningTile\":\"J1\""))
        assert(encoded.contains("\"winType\":\"DISCARD\""))
        assert(encoded.contains("\"type\":\"PUNG\""))
        assert(encoded.contains("\"flowerTiles\":[\"a\",\"h\"]"))
    }

    @Test
    fun `fan response decodes entries and total fan`() {
        val response = GbMahjongNativeJson.decodeFanResponse(
            """
            {
              "valid": true,
              "totalFan": 8,
              "fans": [
                {"name": "Mixed Triple Chow", "fan": 8, "count": 1}
              ]
            }
            """.trimIndent()
        )

        assertEquals(true, response.valid)
        assertEquals(8, response.totalFan)
        assertEquals("Mixed Triple Chow", response.fans.single().name)
    }

    @Test
    fun `ting response decodes waits`() {
        val response = GbMahjongNativeJson.decodeTingResponse(
            """
            {
              "valid": true,
              "waits": [
                {"tile": "W3", "totalFan": 8, "fans": [{"name": "Pure Straight", "fan": 8}]},
                {"tile": "W6", "totalFan": 6, "fans": []}
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, response.waits.size)
        assertEquals("W3", response.waits.first().tile)
        assertEquals(8, response.waits.first().totalFan)
        assertFalse(response.waits.first().fans.isEmpty())
    }

    @Test
    fun `win request encodes seat deltas context`() {
        val request = GbWinRequest(
            handTiles = listOf("W1", "W2", "W3"),
            melds = listOf(GbMeldInput(type = "ADDED_KONG", tiles = listOf("W1", "W1", "W1", "W1"), claimedTile = "W1", fromSeat = "LEFT")),
            winningTile = "W4",
            winType = "DISCARD",
            winnerSeat = "EAST",
            discarderSeat = "SOUTH",
            seatWind = "EAST",
            roundWind = "EAST",
            seatPoints = listOf(GbSeatPointsInput("EAST", 25000), GbSeatPointsInput("SOUTH", 25000)),
            flags = listOf("ROBBING_KONG")
        )

        val encoded = GbMahjongNativeJson.encodeWinRequest(request)

        assertTrue(encoded.contains("\"winnerSeat\":\"EAST\""))
        assertTrue(encoded.contains("\"discarderSeat\":\"SOUTH\""))
        assertTrue(encoded.contains("\"seatPoints\""))
        assertTrue(encoded.contains("\"type\":\"ADDED_KONG\""))
        assertTrue(encoded.contains("\"ROBBING_KONG\""))
    }

    @Test
    fun `win response decodes score deltas`() {
        val response = GbMahjongNativeJson.decodeWinResponse(
            """
            {
              "valid": true,
              "title": "RON",
              "totalFan": 8,
              "fans": [{"name": "Mixed Triple Chow", "fan": 8, "count": 1}],
              "scoreDeltas": [
                {"seat": "EAST", "delta": 24},
                {"seat": "SOUTH", "delta": -24}
              ]
            }
            """.trimIndent()
        )

        assertTrue(response.valid)
        assertEquals("RON", response.title)
        assertEquals(2, response.scoreDeltas.size)
        assertEquals("EAST", response.scoreDeltas.first().seat)
        assertEquals(24, response.scoreDeltas.first().delta)
    }
}
