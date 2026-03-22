package top.ellan.mahjong.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum MahjongTile {
    M1,
    M2,
    M3,
    M4,
    M5,
    M5_RED,
    M6,
    M7,
    M8,
    M9,
    P1,
    P2,
    P3,
    P4,
    P5,
    P5_RED,
    P6,
    P7,
    P8,
    P9,
    S1,
    S2,
    S3,
    S4,
    S5,
    S5_RED,
    S6,
    S7,
    S8,
    S9,
    EAST,
    SOUTH,
    WEST,
    NORTH,
    WHITE_DRAGON,
    GREEN_DRAGON,
    RED_DRAGON,
    PLUM,
    ORCHID,
    BAMBOO,
    CHRYSANTHEMUM,
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER,
    UNKNOWN;

    public String itemModelPath() {
        return "mahjong_tile/" + this.name().toLowerCase();
    }

    public boolean isRedFive() {
        return this == M5_RED || this == P5_RED || this == S5_RED;
    }

    public boolean isFlower() {
        return switch (this) {
            case PLUM, ORCHID, BAMBOO, CHRYSANTHEMUM, SPRING, SUMMER, AUTUMN, WINTER -> true;
            default -> false;
        };
    }

    public static List<MahjongTile> createRiichiWall(boolean redFives) {
        List<MahjongTile> wall = new ArrayList<>(136);
        for (MahjongTile tile : values()) {
            if (tile.isRedFive() || tile.isFlower() || tile == UNKNOWN) {
                continue;
            }
            int copies = 4;
            if (redFives && (tile == M5 || tile == P5 || tile == S5)) {
                copies = 3;
            }
            for (int i = 0; i < copies; i++) {
                wall.add(tile);
            }
        }
        if (redFives) {
            wall.add(M5_RED);
            wall.add(P5_RED);
            wall.add(S5_RED);
        }
        Collections.shuffle(wall);
        return wall;
    }
}

