package doublemoon.mahjongcraft.paper.gb.runtime;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import java.util.Locale;

public final class GbTileEncoding {
    private GbTileEncoding() {
    }

    public static String encode(MahjongTile tile) {
        if (tile == null) {
            throw new IllegalArgumentException("tile cannot be null");
        }
        MahjongTile base = switch (tile) {
            case M5_RED -> MahjongTile.M5;
            case P5_RED -> MahjongTile.P5;
            case S5_RED -> MahjongTile.S5;
            default -> tile;
        };
        return switch (base) {
            case M1, M2, M3, M4, M5, M6, M7, M8, M9 -> "W" + base.name().substring(1);
            case S1, S2, S3, S4, S5, S6, S7, S8, S9 -> "T" + base.name().substring(1);
            case P1, P2, P3, P4, P5, P6, P7, P8, P9 -> "B" + base.name().substring(1);
            case EAST -> "F1";
            case SOUTH -> "F2";
            case WEST -> "F3";
            case NORTH -> "F4";
            case WHITE_DRAGON -> "J1";
            case GREEN_DRAGON -> "J2";
            case RED_DRAGON -> "J3";
            case PLUM -> "a";
            case ORCHID -> "b";
            case BAMBOO -> "c";
            case CHRYSANTHEMUM -> "d";
            case SPRING -> "e";
            case SUMMER -> "f";
            case AUTUMN -> "g";
            case WINTER -> "h";
            default -> throw new IllegalArgumentException("Unsupported GB tile: " + tile.name().toLowerCase(Locale.ROOT));
        };
    }

    public static String encodeWind(SeatWind wind) {
        return switch (wind) {
            case EAST -> "EAST";
            case SOUTH -> "SOUTH";
            case WEST -> "WEST";
            case NORTH -> "NORTH";
        };
    }
}
