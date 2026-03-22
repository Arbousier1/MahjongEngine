package top.ellan.mahjong.compat;

import top.ellan.mahjong.model.MahjongTile;
import java.util.Locale;
import java.util.Objects;

public final class CraftEngineTileItemResolver {
    private CraftEngineTileItemResolver() {
    }

    public static String resolve(String itemIdPrefix, MahjongTile tile, boolean faceDown) {
        Objects.requireNonNull(itemIdPrefix, "itemIdPrefix");
        Objects.requireNonNull(tile, "tile");
        return itemIdPrefix + (faceDown ? "back" : tile.name().toLowerCase(Locale.ROOT));
    }
}

