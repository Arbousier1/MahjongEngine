package top.ellan.mahjong.perf

import top.ellan.mahjong.gb.jni.GbFanEntry
import top.ellan.mahjong.gb.jni.GbTingCandidate
import top.ellan.mahjong.gb.jni.GbTingRequest
import top.ellan.mahjong.gb.jni.GbTingResponse
import top.ellan.mahjong.gb.runtime.GbNativeRulesGateway
import top.ellan.mahjong.model.MahjongTile
import top.ellan.mahjong.model.SeatWind
import top.ellan.mahjong.riichi.model.MahjongRule
import top.ellan.mahjong.table.core.round.GbTableRoundController
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.EnumMap
import java.util.UUID

@Tag("perf")
class GbBotSuggestionBenchmarkTest {
    @Test
    fun `benchmark gb bot discard suggestion`() {
        val controller = gbBotSuggestionController()
        val playerId = controller.playerAt(controller.currentSeat())!!

        PerformanceBenchmarkSupport.run(
            name = "gb.bot.suggest_discard.duplicate_hand",
            batch = 300
        ) {
            controller.suggestedBotDiscardIndex(playerId)
        }
    }

    @Test
    fun `benchmark gb native ting cache hit`() {
        var nativeCalls = 0
        val gateway = object : GbNativeRulesGateway(512, 256) {
            override fun isAvailable(): Boolean = true

            override fun evaluateTingNative(request: GbTingRequest): GbTingResponse {
                nativeCalls++
                var score = 0
                repeat(512) {
                    request.handTiles.forEachIndexed { index, tile ->
                        score += tile.hashCode() * (index + 1)
                    }
                    request.melds.forEachIndexed { index, meld ->
                        score += meld.type.hashCode() * (index + 3)
                        meld.tiles.forEach { tile -> score += tile.hashCode() }
                    }
                }
                PerformanceBenchmarkSupport.consume(score)
                val fan = score.mod(3) + 1
                return GbTingResponse(
                    true,
                    listOf(
                        GbTingCandidate(
                            "M1",
                            fan,
                            listOf(GbFanEntry("TEST", fan))
                        )
                    ),
                    null
                )
            }
        }
        val request = GbTingRequest(
            handTiles = listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "P1", "P2", "S1", "S2"),
            seatWind = "EAST",
            roundWind = "EAST"
        )
        gateway.evaluateTing(request)

        PerformanceBenchmarkSupport.run(
            name = "gb.native_gateway.ting_cache.hit",
            batch = 1000
        ) {
            gateway.evaluateTing(request)
        }
        PerformanceBenchmarkSupport.consume(nativeCalls)
    }

    private fun gbBotSuggestionController(): GbTableRoundController {
        val seats = EnumMap<SeatWind, UUID>(SeatWind::class.java)
        val names = mutableMapOf<UUID, String>()
        SeatWind.values().forEach { wind ->
            val playerId = UUID.nameUUIDFromBytes(("bot-" + wind.name).toByteArray())
            seats[wind] = playerId
            names[playerId] = wind.name
        }
        val controller = GbTableRoundController(
            MahjongRule(),
            seats,
            names,
            object : GbNativeRulesGateway() {
                override fun evaluateTingNative(request: GbTingRequest): GbTingResponse {
                    var score = 0
                    repeat(512) {
                        request.handTiles.forEachIndexed { index, tile ->
                            score += tile.hashCode() * (index + 1)
                        }
                        request.melds.forEachIndexed { index, meld ->
                            score += meld.type.hashCode() * (index + 3)
                            meld.tiles.forEach { tile -> score += tile.hashCode() }
                        }
                    }
                    PerformanceBenchmarkSupport.consume(score)
                    val fan = score.mod(3) + 1
                    return GbTingResponse(
                        true,
                        listOf(
                            GbTingCandidate(
                                "M1",
                                fan,
                                listOf(GbFanEntry("TEST", fan))
                            )
                        ),
                        null
                    )
                }
            }
        )
        controller.startRound()
        forceDuplicateHand(controller)
        return controller
    }

    @Suppress("UNCHECKED_CAST")
    private fun forceDuplicateHand(controller: GbTableRoundController) {
        val handsField = controller.javaClass.getDeclaredField("hands")
        handsField.isAccessible = true
        val hands = handsField.get(controller) as MutableMap<UUID, MutableList<MahjongTile>>
        val playerId = controller.playerAt(controller.currentSeat())!!
        hands[playerId] = MutableList(14) { MahjongTile.M1 }
    }
}

