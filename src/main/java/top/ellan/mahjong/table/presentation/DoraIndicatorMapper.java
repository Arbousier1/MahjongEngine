package top.ellan.mahjong.table.presentation;

import top.ellan.mahjong.model.MahjongTile;

public final class DoraIndicatorMapper {
    private DoraIndicatorMapper() {
    }

    public static MahjongTile doraFromIndicator(MahjongTile indicator) {
        if (indicator == null) {
            return MahjongTile.UNKNOWN;
        }
        return switch (indicator) {
            case M1 -> MahjongTile.M2;
            case M2 -> MahjongTile.M3;
            case M3 -> MahjongTile.M4;
            case M4 -> MahjongTile.M5;
            case M5, M5_RED -> MahjongTile.M6;
            case M6 -> MahjongTile.M7;
            case M7 -> MahjongTile.M8;
            case M8 -> MahjongTile.M9;
            case M9 -> MahjongTile.M1;
            case P1 -> MahjongTile.P2;
            case P2 -> MahjongTile.P3;
            case P3 -> MahjongTile.P4;
            case P4 -> MahjongTile.P5;
            case P5, P5_RED -> MahjongTile.P6;
            case P6 -> MahjongTile.P7;
            case P7 -> MahjongTile.P8;
            case P8 -> MahjongTile.P9;
            case P9 -> MahjongTile.P1;
            case S1 -> MahjongTile.S2;
            case S2 -> MahjongTile.S3;
            case S3 -> MahjongTile.S4;
            case S4 -> MahjongTile.S5;
            case S5, S5_RED -> MahjongTile.S6;
            case S6 -> MahjongTile.S7;
            case S7 -> MahjongTile.S8;
            case S8 -> MahjongTile.S9;
            case S9 -> MahjongTile.S1;
            case EAST -> MahjongTile.SOUTH;
            case SOUTH -> MahjongTile.WEST;
            case WEST -> MahjongTile.NORTH;
            case NORTH -> MahjongTile.EAST;
            case WHITE_DRAGON -> MahjongTile.GREEN_DRAGON;
            case GREEN_DRAGON -> MahjongTile.RED_DRAGON;
            case RED_DRAGON -> MahjongTile.WHITE_DRAGON;
            default -> indicator;
        };
    }
}

