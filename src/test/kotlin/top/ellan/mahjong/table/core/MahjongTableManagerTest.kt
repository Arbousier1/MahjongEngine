package top.ellan.mahjong.table.core

import top.ellan.mahjong.model.SeatWind
import top.ellan.mahjong.render.display.DisplayClickAction
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MahjongTableManagerTest {
    @Test
    fun `same display action matches toggle ready duplicates`() {
        val left = DisplayClickAction.toggleReady("TABLE01", SeatWind.EAST)
        val right = DisplayClickAction.toggleReady("TABLE01", SeatWind.EAST)

        assertTrue(MahjongTableManager.sameDisplayAction(left, right))
    }

    @Test
    fun `different display action does not match different ready seats`() {
        val left = DisplayClickAction.toggleReady("TABLE01", SeatWind.EAST)
        val right = DisplayClickAction.toggleReady("TABLE01", SeatWind.SOUTH)

        assertFalse(MahjongTableManager.sameDisplayAction(left, right))
    }

    @Test
    fun `different player command payload does not match duplicate action`() {
        val owner = UUID.fromString("00000000-0000-0000-0000-000000000099")
        val left = DisplayClickAction.playerCommand("TABLE01", owner, "react:pon")
        val right = DisplayClickAction.playerCommand("TABLE01", owner, "react:skip")

        assertFalse(MahjongTableManager.sameDisplayAction(left, right))
    }

    @Test
    fun `nearby table centers overlap on the same floor`() {
        val world = mock(World::class.java)
        val existing = Location(world, 10.5, 64.5, 10.5)
        val candidate = Location(world, 15.9, 64.5, 10.5)

        assertTrue(MahjongTableManager.isOverlappingTableCenter(existing, candidate))
    }

    @Test
    fun `distant or vertically separated table centers do not overlap`() {
        val world = mock(World::class.java)
        val existing = Location(world, 10.5, 64.5, 10.5)
        val distant = Location(world, 16.1, 64.5, 10.5)
        val upperFloor = Location(world, 10.5, 69.0, 10.5)

        assertFalse(MahjongTableManager.isOverlappingTableCenter(existing, distant))
        assertFalse(MahjongTableManager.isOverlappingTableCenter(existing, upperFloor))
    }

    @Test
    fun `passable clearance allows table placement`() {
        val world = mock(World::class.java)
        val passableBlock = mock(Block::class.java)
        `when`(world.minHeight).thenReturn(0)
        `when`(world.maxHeight).thenReturn(320)
        `when`(passableBlock.isPassable).thenReturn(true)
        `when`(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(passableBlock)

        assertNull(MahjongTableManager.firstBlockedTableSpace(Location(world, 10.5, 64.5, 10.5)))
    }

    @Test
    fun `blocked clearance reports obstruction coordinates`() {
        val world = mock(World::class.java)
        val passableBlock = mock(Block::class.java)
        val blockedBlock = mock(Block::class.java)
        `when`(world.minHeight).thenReturn(0)
        `when`(world.maxHeight).thenReturn(320)
        `when`(passableBlock.isPassable).thenReturn(true)
        `when`(blockedBlock.isPassable).thenReturn(false)
        `when`(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer { invocation ->
            val x = invocation.getArgument<Int>(0)
            val y = invocation.getArgument<Int>(1)
            val z = invocation.getArgument<Int>(2)
            if (x == 8 && y == 65 && z == 11) blockedBlock else passableBlock
        }

        val failure = MahjongTableManager.firstBlockedTableSpace(Location(world, 10.5, 64.5, 10.5))

        assertEquals(MahjongTableManager.CreateTableFailureReason.BLOCKED_SPACE, failure?.reason())
        assertEquals(8, failure?.x())
        assertEquals(65, failure?.y())
        assertEquals(11, failure?.z())
    }

    @Test
    fun `same seat join action is treated as an existing binding`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000021")
        val session = mock(MahjongTableSession::class.java)
        val action = DisplayClickAction.joinSeat("TABLE01", SeatWind.SOUTH)

        `when`(session.id()).thenReturn("TABLE01")
        `when`(session.seatOf(playerId)).thenReturn(SeatWind.SOUTH)

        assertTrue(MahjongTableManager.isSameSeatJoin(session, playerId, action))
    }

    @Test
    fun `different seat join action is not treated as an existing binding`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000022")
        val session = mock(MahjongTableSession::class.java)
        val action = DisplayClickAction.joinSeat("TABLE01", SeatWind.WEST)

        `when`(session.id()).thenReturn("TABLE01")
        `when`(session.seatOf(playerId)).thenReturn(SeatWind.NORTH)

        assertFalse(MahjongTableManager.isSameSeatJoin(session, playerId, action))
    }
}

