package top.ellan.mahjong.model;

public enum SeatWind {
    EAST(0, "seat.wind.east"),
    SOUTH(1, "seat.wind.south"),
    WEST(2, "seat.wind.west"),
    NORTH(3, "seat.wind.north");

    private final int index;
    private final String translationKey;

    SeatWind(int index, String translationKey) {
        this.index = index;
        this.translationKey = translationKey;
    }

    public int index() {
        return this.index;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public static SeatWind fromIndex(int index) {
        for (SeatWind seatWind : values()) {
            if (seatWind.index == index) {
                return seatWind;
            }
        }
        throw new IllegalArgumentException("Unknown seat index: " + index);
    }
}

