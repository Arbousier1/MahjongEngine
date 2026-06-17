package top.ellan.mahjong.table.runtime;

import java.util.Set;
import java.util.UUID;
import org.bukkit.World;

final class TableAreaChunks {
    private TableAreaChunks() {
    }

    static boolean allLoaded(UUID worldId, int centerChunkX, int centerChunkZ, Set<ChunkNeighborhood.ChunkKey> loadedChunks) {
        if (worldId == null || loadedChunks == null) {
            return false;
        }
        return loadedChunks.containsAll(ChunkNeighborhood.around(worldId, centerChunkX, centerChunkZ));
    }

    static boolean allLoaded(World world, int centerChunkX, int centerChunkZ) {
        if (world == null) {
            return false;
        }
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (!world.isChunkLoaded(centerChunkX + offsetX, centerChunkZ + offsetZ)) {
                    return false;
                }
            }
        }
        return true;
    }
}


