package top.ellan.mahjong.table.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class SessionHandSelectionCoordinator {
    private final SessionState session;
    private final Map<UUID, Integer> selectedHandTileIndices = new HashMap<>();

    SessionHandSelectionCoordinator(SessionState session) {
        this.session = session;
    }

    boolean clickHandTile(UUID playerId, int tileIndex, boolean cancelSelection) {
        if (!this.session.canSelectHandTileInternal(playerId, tileIndex)) {
            return false;
        }
        Integer selectedIndex = this.selectedHandTileIndices.get(playerId);
        if (selectedIndex != null && selectedIndex == tileIndex) {
            if (cancelSelection) {
                this.selectedHandTileIndices.remove(playerId);
                this.session.refreshSelectedHandTileViewInternal(playerId);
                return true;
            }
            return this.session.discard(playerId, tileIndex);
        }
        this.selectedHandTileIndices.put(playerId, tileIndex);
        this.session.refreshSelectedHandTileViewInternal(playerId);
        return true;
    }

    int selectedHandTileIndex(UUID playerId) {
        return this.selectedHandTileIndices.getOrDefault(playerId, -1);
    }

    void clearPlayer(UUID playerId) {
        this.selectedHandTileIndices.remove(playerId);
    }

    void clearAll() {
        this.selectedHandTileIndices.clear();
    }

    void pruneSelectedHandTiles() {
        this.selectedHandTileIndices.entrySet().removeIf(entry -> !this.session.canSelectHandTileInternal(entry.getKey(), entry.getValue()));
    }
}

