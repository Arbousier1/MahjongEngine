package top.ellan.mahjong.table.core.round

import top.ellan.mahjong.riichi.RiichiPlayerState
import top.ellan.mahjong.riichi.RiichiRoundEngine
import top.ellan.mahjong.riichi.model.ClaimTarget
import top.ellan.mahjong.riichi.model.Fuuro
import top.ellan.mahjong.riichi.model.MahjongTile
import top.ellan.mahjong.riichi.model.MeldType
import top.ellan.mahjong.riichi.model.TileInstance
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RiichiTableRoundControllerTest {
    @Test
    fun `pon claim source maps to left middle right slots`() {
        val east = RiichiPlayerState("East", "00000000-0000-0000-0000-000000000001")
        val south = RiichiPlayerState("South", "00000000-0000-0000-0000-000000000002")
        val west = RiichiPlayerState("West", "00000000-0000-0000-0000-000000000003")
        val north = RiichiPlayerState("North", "00000000-0000-0000-0000-000000000004")
        val engine = RiichiRoundEngine(listOf(east, south, west, north))
        val controller = RiichiTableRoundController(engine)
        val eastId = UUID.fromString(east.uuid)

        east.fuuroList.clear()
        east.fuuroList += ponFuuro(ClaimTarget.LEFT)
        assertEquals(0, controller.fuuro(eastId).single().claimTileIndex())

        east.fuuroList.clear()
        east.fuuroList += ponFuuro(ClaimTarget.ACROSS)
        assertEquals(1, controller.fuuro(eastId).single().claimTileIndex())

        east.fuuroList.clear()
        east.fuuroList += ponFuuro(ClaimTarget.RIGHT)
        assertEquals(2, controller.fuuro(eastId).single().claimTileIndex())
    }

    @Test
    fun `kakan is exposed as three base tiles plus stacked tile`() {
        val east = RiichiPlayerState("East", "00000000-0000-0000-0000-000000000001")
        val south = RiichiPlayerState("South", "00000000-0000-0000-0000-000000000002")
        val west = RiichiPlayerState("West", "00000000-0000-0000-0000-000000000003")
        val north = RiichiPlayerState("North", "00000000-0000-0000-0000-000000000004")
        val engine = RiichiRoundEngine(listOf(east, south, west, north))
        val controller = RiichiTableRoundController(engine)
        val eastId = UUID.fromString(east.uuid)

        val claim = TileInstance(mahjongTile = MahjongTile.P3)
        east.fuuroList.clear()
        east.fuuroList += Fuuro(
            type = MeldType.KAKAN,
            tileInstances = listOf(
                TileInstance(mahjongTile = MahjongTile.P3),
                TileInstance(mahjongTile = MahjongTile.P3),
                claim,
                TileInstance(mahjongTile = MahjongTile.P3)
            ),
            claimTarget = ClaimTarget.ACROSS,
            claimTile = claim
        )

        val meld = controller.fuuro(eastId).single()
        assertEquals(3, meld.tiles().size)
        assertEquals(top.ellan.mahjong.model.MahjongTile.P3, meld.addedKanTile())
    }

    private fun ponFuuro(target: ClaimTarget): Fuuro {
        val a = TileInstance(mahjongTile = MahjongTile.M5)
        val b = TileInstance(mahjongTile = MahjongTile.M5)
        val c = TileInstance(mahjongTile = MahjongTile.M5)
        return Fuuro(
            type = MeldType.PON,
            tileInstances = listOf(a, b, c),
            claimTarget = target,
            claimTile = a
        )
    }
}

