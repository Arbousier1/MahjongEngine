package top.ellan.mahjong.render.display;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TableDisplayRegistry {
    private static final Map<Integer, DisplayClickAction> ACTIONS = new ConcurrentHashMap<>();

    private TableDisplayRegistry() {
    }

    public static void register(int entityId, DisplayClickAction action) {
        if (action == null) {
            return;
        }
        ACTIONS.put(entityId, action);
    }

    public static DisplayClickAction get(int entityId) {
        return ACTIONS.get(entityId);
    }

    public static void unregister(int entityId) {
        ACTIONS.remove(entityId);
    }

    public static void clear() {
        ACTIONS.clear();
    }
}

