package top.ellan.mahjong.table.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class SessionViewerActionMenuCoordinator {
    private final Map<UUID, String> menuStates = new HashMap<>();

    String state(UUID viewerId) {
        return viewerId == null ? "" : Objects.toString(this.menuStates.get(viewerId), "");
    }

    boolean set(UUID viewerId, String menuState) {
        if (viewerId == null || menuState == null || menuState.isBlank()) {
            return false;
        }
        if (Objects.equals(this.menuStates.get(viewerId), menuState)) {
            return false;
        }
        this.menuStates.put(viewerId, menuState);
        return true;
    }

    boolean clear(UUID viewerId) {
        return viewerId != null && this.menuStates.remove(viewerId) != null;
    }

    void clearAll() {
        this.menuStates.clear();
    }
}
