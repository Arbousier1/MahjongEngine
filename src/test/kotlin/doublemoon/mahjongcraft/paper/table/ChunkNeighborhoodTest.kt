package doublemoon.mahjongcraft.paper.table

import doublemoon.mahjongcraft.paper.table.runtime.ChunkNeighborhood
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkNeighborhoodTest {
    @Test
    fun `around includes exactly current and adjacent chunks`() {
        val worldId = UUID.fromString("00000000-0000-0000-0000-000000000123")

        val keys = ChunkNeighborhood.around(worldId, 10, -4)

        assertEquals(9, keys.size)
        assertTrue(ChunkNeighborhood.ChunkKey(worldId, 10, -4) in keys)
        assertTrue(ChunkNeighborhood.ChunkKey(worldId, 9, -5) in keys)
        assertTrue(ChunkNeighborhood.ChunkKey(worldId, 11, -3) in keys)
    }
}
