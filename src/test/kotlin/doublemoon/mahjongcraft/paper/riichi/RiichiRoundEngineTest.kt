package doublemoon.mahjongcraft.paper.riichi

import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance
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

    @Test
    fun `engine preserves input seat order`() {
        val players = listOf(
            RiichiPlayerState("East", "east"),
            RiichiPlayerState("South", "south"),
            RiichiPlayerState("West", "west"),
            RiichiPlayerState("North", "north")
        )

        val engine = RiichiRoundEngine(players, MahjongRule())

        assertEquals(listOf("east", "south", "west", "north"), engine.seats.map { it.uuid })
    }

    @Test
    fun `normal draw with all players tenpai does not divide by zero`() {
        val players = listOf(
            RiichiPlayerState("A", "a"),
            RiichiPlayerState("B", "b"),
            RiichiPlayerState("C", "c"),
            RiichiPlayerState("D", "d")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())

        engine.startRound()
        engine.seats.forEach { player ->
            player.hands.clear()
            repeat(if (player == engine.currentPlayer) 14 else 13) { index ->
                player.hands += TileInstance(mahjongTile = if (index % 2 == 0) MahjongTile.M1 else MahjongTile.P1)
            }
        }
        engine.wall.clear()

        val result = engine.discard(engine.currentPlayer.uuid, 0)

        assertTrue(result)
        val scoreChanges = engine.lastResolution?.scoreSettlement?.scoreList?.map { it.scoreChange }
        if (scoreChanges != null) {
            assertEquals(0, scoreChanges.sum())
        }
    }
}
