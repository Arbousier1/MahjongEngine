package doublemoon.mahjongcraft.paper.render.display;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DisplayVisibilityRegistry {
    private static final Map<Integer, Set<UUID>> VIEWERS = new ConcurrentHashMap<>();

    private DisplayVisibilityRegistry() {
    }

    public static void registerPrivate(int entityId, Collection<UUID> viewers) {
        if (viewers == null || viewers.isEmpty()) {
            VIEWERS.remove(entityId);
            return;
        }
        VIEWERS.put(entityId, Set.copyOf(viewers));
    }

    public static void registerHidden(int entityId) {
        VIEWERS.put(entityId, Set.of());
    }

    public static boolean canView(int entityId, UUID viewerId) {
        Set<UUID> viewers = VIEWERS.get(entityId);
        return viewers == null || viewers.contains(viewerId);
    }

    public static boolean matches(int entityId, Collection<UUID> viewers) {
        Set<UUID> current = VIEWERS.get(entityId);
        if (viewers == null) {
            return current == null;
        }
        if (viewers.isEmpty()) {
            return current != null && current.isEmpty();
        }
        return current != null && current.equals(Set.copyOf(viewers));
    }

    public static void unregister(int entityId) {
        VIEWERS.remove(entityId);
    }

    public static void clear() {
        VIEWERS.clear();
    }
}
