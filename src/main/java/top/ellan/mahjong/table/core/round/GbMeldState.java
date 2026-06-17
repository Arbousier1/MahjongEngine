package top.ellan.mahjong.table.core.round;

import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.SeatWind;
import java.util.ArrayList;
import java.util.List;

record GbMeldState(
    GbMeldType type,
    List<MahjongTile> tiles,
    MahjongTile baseTile,
    MahjongTile claimedTile,
    SeatWind fromSeat,
    boolean open,
    int claimTileIndex,
    int claimYawOffset,
    MahjongTile addedKanTile
) implements GbNativeRequestFactory.MeldStateView {
    static GbMeldState chow(MahjongTile claim, MahjongTile first, MahjongTile second, SeatWind fromSeat) {
        List<MahjongTile> ordered = new ArrayList<>(List.of(claim, first, second));
        ordered.sort((left, right) -> Integer.compare(tileSort(left), tileSort(right)));
        int claimIndex = ordered.indexOf(claim);
        return new GbMeldState(GbMeldType.CHOW, List.copyOf(ordered), claim, claim, fromSeat, true, claimIndex, 90, null);
    }

    static GbMeldState pung(MahjongTile claim, SeatWind fromSeat, SeatWind selfSeat) {
        List<MahjongTile> ordered = List.of(claim, claim, claim);
        int claimIndex = claimTileIndex(fromSeat, selfSeat);
        return new GbMeldState(GbMeldType.PUNG, ordered, claim, claim, fromSeat, true, claimIndex, claimYaw(claimIndex), null);
    }

    static GbMeldState openKong(MahjongTile claim, SeatWind fromSeat, SeatWind selfSeat) {
        List<MahjongTile> ordered = List.of(claim, claim, claim, claim);
        int claimIndex = claimTileIndex(fromSeat, selfSeat);
        return new GbMeldState(GbMeldType.OPEN_KONG, ordered, claim, claim, fromSeat, true, claimIndex, claimYaw(claimIndex), null);
    }

    static GbMeldState ankan(MahjongTile tile) {
        List<MahjongTile> ordered = List.of(tile, tile, tile, tile);
        return new GbMeldState(GbMeldType.CONCEALED_KONG, ordered, tile, null, null, false, -1, 0, null);
    }

    GbMeldState toAddedKong(MahjongTile tile) {
        List<MahjongTile> ordered = new ArrayList<>(this.tiles);
        ordered.add(tile);
        return new GbMeldState(GbMeldType.ADDED_KONG, List.copyOf(ordered), this.baseTile, this.claimedTile, this.fromSeat, true, this.claimTileIndex, this.claimYawOffset, tile);
    }

    public String nativeType() {
        return switch (this.type) {
            case CHOW -> "CHOW";
            case PUNG -> "PUNG";
            case OPEN_KONG -> "OPEN_KONG";
            case CONCEALED_KONG -> "CONCEALED_KONG";
            case ADDED_KONG -> "ADDED_KONG";
        };
    }

    private static int claimTileIndex(SeatWind fromSeat, SeatWind selfSeat) {
        if (fromSeat == null || selfSeat == null) {
            return 1;
        }
        int diff = Math.floorMod(selfSeat.index() - fromSeat.index(), SeatWind.values().length);
        return switch (diff) {
            case 1 -> 0; // Left source -> left slot
            case 2 -> 1; // Across source -> middle slot
            case 3 -> 2; // Right source -> right slot
            default -> 1;
        };
    }

    private static int claimYaw(int claimTileIndex) {
        return claimTileIndex == 0 ? -90 : 90;
    }

    private static int tileSort(MahjongTile tile) {
        return switch (tile) {
            case M1, P1, S1 -> 1;
            case M2, P2, S2 -> 2;
            case M3, P3, S3 -> 3;
            case M4, P4, S4 -> 4;
            case M5, P5, S5, M5_RED, P5_RED, S5_RED -> 5;
            case M6, P6, S6 -> 6;
            case M7, P7, S7 -> 7;
            case M8, P8, S8 -> 8;
            case M9, P9, S9 -> 9;
            default -> 100 + tile.ordinal();
        };
    }
}
