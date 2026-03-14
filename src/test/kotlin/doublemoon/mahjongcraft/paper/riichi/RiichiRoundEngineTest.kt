package doublemoon.mahjongcraft.paper.riichi

import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiichiRoundEngineTest {
    @Test
    fun `start round deals dealer 14 and others 13`() {
        val players = listOf(
            RiichiPlayerState("A", "a"),
            RiichiPlayerState("B", "b"),
            RiichiPlayerState("C", "c"),
            RiichiPlayerState("D", "d")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())

        engine.startRound()

        assertTrue(engine.started)
        assertEquals(4, engine.seats.size)
        assertEquals(1, engine.doraIndicators.size)
        assertEquals(14, engine.currentPlayer.hands.size)
        assertEquals(3, engine.seats.count { it.hands.size == 13 })
        assertEquals(1, engine.seats.count { it.hands.size == 14 })
        assertFalse(engine.gameFinished)
    }

    @Test
    fun `engine initializes starting points for all seats`() {
        val rule = MahjongRule(startingPoints = 32000)
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("A", "a"),
                RiichiPlayerState("B", "b"),
                RiichiPlayerState("C", "c"),
                RiichiPlayerState("D", "d")
            ),
            rule
        )

        assertTrue(engine.seats.all { it.points == 32000 })
    }
}
