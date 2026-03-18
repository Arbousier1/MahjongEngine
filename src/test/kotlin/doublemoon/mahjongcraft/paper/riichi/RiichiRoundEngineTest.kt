package doublemoon.mahjongcraft.paper.riichi

import doublemoon.mahjongcraft.paper.table.core.round.OpeningDiceRoll
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
    fun `engine uses pending opening dice roll when provided`() {
        val players = listOf(
            RiichiPlayerState("A", "a"),
            RiichiPlayerState("B", "b"),
            RiichiPlayerState("C", "c"),
            RiichiPlayerState("D", "d")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())
        engine.setPendingDiceRoll(OpeningDiceRoll(3, 4))

        engine.startRound()

        assertEquals(7, engine.dicePoints)
    }

    @Test
    fun `dora indicators stay empty before dead wall is assigned`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("A", "a"),
                RiichiPlayerState("B", "b"),
                RiichiPlayerState("C", "c"),
                RiichiPlayerState("D", "d")
            ),
            MahjongRule()
        )

        assertTrue(engine.doraIndicators.isEmpty())
        assertTrue(engine.uraDoraIndicators.isEmpty())
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
        val tenpaiHand = listOf(
            MahjongTile.M1,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M3,
            MahjongTile.P4,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P5,
            MahjongTile.S6,
            MahjongTile.S6,
            MahjongTile.EAST
        )
        engine.seats.forEach { player ->
            player.hands.clear()
            tenpaiHand.forEach { tile ->
                player.hands += TileInstance(mahjongTile = tile)
            }
            if (player == engine.currentPlayer) {
                player.hands += TileInstance(mahjongTile = MahjongTile.WHITE_DRAGON)
            }
        }
        engine.wall.clear()

        val result = engine.discard(engine.currentPlayer.uuid, engine.currentPlayer.hands.lastIndex)

        assertTrue(result)
        val scoreChanges = engine.lastResolution?.scoreSettlement?.scoreList?.map { it.scoreChange }
        if (scoreChanges != null) {
            assertEquals(0, scoreChanges.sum())
        }
    }

    @Test
    fun `discard uses the exact selected tile instance`() {
        val players = listOf(
            RiichiPlayerState("A", "a"),
            RiichiPlayerState("B", "b"),
            RiichiPlayerState("C", "c"),
            RiichiPlayerState("D", "d")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())

        engine.startRound()

        val player = engine.currentPlayer
        player.resetRoundState()
        val selected = TileInstance(mahjongTile = MahjongTile.M1)
        val otherSameKind = TileInstance(mahjongTile = MahjongTile.M1)
        player.hands += listOf(selected, otherSameKind, TileInstance(mahjongTile = MahjongTile.P5))

        assertTrue(engine.discard(player.uuid, 0))
        assertSame(selected, engine.discards.last())
        assertFalse(player.hands.contains(selected))
        assertTrue(player.hands.contains(otherSameKind))
    }
}
