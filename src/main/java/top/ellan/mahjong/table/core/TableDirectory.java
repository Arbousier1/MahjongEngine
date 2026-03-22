package top.ellan.mahjong.table.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.Location;

final class TableDirectory {
    private final Map<String, MahjongTableSession> tables = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerTables = new ConcurrentHashMap<>();
    private final Map<UUID, String> spectatorTables = new ConcurrentHashMap<>();
    private final Map<TableChunkKey, Set<String>> tablesByChunk = new ConcurrentHashMap<>();

    MahjongTableSession resolveTable(String tableId) {
        if (tableId == null) {
            return null;
        }
        return this.tables.get(tableId.toUpperCase(Locale.ROOT));
    }

    MahjongTableSession tableFor(UUID playerId) {
        String tableId = this.playerTables.get(playerId);
        return tableId == null ? null : this.tables.get(tableId);
    }

    MahjongTableSession sessionForViewer(UUID playerId) {
        MahjongTableSession seated = this.tableFor(playerId);
        if (seated != null) {
            return seated;
        }
        String tableId = this.spectatorTables.get(playerId);
        return tableId == null ? null : this.tables.get(tableId);
    }

    boolean isSpectating(UUID playerId) {
        return this.spectatorTables.containsKey(playerId);
    }

    boolean isViewingAnyTable(UUID playerId) {
        return this.playerTables.containsKey(playerId) || this.spectatorTables.containsKey(playerId);
    }

    Collection<MahjongTableSession> tables() {
        return List.copyOf(this.tables.values());
    }

    Collection<String> tableIds() {
        return new ArrayList<>(this.tables.keySet());
    }

    boolean containsTableId(String tableId) {
        return this.tables.containsKey(tableId);
    }

    void assignPlayer(UUID playerId, String tableId) {
        this.playerTables.put(playerId, tableId);
    }

    void removePlayer(UUID playerId) {
        this.playerTables.remove(playerId);
    }

    void assignSpectator(UUID playerId, String tableId) {
        this.spectatorTables.put(playerId, tableId);
    }

    String removeSpectator(UUID playerId) {
        return this.spectatorTables.remove(playerId);
    }

    void registerTable(MahjongTableSession session) {
        this.tables.put(session.id(), session);
        this.indexTable(session);
    }

    void removeTable(MahjongTableSession session) {
        this.unindexTable(session);
        this.tables.remove(session.id());
    }

    void clear() {
        this.tables.clear();
        this.playerTables.clear();
        this.spectatorTables.clear();
        this.tablesByChunk.clear();
    }

    Set<String> tableIdsNearChunk(Chunk chunk) {
        Set<String> candidates = new HashSet<>();
        UUID worldId = chunk.getWorld().getUID();
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                Set<String> ids = this.tablesByChunk.get(new TableChunkKey(worldId, chunk.getX() + offsetX, chunk.getZ() + offsetZ));
                if (ids != null) {
                    candidates.addAll(ids);
                }
            }
        }
        return candidates;
    }

    private void indexTable(MahjongTableSession session) {
        this.tablesByChunk.computeIfAbsent(TableChunkKey.from(session.center()), ignored -> ConcurrentHashMap.newKeySet()).add(session.id());
    }

    private void unindexTable(MahjongTableSession session) {
        Set<String> ids = this.tablesByChunk.get(TableChunkKey.from(session.center()));
        if (ids == null) {
            return;
        }
        ids.remove(session.id());
        if (ids.isEmpty()) {
            this.tablesByChunk.remove(TableChunkKey.from(session.center()));
        }
    }

    void removeSpectators(Collection<UUID> spectatorIds) {
        for (UUID spectatorId : spectatorIds) {
            this.spectatorTables.remove(spectatorId);
        }
    }

    private record TableChunkKey(UUID worldId, int chunkX, int chunkZ) {
        private static TableChunkKey from(Location location) {
            return new TableChunkKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
    }
}

