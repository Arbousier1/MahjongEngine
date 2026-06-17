package top.ellan.mahjong.compat;

import top.ellan.mahjong.model.MahjongTile;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CraftEngineTileItemResolver {
    private static final ConcurrentMap<ResolveKey, String> CACHE = new ConcurrentHashMap<>();

    private CraftEngineTileItemResolver() {
    }

    public static String resolve(String itemIdPrefix, MahjongTile tile, boolean faceDown) {
        Objects.requireNonNull(itemIdPrefix, "itemIdPrefix");
        Objects.requireNonNull(tile, "tile");
        return CACHE.computeIfAbsent(
            new ResolveKey(itemIdPrefix, tile, faceDown),
            key -> key.prefix() + (key.faceDown() ? "back" : key.tile().name().toLowerCase(Locale.ROOT))
        );
    }

    private record ResolveKey(String prefix, MahjongTile tile, boolean faceDown) {
    }
}

