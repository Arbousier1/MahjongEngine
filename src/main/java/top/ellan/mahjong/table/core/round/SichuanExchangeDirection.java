package top.ellan.mahjong.table.core.round;

import top.ellan.mahjong.model.SeatWind;

enum SichuanExchangeDirection {
    CLOCKWISE {
        @Override
        SeatWind targetOf(SeatWind source) {
            return SeatWind.fromIndex(Math.floorMod(source.index() + 1, SeatWind.values().length));
        }
    },
    COUNTERCLOCKWISE {
        @Override
        SeatWind targetOf(SeatWind source) {
            return SeatWind.fromIndex(Math.floorMod(source.index() - 1, SeatWind.values().length));
        }
    },
    ACROSS {
        @Override
        SeatWind targetOf(SeatWind source) {
            return SeatWind.fromIndex(Math.floorMod(source.index() + 2, SeatWind.values().length));
        }
    };

    abstract SeatWind targetOf(SeatWind source);

    static SichuanExchangeDirection fromDicePoints(int dicePoints) {
        int mod = Math.floorMod(dicePoints, 3);
        return switch (mod) {
            case 1 -> CLOCKWISE;
            case 2 -> COUNTERCLOCKWISE;
            default -> ACROSS;
        };
    }
}
