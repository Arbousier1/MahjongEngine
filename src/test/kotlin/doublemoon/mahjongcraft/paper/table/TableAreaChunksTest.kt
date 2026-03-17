package doublemoon.mahjongcraft.paper.table.runtime

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableAreaChunksTest {
    @Test
    fun `allLoaded returns false when any adjacent chunk is missing`() {
        val worldId = UUID.fromString("00000000-0000-0000-0000-000000000321")
        val loaded = ChunkNeighborhood.around(worldId, 12, -7).toMutableSet()

        loaded.remove(ChunkNeighborhood.ChunkKey(worldId, 13, -6))

        assertFalse(TableAreaChunks.allLoaded(worldId, 12, -7, loaded))
    }

    @Test
    fun `allLoaded returns true when full three by three area is loaded`() {
        val worldId = UUID.fromString("00000000-0000-0000-0000-000000000321")
        val loaded = ChunkNeighborhood.around(worldId, 12, -7)

        assertTrue(TableAreaChunks.allLoaded(worldId, 12, -7, loaded))
    }
}
