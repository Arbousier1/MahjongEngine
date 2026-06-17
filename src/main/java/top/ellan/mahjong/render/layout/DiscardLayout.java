package top.ellan.mahjong.render.layout;

import top.ellan.mahjong.model.SeatWind;

public final class DiscardLayout {
    private DiscardLayout() {
    }

    public static double discardFootprint(double tileWidth, double tileHeight, boolean riichiTile) {
        return riichiTile ? tileHeight : tileWidth;
    }

    public static double rowFootprint(double tileWidth, double tileHeight, double tilePadding, int rowStart, int rowSize, int riichiDiscardIndex) {
        double total = 0.0D;
        for (int i = 0; i < rowSize; i++) {
            total += discardFootprint(tileWidth, tileHeight, rowStart + i == riichiDiscardIndex);
        }
        total += Math.max(0, rowSize - 1) * tilePadding;
        return total;
    }

    public static float discardYaw(SeatWind wind, boolean riichiTile) {
        return riichiTile ? seatYaw(wind) - 90.0F : seatYaw(wind);
    }

    public static float seatYaw(SeatWind wind) {
        return switch (displayDirection(wind)) {
            case EAST -> -90.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
        };
    }

    private static SeatWind displayDirection(SeatWind wind) {
        return switch (wind) {
            case EAST -> SeatWind.EAST;
            case SOUTH -> SeatWind.NORTH;
            case WEST -> SeatWind.WEST;
            case NORTH -> SeatWind.SOUTH;
        };
    }
}

