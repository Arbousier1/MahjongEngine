package doublemoon.mahjongcraft.paper.perf

import doublemoon.mahjongcraft.paper.gb.jni.GbFanEntry
import doublemoon.mahjongcraft.paper.gb.jni.GbTingCandidate
import doublemoon.mahjongcraft.paper.gb.jni.GbTingRequest
import doublemoon.mahjongcraft.paper.gb.jni.GbTingResponse
import doublemoon.mahjongcraft.paper.gb.runtime.GbNativeRulesGateway
import doublemoon.mahjongcraft.paper.model.MahjongTile
import doublemoon.mahjongcraft.paper.model.SeatWind
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule
import doublemoon.mahjongcraft.paper.table.core.round.GbTableRoundController
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
                override fun evaluateTing(request: GbTingRequest): GbTingResponse {
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
