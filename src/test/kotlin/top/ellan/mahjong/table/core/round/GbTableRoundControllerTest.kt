package top.ellan.mahjong.table.core.round

import top.ellan.mahjong.gb.jni.GbFanEntry
import top.ellan.mahjong.gb.jni.GbFanRequest
import top.ellan.mahjong.gb.jni.GbFanResponse
import top.ellan.mahjong.gb.jni.GbScoreDelta
import top.ellan.mahjong.gb.jni.GbTingCandidate
import top.ellan.mahjong.gb.jni.GbTingRequest
import top.ellan.mahjong.gb.jni.GbTingResponse
import top.ellan.mahjong.gb.jni.GbWinRequest
import top.ellan.mahjong.gb.jni.GbWinResponse
import top.ellan.mahjong.gb.runtime.GbNativeRulesGateway
import top.ellan.mahjong.gb.runtime.GbTileEncoding
import top.ellan.mahjong.model.MahjongTile
import top.ellan.mahjong.model.SeatWind
import top.ellan.mahjong.riichi.ReactionResponse
import top.ellan.mahjong.riichi.ReactionType
import top.ellan.mahjong.riichi.model.MahjongRule
import java.util.EnumMap
import java.util.UUID
import java.util.function.IntSupplier
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `sichuan profile deals suited tiles only`() {
        val controller = controller(profile = GbRuleProfile.SICHUAN)

        controller.startRound()

        val allHands = SeatWind.values().flatMap { wind -> controller.hand(player(wind)) }
        assertTrue(allHands.all { !it.isFlower && !GbRoundSupport.isHonor(it) })
    }

    @Test
    fun `gb round rolls dice and breaks wall from the matching side`() {
        val sourceWall = deterministicWall()
        val controller = controller(wall = sourceWall)
        controller.setPendingDiceRoll(OpeningDiceRoll(3, 4))

        controller.startRound()

        assertEquals(7, controller.dicePoints())
        assertEquals(reorderWall(sourceWall, 7, 0).drop(53), currentWall(controller))
    }

    @Test
    fun `discard draw supplements flower from the back and exposes it publicly`() {
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                GbFanResponse(false, 0, emptyList(), "cannot win")

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                GbTingResponse(true, emptyList(), null)

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(false, error = "cannot win")
        })
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)
        val west = player(SeatWind.WEST)
        val north = player(SeatWind.NORTH)

        forceHand(controller, east, listOf("NORTH", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "P3", "P4"))
        forceHand(controller, south, listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "P3", "P4"))
        forceHand(controller, west, listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "P3", "P4"))
        forceHand(controller, north, listOf("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "P1", "P2", "P3", "P4"))
        forceWall(controller, listOf("PLUM", "S9"))

        assertTrue(controller.discard(east, 0))
        assertFalse(controller.hasPendingReaction())
        assertEquals(14, controller.hand(south).size)
        assertFalse(controller.hand(south).contains(top.ellan.mahjong.model.MahjongTile.PLUM))
        assertEquals(listOf(top.ellan.mahjong.model.MahjongTile.PLUM), controller.fuuro(south).single().tiles())
        assertEquals(top.ellan.mahjong.model.MahjongTile.S9, controller.hand(south).last())
    }

    @Test
    fun `drawn tile stays on the right while the rest of gb hand is sorted`() {
        val controller = controller()
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)

        forceHand(controller, east, listOf("M9", "P9", "S9", "EAST", "SOUTH", "WEST", "NORTH", "WHITE_DRAGON", "GREEN_DRAGON", "RED_DRAGON", "M1", "P1", "S1", "M2"))
        forceHand(controller, south, listOf("P3", "M3", "EAST", "S2", "M1", "RED_DRAGON", "P1", "S1", "M2", "P2", "S3", "M4", "P4"))
        forceWall(controller, listOf("M5"))

        assertTrue(controller.discard(east, 0))
        if (controller.availableReactions(south) != null) {
            assertTrue(controller.react(south, ReactionResponse(ReactionType.SKIP, null)))
        }
        if (controller.availableReactions(player(SeatWind.WEST)) != null) {
            assertTrue(controller.react(player(SeatWind.WEST), ReactionResponse(ReactionType.SKIP, null)))
        }
        if (controller.availableReactions(player(SeatWind.NORTH)) != null) {
            assertTrue(controller.react(player(SeatWind.NORTH), ReactionResponse(ReactionType.SKIP, null)))
        }
        assertEquals(
            listOf(
                MahjongTile.M1,
                MahjongTile.M2,
                MahjongTile.M3,
                MahjongTile.M4,
                MahjongTile.P1,
                MahjongTile.P2,
                MahjongTile.P3,
                MahjongTile.P4,
                MahjongTile.S1,
                MahjongTile.S2,
                MahjongTile.S3,
                MahjongTile.EAST,
                MahjongTile.RED_DRAGON,
                MahjongTile.M5
            ),
            controller.hand(south)
        )
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
        assertEquals(0, controller.fuuro(south).single().claimTileIndex())
        assertEquals(0, controller.discards(east).size)
        assertEquals(
            listOf(
                MahjongTile.P1,
                MahjongTile.P2,
                MahjongTile.S1,
                MahjongTile.S2,
                MahjongTile.S3,
                MahjongTile.S4,
                MahjongTile.S5,
                MahjongTile.S6,
                MahjongTile.S7,
                MahjongTile.S8,
                MahjongTile.S9
            ),
            controller.hand(south)
        )
        assertFalse(controller.canWinByTsumo(south))
    }

    @Test
    fun `chii removes claimed discard from river`() {
        val controller = controller()
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)
        val west = player(SeatWind.WEST)
        val north = player(SeatWind.NORTH)

        forceHand(controller, east, listOf("M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "P3", "P4", "P5", "P6"))
        forceHand(controller, south, listOf("M1", "M3", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "P1", "P2"))

        assertTrue(controller.discard(east, 0))
        val chiiPair = controller.availableReactions(south)?.chiiPairs?.single()
        assertNotNull(chiiPair)

        assertTrue(controller.react(south, ReactionResponse(ReactionType.CHII, chiiPair)))
        if (controller.availableReactions(west) != null) {
            assertTrue(controller.react(west, ReactionResponse(ReactionType.SKIP, null)))
        }
        if (controller.availableReactions(north) != null) {
            assertTrue(controller.react(north, ReactionResponse(ReactionType.SKIP, null)))
        }

        assertEquals(0, controller.discards(east).size)
        assertEquals(1, controller.fuuro(south).size)
        assertEquals(SeatWind.SOUTH, controller.currentSeat())
    }

    @Test
    fun `minkan takes priority over chii on the same discard`() {
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                GbFanResponse(false, 0, emptyList(), "cannot win")

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                GbTingResponse(true, emptyList(), null)

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(false, error = "cannot win")
        })
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)
        val west = player(SeatWind.WEST)

        forceHand(controller, east, listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "P3", "P4", "P5"))
        forceHand(controller, south, listOf("M2", "M3", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "P1", "P2"))
        forceHand(controller, west, listOf("M1", "M1", "M1", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "P1"))

        assertTrue(controller.discard(east, 0))
        val chiiPair = controller.availableReactions(south)?.chiiPairs?.single()
        assertNotNull(chiiPair)
        assertTrue(controller.availableReactions(west)?.canMinkan == true)

        assertTrue(controller.react(south, ReactionResponse(ReactionType.CHII, chiiPair)))
        assertTrue(controller.react(west, ReactionResponse(ReactionType.MINKAN, null)))

        assertEquals(SeatWind.WEST, controller.currentSeat())
        val southMelds = controller.fuuro(south).filter { it.claimTileIndex() >= 0 }
        val westMelds = controller.fuuro(west).filter { it.claimTileIndex() >= 0 }
        assertEquals(0, southMelds.size)
        assertEquals(1, westMelds.size)
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
    fun `sichuan tsumo uses local hu evaluation`() {
        val controller = controller(profile = GbRuleProfile.SICHUAN)
        controller.startRound()
        val east = player(SeatWind.EAST)

        forceHand(controller, east, listOf("M1", "M1", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "P2", "P3", "P4", "S9", "S9"))

        assertTrue(controller.canWinByTsumo(east))
        assertTrue(controller.declareTsumo(east))
        assertTrue(controller.started())
        assertEquals(SeatWind.SOUTH, controller.currentSeat())
        assertFalse(controller.canSelectHandTile(east, 0))
        assertNull(controller.lastResolution())
    }

    @Test
    fun `sichuan blood battle ends after three winners`() {
        val controller = controller(profile = GbRuleProfile.SICHUAN)
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)
        val west = player(SeatWind.WEST)
        val north = player(SeatWind.NORTH)

        forceHand(controller, east, listOf("M1", "M1", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "P2", "P3", "P4", "S9", "S9"))
        forceHand(controller, south, listOf("M1", "M1", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "P2", "P3", "P4", "S9"))
        forceHand(controller, west, listOf("M1", "M1", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "P2", "P3", "P4", "S9"))
        forceHand(controller, north, listOf("M2", "M3", "M4", "M5", "M6", "M7", "P1", "P2", "P3", "S1", "S2", "S3", "S4"))
        forceWall(controller, listOf("S9", "S9", "M1"))

        assertTrue(controller.declareTsumo(east))
        assertTrue(controller.started())
        assertEquals(SeatWind.SOUTH, controller.currentSeat())
        assertTrue(controller.canDeclareTsumo(south))

        assertTrue(controller.declareTsumo(south))
        assertTrue(controller.started())
        assertEquals(SeatWind.WEST, controller.currentSeat())
        assertTrue(controller.canDeclareTsumo(west))

        assertTrue(controller.declareTsumo(west))
        assertFalse(controller.started())
        assertEquals("TSUMO", controller.lastResolution()?.title)
        assertEquals(3, controller.lastResolution()?.yakuSettlements?.size)
    }

    @Test
    fun `gb round advances dealer between hands and keeps east round wind`() {
        val controller = controller()
        controller.startRound()

        assertTrue(controller.declareTsumo(player(SeatWind.EAST)))
        assertFalse(controller.started())
        assertFalse(controller.gameFinished())
        assertEquals(SeatWind.SOUTH, controller.dealerSeat())
        assertEquals(SeatWind.EAST, controller.roundWind())
        assertEquals(1, controller.roundIndex())

        controller.startRound()

        assertEquals(14, controller.hand(player(SeatWind.SOUTH)).size)
        assertEquals(13, controller.hand(player(SeatWind.EAST)).size)
    }

    @Test
    fun `gb native requests use prevailing round wind after east cycle advances`() {
        val gateway = CapturingGateway()
        val controller = controller(gateway)

        controller.startRound()
        assertTrue(controller.declareTsumo(player(SeatWind.EAST)))
        controller.startRound()
        assertTrue(controller.declareTsumo(player(SeatWind.SOUTH)))
        controller.startRound()
        assertTrue(controller.declareTsumo(player(SeatWind.WEST)))
        controller.startRound()
        assertTrue(controller.declareTsumo(player(SeatWind.NORTH)))

        assertEquals(SeatWind.SOUTH, controller.roundWind())
        assertEquals(SeatWind.EAST, controller.dealerSeat())
        assertEquals(0, controller.roundIndex())

        controller.startRound()
        assertTrue(controller.canWinByTsumo(player(SeatWind.EAST)))
        assertEquals("EAST", gateway.lastFanRequest?.seatWind)
        assertEquals("SOUTH", gateway.lastFanRequest?.roundWind)
    }

    @Test
    fun `gb native fan requests include collected flower tiles`() {
        val gateway = CapturingGateway()
        val controller = controller(gateway)
        val east = player(SeatWind.EAST)

        controller.startRound()
        forceFlowers(controller, east, listOf("PLUM", "WINTER"))

        assertTrue(controller.canWinByTsumo(east))
        assertEquals(listOf("a", "h"), gateway.lastFanRequest?.flowerTiles)
    }

    @Test
    fun `gb bot discard suggestion prefers discard that keeps eight fan waits`() {
        val targetHand = encodedTiles("M1", "M2", "M3", "M4", "M5", "M6", "P1", "P2", "P3", "S1", "S2", "S3", "RED_DRAGON")
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                GbFanResponse(false, 0, emptyList(), "cannot win")

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                if (request.handTiles == targetHand) {
                    GbTingResponse(true, listOf(GbTingCandidate("W9", 8, listOf(GbFanEntry("Mock Fan", 8, 1)))), null)
                } else {
                    GbTingResponse(true, emptyList(), null)
                }

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(false, error = "cannot win")
        })
        controller.startRound()
        val east = player(SeatWind.EAST)
        forceHand(controller, east, listOf("M1", "M2", "M3", "M4", "M5", "M6", "P1", "P2", "P3", "S1", "S2", "S3", "EAST", "RED_DRAGON"))

        assertEquals(12, controller.suggestedBotDiscardIndex(east))
    }

    @Test
    fun `gb bot reaction prefers pon when it creates eight fan ready shape`() {
        val targetHand = encodedTiles("P1", "P2", "P3", "P4", "S1", "S2", "S3", "S4", "S5", "S6")
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                GbFanResponse(false, 0, emptyList(), "cannot win")

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                if (request.melds.any { it.type == "PUNG" } && request.handTiles == targetHand) {
                    GbTingResponse(true, listOf(GbTingCandidate("B9", 8, listOf(GbFanEntry("Mock Fan", 8, 1)))), null)
                } else {
                    GbTingResponse(true, emptyList(), null)
                }

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(false, error = "cannot win")
        })
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)

        forceHand(controller, east, listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "P3", "P4", "P5"))
        forceHand(controller, south, listOf("M1", "M1", "P1", "P2", "P3", "P4", "S1", "S2", "S3", "S4", "S5", "S6", "RED_DRAGON"))

        assertTrue(controller.discard(east, 0))
        assertEquals(ReactionType.PON, controller.suggestedBotReaction(south).type)
    }

    @Test
    fun `gb bot reaction skips pon when it does not improve to qualified waits`() {
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                GbFanResponse(false, 0, emptyList(), "cannot win")

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                GbTingResponse(true, emptyList(), null)

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(false, error = "cannot win")
        })
        controller.startRound()
        val east = player(SeatWind.EAST)
        val south = player(SeatWind.SOUTH)

        forceHand(controller, east, listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "P3", "P4", "P5"))
        forceHand(controller, south, listOf("M1", "M1", "P1", "P2", "P3", "P4", "S1", "S2", "S3", "S4", "S5", "S6", "RED_DRAGON"))

        assertTrue(controller.discard(east, 0))
        assertEquals(ReactionType.SKIP, controller.suggestedBotReaction(south).type)
    }

    @Test
    fun `gb bot kan suggestion only keeps kong when it leads to qualified waits`() {
        val targetHand = encodedTiles("P1", "P2", "P3", "S1", "S2", "S3", "S4", "S5", "S6", "RED_DRAGON")
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                GbFanResponse(false, 0, emptyList(), "cannot win")

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                if (request.melds.any { it.type == "CONCEALED_KONG" } && request.handTiles == targetHand) {
                    GbTingResponse(true, listOf(GbTingCandidate("T9", 8, listOf(GbFanEntry("Mock Fan", 8, 1)))), null)
                } else {
                    GbTingResponse(true, emptyList(), null)
                }

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(false, error = "cannot win")
        })
        controller.startRound()
        val east = player(SeatWind.EAST)

        forceHand(controller, east, listOf("M1", "M1", "M1", "M1", "P1", "P2", "P3", "S1", "S2", "S3", "S4", "S5", "S6", "RED_DRAGON"))

        assertEquals("m1", controller.suggestedBotKanTile(east))
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
    fun `concealed kan with empty replacement wall ends the hand in draw`() {
        val controller = controller()
        controller.startRound()
        val east = player(SeatWind.EAST)

        forceHand(controller, east, listOf("M1", "M1", "M1", "M1", "P1", "P2", "P3", "S1", "S2", "S3", "S4", "S5", "S6", "S7"))
        forceWall(controller, emptyList())

        assertTrue(controller.declareKan(east, "m1"))
        assertFalse(controller.started())
        assertEquals("DRAW", controller.lastResolution()?.title)
        assertEquals(1, controller.kanCount())
    }

    @Test
    fun `gb ron uses intercept priority when multiple players call on one discard`() {
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
        assertEquals(1, controller.lastResolution()?.yakuSettlements?.size)
        assertEquals("SOUTH", controller.lastResolution()?.yakuSettlements?.single()?.displayName)
        assertEquals(-8, controller.lastResolution()?.scoreSettlement?.scoreList?.first { it.stringUUID == east.toString() }?.scoreChange)
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

    @Test
    fun `added kong displays as three base tiles plus one stacked tile`() {
        val controller = controller(object : GbNativeRulesGateway() {
            override fun isAvailable(): Boolean = true

            override fun evaluateFan(request: GbFanRequest): GbFanResponse =
                GbFanResponse(false, 0, emptyList(), "cannot win")

            override fun evaluateTing(request: GbTingRequest): GbTingResponse =
                GbTingResponse(true, emptyList(), null)

            override fun evaluateWin(request: GbWinRequest): GbWinResponse =
                GbWinResponse(false, error = "cannot win")
        })
        controller.startRound()
        val east = player(SeatWind.EAST)

        addPung(controller, east, "P3")
        forceHand(controller, east, listOf("P3", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "S1", "S2", "S3", "S4"))

        assertTrue(controller.declareKan(east, "p3"))
        val meld = controller.fuuro(east).first { it.addedKanTile() != null }
        assertEquals(3, meld.tiles().size)
        assertEquals(MahjongTile.P3, meld.addedKanTile())
    }

    private fun controller(
        gateway: GbNativeRulesGateway = defaultGateway(),
        profile: GbRuleProfile = GbRuleProfile.GB,
        dicePoints: Int? = null,
        wall: List<MahjongTile>? = null
    ): GbTableRoundController {
        val seats = EnumMap<SeatWind, UUID>(SeatWind::class.java)
        val names = mutableMapOf<UUID, String>()
        SeatWind.values().forEach { wind ->
            val playerId = player(wind)
            seats[wind] = playerId
            names[playerId] = wind.name
        }
        return if (dicePoints == null && wall == null) {
            GbTableRoundController(MahjongRule(), seats, names, gateway, profile)
        } else {
            GbTableRoundController(
                MahjongRule(),
                seats,
                names,
                gateway,
                profile,
                IntSupplier { dicePoints ?: 7 },
                Supplier { wall ?: deterministicWall() }
            )
        }
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
        val hands = handsField.get(controller) as MutableMap<UUID, MutableList<top.ellan.mahjong.model.MahjongTile>>
        hands[playerId] = tiles.map(top.ellan.mahjong.model.MahjongTile::valueOf).toMutableList()
        forceFlowers(controller, playerId, emptyList())
    }

    private fun addPung(controller: GbTableRoundController, playerId: UUID, tile: String, selfSeat: SeatWind = SeatWind.EAST) {
        val meldsField = GbTableRoundController::class.java.getDeclaredField("melds")
        meldsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val melds = meldsField.get(controller) as MutableMap<UUID, MutableList<Any>>
        val meldClass = Class.forName("top.ellan.mahjong.table.core.round.GbTableRoundController\$GbMeldState")
        val pungMethod = meldClass.getDeclaredMethod(
            "pung",
            top.ellan.mahjong.model.MahjongTile::class.java,
            SeatWind::class.java,
            SeatWind::class.java
        )
        pungMethod.isAccessible = true
        val pung = pungMethod.invoke(
            null,
            top.ellan.mahjong.model.MahjongTile.valueOf(tile),
            SeatWind.SOUTH,
            selfSeat
        )
        melds.getValue(playerId).add(pung)
    }

    private fun forceWall(controller: GbTableRoundController, tiles: List<String>) {
        val wallField = GbTableRoundController::class.java.getDeclaredField("wall")
        wallField.isAccessible = true
        wallField.set(controller, tiles.map(top.ellan.mahjong.model.MahjongTile::valueOf))
    }

    private fun forceFlowers(controller: GbTableRoundController, playerId: UUID, tiles: List<String>) {
        val flowersField = GbTableRoundController::class.java.getDeclaredField("flowers")
        flowersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flowers = flowersField.get(controller) as MutableMap<UUID, MutableList<top.ellan.mahjong.model.MahjongTile>>
        flowers[playerId] = tiles.map(top.ellan.mahjong.model.MahjongTile::valueOf).toMutableList()
    }

    private fun currentWall(controller: GbTableRoundController): List<MahjongTile> {
        val wallField = GbTableRoundController::class.java.getDeclaredField("wall")
        wallField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return wallField.get(controller) as List<MahjongTile>
    }

    private fun deterministicWall(): List<MahjongTile> {
        val sequence = MahjongTile.values().filter { tile ->
            tile != MahjongTile.UNKNOWN && !tile.isRedFive && !tile.isFlower
        }
        return List(144) { sequence[it % sequence.size] }
    }

    private fun reorderWall(wall: List<MahjongTile>, dicePoints: Int, roundIndex: Int): List<MahjongTile> {
        val seatCount = SeatWind.values().size
        val wallTilesPerSide = wall.size / seatCount
        val directionIndex = seatCount - (((dicePoints % seatCount) - 1 + roundIndex) % seatCount)
        val breakIndex = (directionIndex * wallTilesPerSide + dicePoints * 2).mod(wall.size)
        return List(wall.size) { offset -> wall[(breakIndex + offset) % wall.size] }
    }

    private fun encodedTiles(vararg names: String): List<String> =
        names.map { GbTileEncoding.encode(MahjongTile.valueOf(it)) }

    private class CapturingGateway : GbNativeRulesGateway() {
        var lastFanRequest: GbFanRequest? = null

        override fun isAvailable(): Boolean = true

        override fun evaluateFan(request: GbFanRequest): GbFanResponse {
            lastFanRequest = request
            return GbFanResponse(true, 8, listOf(GbFanEntry("Mock Fan", 8, 1)), null)
        }

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

    private fun player(wind: SeatWind): UUID = UUID.nameUUIDFromBytes(wind.name.toByteArray())
}

