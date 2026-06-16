package top.ellan.mahjong.table.core.round;

import top.ellan.mahjong.model.MahjongTile;
import java.util.Locale;

enum SichuanSuit {
    WAN('M', "wan"),
    TONG('P', "tong"),
    SUO('S', "suo");

    private final char tilePrefix;
    private final String key;

    SichuanSuit(char tilePrefix, String key) {
        this.tilePrefix = tilePrefix;
        this.key = key;
    }

    public char tilePrefix() {
        return this.tilePrefix;
    }

    public String key() {
        return this.key;
    }

    public boolean matches(MahjongTile tile) {
        MahjongTile normalized = normalize(tile);
        return normalized != null
            && !normalized.isFlower()
            && !GbRoundSupport.isHonor(normalized)
            && normalized.name().charAt(0) == this.tilePrefix;
    }

    public static SichuanSuit fromTile(MahjongTile tile) {
        MahjongTile normalized = normalize(tile);
        if (normalized == null || normalized.isFlower() || GbRoundSupport.isHonor(normalized)) {
            return null;
        }
        return fromKey(String.valueOf(normalized.name().charAt(0)));
    }

    public static SichuanSuit fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "m", "man", "wan", "w" -> WAN;
            case "p", "pin", "tong", "dot", "t" -> TONG;
            case "s", "sou", "suo", "bamboo", "zhu" -> SUO;
            default -> null;
        };
    }

    private static MahjongTile normalize(MahjongTile tile) {
        if (tile == null) {
            return null;
        }
        return switch (tile) {
            case M5_RED -> MahjongTile.M5;
            case P5_RED -> MahjongTile.P5;
            case S5_RED -> MahjongTile.S5;
            default -> tile;
        };
    }
}
