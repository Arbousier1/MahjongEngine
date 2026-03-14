package doublemoon.mahjongcraft.paper.riichi

import doublemoon.mahjongcraft.paper.riichi.model.ClaimTarget
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile
import doublemoon.mahjongcraft.paper.riichi.model.MeldType
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiichiPlayerStateTest {
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
}
