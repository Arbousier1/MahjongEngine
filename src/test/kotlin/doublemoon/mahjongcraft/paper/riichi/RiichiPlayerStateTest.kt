package doublemoon.mahjongcraft.paper.riichi

import doublemoon.mahjongcraft.paper.riichi.model.ClaimTarget
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile
import doublemoon.mahjongcraft.paper.riichi.model.MeldType
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance
import java.lang.reflect.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RiichiPlayerStateTest {
    @Test
    fun `tile pairs for riichi cache invalidates when hand changes`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.drawTile(TileInstance(mahjongTile = MahjongTile.M1))
        player.tilePairsForRiichi
        val cachedVersionBefore = cachedTilePairsVersion(player)
        assertEquals(analysisVersion(player), cachedVersionBefore)

        player.discardTile(MahjongTile.M1)

        assertTrue(analysisVersion(player) > cachedVersionBefore)

        player.tilePairsForRiichi

        assertEquals(analysisVersion(player), cachedTilePairsVersion(player))
    }

    @Test
    fun `reset round state clears cached hand analysis`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.drawTile(TileInstance(mahjongTile = MahjongTile.M1))
        player.riichi = true
        player.doubleRiichi = true
        player.tilePairsForRiichi
        val cachedVersionBefore = cachedTilePairsVersion(player)

        player.resetRoundState()

        assertTrue(analysisVersion(player) > cachedVersionBefore)
        assertTrue(player.hands.isEmpty())
        assertTrue(player.fuuroList.isEmpty())
        assertFalse(player.riichi)
        assertFalse(player.doubleRiichi)

        player.tilePairsForRiichi

        assertEquals(analysisVersion(player), cachedTilePairsVersion(player))
    }

    @Test
    fun `pon removes two matching hand tiles and creates open meld`() {
        val player = RiichiPlayerState("Alice", "alice")
        val target = RiichiPlayerState("Bob", "bob")
        val discard = TileInstance(mahjongTile = MahjongTile.M5)
        player.hands += listOf(
            TileInstance(mahjongTile = MahjongTile.M5),
            TileInstance(mahjongTile = MahjongTile.M5_RED),
            TileInstance(mahjongTile = MahjongTile.P1)
        )
        target.discardedTilesForDisplay += discard

        player.pon(discard, ClaimTarget.RIGHT, target)

        assertEquals(1, player.hands.size)
        assertEquals(MahjongTile.P1, player.hands.single().mahjongTile)
        assertEquals(1, player.fuuroList.size)
        assertEquals(MeldType.PON, player.fuuroList.single().type)
        assertTrue(player.fuuroList.single().isOpen)
        assertFalse(target.discardedTilesForDisplay.contains(discard))
    }

    @Test
    fun `ankan removes four matching tiles and creates concealed kan`() {
        val player = RiichiPlayerState("Alice", "alice")
        val tile = TileInstance(mahjongTile = MahjongTile.EAST)
        player.hands += listOf(
            tile,
            TileInstance(mahjongTile = MahjongTile.EAST),
            TileInstance(mahjongTile = MahjongTile.EAST),
            TileInstance(mahjongTile = MahjongTile.EAST)
        )

        player.ankan(tile)

        assertTrue(player.hands.isEmpty())
        assertEquals(1, player.fuuroList.size)
        assertEquals(MeldType.ANKAN, player.fuuroList.single().type)
        assertTrue(player.fuuroList.single().isKan)
        assertFalse(player.fuuroList.single().isOpen)
    }

    @Test
    fun `drawing winning tile refreshes riichi availability`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3)
        player.tilePairsForRiichi
        val cachedVersionBefore = cachedTilePairsVersion(player)

        player.drawTile(TileInstance(mahjongTile = MahjongTile.M4))

        assertTrue(analysisVersion(player) > cachedVersionBefore)
        assertEquals(cachedVersionBefore, cachedTilePairsVersion(player))
    }

    @Test
    fun `furiten checks start from the actual last discard instance`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
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
        val earlierSameKind = TileInstance(mahjongTile = MahjongTile.M9)
        val waitedTileFromEarlierTurn = TileInstance(mahjongTile = MahjongTile.EAST)
        val ownLastDiscard = TileInstance(mahjongTile = MahjongTile.M9)
        val currentWinningDiscard = TileInstance(mahjongTile = MahjongTile.EAST)
        player.discardedTiles += ownLastDiscard

        assertFalse(
            player.isFuriten(
                currentWinningDiscard,
                listOf(earlierSameKind, waitedTileFromEarlierTurn, ownLastDiscard, currentWinningDiscard)
            )
        )
    }

    @Test
    fun `discarding a selected tile removes the exact tile instance`() {
        val player = RiichiPlayerState("Alice", "alice")
        val selected = TileInstance(mahjongTile = MahjongTile.M1)
        val otherSameKind = TileInstance(mahjongTile = MahjongTile.M1)
        player.hands += listOf(selected, otherSameKind, TileInstance(mahjongTile = MahjongTile.P5))

        val discarded = player.discardTile(selected)

        assertSame(selected, discarded)
        assertFalse(player.hands.contains(selected))
        assertTrue(player.hands.contains(otherSameKind))
    }

    @Test
    fun `available chii pairs come from mahjong utils furo analysis`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M3,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.P1,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.P3,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.S6
        )

        val pairs = player.availableChiiPairs(TileInstance(mahjongTile = MahjongTile.M2))

        assertEquals(
            setOf(MahjongTile.M1 to MahjongTile.M3, MahjongTile.M3 to MahjongTile.M4),
            pairs.toSet()
        )
    }

    private fun analysisVersion(player: RiichiPlayerState): Long =
        playerField("analysisStateVersion").getLong(player)

    private fun cachedTilePairsVersion(player: RiichiPlayerState): Long =
        playerField("cachedTilePairsForRiichiVersion").getLong(player)

    private fun playerField(name: String): Field =
        RiichiPlayerState::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun tiles(vararg tiles: MahjongTile): List<TileInstance> =
        tiles.map { TileInstance(mahjongTile = it) }
}
