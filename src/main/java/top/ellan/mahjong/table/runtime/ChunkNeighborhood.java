package top.ellan.mahjong.table.runtime;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ChunkNeighborhood {
    private ChunkNeighborhood() {
    }

    public static Set<ChunkKey> around(UUID worldId, int chunkX, int chunkZ) {
        Set<ChunkKey> keys = new HashSet<>();
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                keys.add(new ChunkKey(worldId, chunkX + offsetX, chunkZ + offsetZ));
            }
        }
        return keys;
    }

    public record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    }
}


