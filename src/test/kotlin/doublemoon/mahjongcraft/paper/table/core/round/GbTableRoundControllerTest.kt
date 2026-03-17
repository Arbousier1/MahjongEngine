package doublemoon.mahjongcraft.paper.table.core.round

import doublemoon.mahjongcraft.paper.gb.jni.GbFanEntry
import doublemoon.mahjongcraft.paper.gb.jni.GbFanRequest
import doublemoon.mahjongcraft.paper.gb.jni.GbFanResponse
import doublemoon.mahjongcraft.paper.gb.jni.GbScoreDelta
import doublemoon.mahjongcraft.paper.gb.jni.GbTingCandidate
import doublemoon.mahjongcraft.paper.gb.jni.GbTingRequest
import doublemoon.mahjongcraft.paper.gb.jni.GbTingResponse
import doublemoon.mahjongcraft.paper.gb.jni.GbWinRequest
import doublemoon.mahjongcraft.paper.gb.jni.GbWinResponse
import doublemoon.mahjongcraft.paper.gb.runtime.GbNativeRulesGateway
import doublemoon.mahjongcraft.paper.model.SeatWind
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse
import doublemoon.mahjongcraft.paper.riichi.ReactionType
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule
import java.util.EnumMap
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GbTableRoundControllerTest {
    @Test
    fun `gb round deals 14 tiles to dealer and 13 to others`() {
        val controller = controller()

        controller.startRound()

        assertTrue(controller.started())
        assertEquals(14, controller.hand(player(SeatWind.EAST)).size)
        assertEquals(13, controller.hand(player(SeatWind.SOUTH)).size)
        assertEquals(13, controller.hand(player(SeatWind.WEST)).size)
        assertEquals(13, controller.hand(player(SeatWind.NORTH)).size)
    }

    @Test
    fun `discard opens ron window and resumes after all skips`() {
        val controller = controller()
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)
        val west = player(SeatWind.WEST)
        val north = player(SeatWind.NORTH)

        assertTrue(controller.discard(east, 0))
        assertNotNull(controller.availableReactions(south))
        assertNotNull(controller.availableReactions(west))
        assertNotNull(controller.availableReactions(north))

        assertTrue(controller.react(south, ReactionResponse(ReactionType.SKIP, null)))
        assertTrue(controller.react(west, ReactionResponse(ReactionType.SKIP, null)))
        assertTrue(controller.react(north, ReactionResponse(ReactionType.SKIP, null)))

        assertFalse(controller.hasPendingReaction())
        assertEquals(SeatWind.SOUTH, controller.currentSeat())
        assertEquals(14, controller.hand(south).size)
    }

    @Test
    fun `pon claim keeps claimant on discard turn without drawing`() {
        val controller = controller()
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)
        val west = player(SeatWind.WEST)
        val north = player(SeatWind.NORTH)

        forceHand(controller, east, listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "P3", "P4", "P5"))
        forceHand(controller, south, listOf("M1", "M1", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "P1", "P2"))

        assertTrue(controller.discard(east, 0))
        assertTrue(controller.react(south, ReactionResponse(ReactionType.PON, null)))
        assertTrue(controller.react(west, ReactionResponse(ReactionType.SKIP, null)))
        assertTrue(controller.react(north, ReactionResponse(ReactionType.SKIP, null)))

        assertEquals(SeatWind.SOUTH, controller.currentSeat())
        assertEquals(11, controller.hand(south).size)
        assertEquals(1, controller.fuuro(south).size)
    }

    @Test
    fun `gb tsumo uses native fan validation`() {
        val controller = controller()
        controller.startRound()

        assertTrue(controller.canWinByTsumo(player(SeatWind.EAST)))
        assertTrue(controller.declareTsumo(player(SeatWind.EAST)))
        assertFalse(controller.started())
        assertEquals("TSUMO", controller.lastResolution()?.title)
        assertEquals(1, controller.lastResolution()?.yakuSettlements?.size)
        assertEquals(24, controller.lastResolution()?.scoreSettlement?.scoreList?.first { it.stringUUID == player(SeatWind.EAST).toString() }?.scoreChange)
    }

    @Test
    fun `gb kan suggestions include concealed and added kong tiles`() {
        val controller = controller()
        controller.startRound()
        val east = player(SeatWind.EAST)

        forceHand(controller, east, listOf("M1", "M1", "M1", "M1", "P3", "P3", "P3", "S1", "S2", "S3", "S4", "S5", "S6", "S7"))
        assertTrue(controller.suggestedKanTiles(east).contains("m1"))

        addPung(controller, east, "P3")
        forceHand(controller, east, listOf("M1", "M1", "M1", "M1", "P3", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9"))
        assertTrue(controller.suggestedKanTiles(east).contains("p3"))
    }

    @Test
    fun `multiple ron winners are settled together`() {
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                if (request.seatWind in setOf("SOUTH", "WEST")) {
                    GbFanResponse(true, 8, listOf(GbFanEntry("Mock Ron", 8, 1)), null)
                } else {
                    GbFanResponse(false, 0, emptyList(), "cannot win")
                }

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                GbTingResponse(true, emptyList(), null)

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(
                    true,
                    "RON",
                    8,
                    listOf(GbFanEntry("Mock Ron", 8, 1)),
                    listOf(GbScoreDelta(request.winnerSeat, 8), GbScoreDelta(request.discarderSeat ?: "EAST", -8)),
                    null
                )
        })
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)
        val west = player(SeatWind.WEST)
        val north = player(SeatWind.NORTH)

        assertTrue(controller.discard(east, 0))
        assertTrue(controller.react(south, ReactionResponse(ReactionType.RON, null)))
        assertTrue(controller.react(west, ReactionResponse(ReactionType.RON, null)))
        if (controller.availableReactions(north) != null) {
            assertTrue(controller.react(north, ReactionResponse(ReactionType.SKIP, null)))
        }

        assertFalse(controller.started())
        assertEquals(2, controller.lastResolution()?.yakuSettlements?.size)
        assertEquals(-16, controller.lastResolution()?.scoreSettlement?.scoreList?.first { it.stringUUID == east.toString() }?.scoreChange)
    }

    @Test
    fun `added kong exposes robbing kong window`() {
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                if ("ROBBING_KONG" in request.flags && request.seatWind == "SOUTH") {
                    GbFanResponse(true, 8, listOf(GbFanEntry("QIANGGANGHU", 8, 1)), null)
                } else {
                    GbFanResponse(false, 0, emptyList(), "cannot win")
                }

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                GbTingResponse(true, emptyList(), null)

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(
                    true,
                    "RON",
                    8,
                    listOf(GbFanEntry("QIANGGANGHU", 8, 1)),
                    listOf(GbScoreDelta(request.winnerSeat, 8), GbScoreDelta(request.discarderSeat ?: "EAST", -8)),
                    null
                )
        })
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)
        val west = player(SeatWind.WEST)
        val north = player(SeatWind.NORTH)

        addPung(controller, east, "P3")
        forceHand(controller, east, listOf("P3", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "S1", "S2", "S3", "S4"))

        assertTrue(controller.declareKan(east, "p3"))
        assertNotNull(controller.availableReactions(south))
        assertTrue(controller.react(south, ReactionResponse(ReactionType.RON, null)))
        if (controller.availableReactions(west) != null) {
            assertTrue(controller.react(west, ReactionResponse(ReactionType.SKIP, null)))
        }
        if (controller.availableReactions(north) != null) {
            assertTrue(controller.react(north, ReactionResponse(ReactionType.SKIP, null)))
        }

        assertFalse(controller.started())
        assertEquals("RON", controller.lastResolution()?.title)
    }

    private fun controller(gateway: GbNativeRulesGateway = defaultGateway()): GbTableRoundController {
        val seats = EnumMap<SeatWind, UUID>(SeatWind::class.java)
        val names = mutableMapOf<UUID, String>()
        SeatWind.values().forEach { wind ->
            val playerId = player(wind)
            seats[wind] = playerId
            names[playerId] = wind.name
        }
        return GbTableRoundController(MahjongRule(), seats, names, gateway)
    }

    private fun defaultGateway(): GbNativeRulesGateway = object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                GbFanResponse(true, 8, listOf(GbFanEntry("Mock Fan", 8, 1)), null)

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                GbTingResponse(true, listOf(GbTingCandidate("W1", 8, listOf(GbFanEntry("Mock Fan", 8, 1)))), null)

            override fun evaluateWin(request: GbWinRequest): GbWinResponse {
                val winnerDelta = 24
                val loserSeat = request.discarderSeat ?: "SOUTH"
                return GbWinResponse(
                    true,
                    if (request.winType == "SELF_DRAW") "TSUMO" else "RON",
                    8,
                    listOf(GbFanEntry("Mock Fan", 8, 1)),
                    listOf(GbScoreDelta(request.winnerSeat, winnerDelta), GbScoreDelta(loserSeat, -winnerDelta)),
                    null
                )
            }
        }

    private fun forceHand(controller: GbTableRoundController, playerId: UUID, tiles: List<String>) {
        val handsField = GbTableRoundController::class.java.getDeclaredField("hands")
        handsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val hands = handsField.get(controller) as MutableMap<UUID, MutableList<doublemoon.mahjongcraft.paper.model.MahjongTile>>
        hands[playerId] = tiles.map(doublemoon.mahjongcraft.paper.model.MahjongTile::valueOf).toMutableList()
    }

    private fun addPung(controller: GbTableRoundController, playerId: UUID, tile: String) {
        val meldsField = GbTableRoundController::class.java.getDeclaredField("melds")
        meldsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val melds = meldsField.get(controller) as MutableMap<UUID, MutableList<Any>>
        val meldClass = Class.forName("doublemoon.mahjongcraft.paper.table.core.round.GbTableRoundController\$GbMeldState")
        val pungMethod = meldClass.getDeclaredMethod("pung", doublemoon.mahjongcraft.paper.model.MahjongTile::class.java, SeatWind::class.java)
        pungMethod.isAccessible = true
        val pung = pungMethod.invoke(null, doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile), SeatWind.SOUTH)
        melds.getValue(playerId).add(pung)
    }

    private fun player(wind: SeatWind): UUID = UUID.nameUUIDFromBytes(wind.name.toByteArray())
}
