package doublemoon.mahjongcraft.paper.table;

import java.util.Set;
import java.util.UUID;

final class TableAreaChunks {
    private TableAreaChunks() {
    }

    static boolean allLoaded(UUID worldId, int centerChunkX, int centerChunkZ, Set<ChunkNeighborhood.ChunkKey> loadedChunks) {
        if (worldId == null || loadedChunks == null) {
            return false;
        }
        return loadedChunks.containsAll(ChunkNeighborhood.around(worldId, centerChunkX, centerChunkZ));
    }
}
