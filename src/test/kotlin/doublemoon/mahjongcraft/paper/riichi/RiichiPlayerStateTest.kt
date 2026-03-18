package doublemoon.mahjongcraft.paper.riichi

import doublemoon.mahjongcraft.paper.riichi.model.ClaimTarget
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile
import doublemoon.mahjongcraft.paper.riichi.model.MeldType
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance
import java.lang.reflect.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun analysisVersion(player: RiichiPlayerState): Long =
        playerField("analysisStateVersion").getLong(player)

    private fun cachedTilePairsVersion(player: RiichiPlayerState): Long =
        playerField("cachedTilePairsForRiichiVersion").getLong(player)

    private fun playerField(name: String): Field =
        RiichiPlayerState::class.java.getDeclaredField(name).apply { isAccessible = true }
}
