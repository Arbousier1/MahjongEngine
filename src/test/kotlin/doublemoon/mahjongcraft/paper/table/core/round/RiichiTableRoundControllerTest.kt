package doublemoon.mahjongcraft.paper.table.core.round

import doublemoon.mahjongcraft.paper.riichi.RiichiPlayerState
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine
import doublemoon.mahjongcraft.paper.riichi.model.ClaimTarget
import doublemoon.mahjongcraft.paper.riichi.model.Fuuro
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile
import doublemoon.mahjongcraft.paper.riichi.model.MeldType
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance
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

