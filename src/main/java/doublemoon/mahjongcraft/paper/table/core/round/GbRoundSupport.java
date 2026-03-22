package doublemoon.mahjongcraft.paper.table.core.round;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

final class GbRoundSupport {
    private GbRoundSupport() {
    }

    static String relationLabel(SeatWind claimant, SeatWind source) {
        int diff = Math.floorMod(source.index() - claimant.index(), SeatWind.values().length);
        return switch (diff) {
            case 1 -> "LEFT";
            case 2 -> "ACROSS";
            case 3 -> "RIGHT";
            default -> "SELF";
        };
    }

    static boolean canChii(SeatWind candidate, SeatWind discarder) {
        return candidate == SeatWind.fromIndex(Math.floorMod(discarder.index() + 1, SeatWind.values().length));
    }

    static List<SeatWind> orderedAfter(SeatWind start) {
        List<SeatWind> winds = new ArrayList<>(SeatWind.values().length - 1);
        for (int offset = 1; offset < SeatWind.values().length; offset++) {
            winds.add(SeatWind.fromIndex(Math.floorMod(start.index() + offset, SeatWind.values().length)));
        }
        return List.copyOf(winds);
    }

    static String normalizeTileToken(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String upper = trimmed.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            MahjongTile.valueOf(upper);
            return upper;
        } catch (IllegalArgumentException ignored) {
            // Continue to shorthand parsing below.
        }

        String compact = trimmed.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (compact.length() < 2 || compact.length() > 3) {
            return upper;
        }
        char first = compact.charAt(0);
        char second = compact.charAt(1);
        Character suit = null;
        Character number = null;
        if (isSuit(first) && Character.isDigit(second)) {
            suit = first;
            number = second;
        } else if (Character.isDigit(first) && isSuit(second)) {
            number = first;
            suit = second;
        }
        if (suit == null || number == null) {
            return upper;
        }
        boolean red = compact.endsWith("r");
        int numeric = number == '0' ? 5 : Character.digit(number, 10);
        if (numeric < 1 || numeric > 9) {
            return upper;
        }
        return ("" + Character.toUpperCase(suit) + numeric) + (red || number == '0' ? "_RED" : "");
    }

    private static boolean isSuit(char value) {
        return value == 'm' || value == 'p' || value == 's';
    }

    static boolean containsTile(List<MahjongTile> hand, MahjongTile target) {
        return countMatchingTiles(hand, target) > 0;
    }

    static int countMatchingTiles(List<MahjongTile> hand, MahjongTile target) {
        if (hand == null || target == null) {
            return 0;
        }
        int count = 0;
        for (MahjongTile tile : hand) {
            if (sameKind(tile, target)) {
                count++;
            }
        }
        return count;
    }

    static void removeTiles(List<MahjongTile> hand, MahjongTile target, int amount) {
        int remaining = amount;
        for (int i = hand.size() - 1; i >= 0 && remaining > 0; i--) {
            if (sameKind(hand.get(i), target)) {
                hand.remove(i);
                remaining--;
            }
        }
    }

    static boolean sameKind(MahjongTile left, MahjongTile right) {
        if (left == null || right == null) {
            return false;
        }
        MahjongTile leftBase = left.isRedFive() ? MahjongTile.valueOf(left.name().replace("_RED", "")) : left;
        MahjongTile rightBase = right.isRedFive() ? MahjongTile.valueOf(right.name().replace("_RED", "")) : right;
        return leftBase == rightBase;
    }

    static boolean isHonor(MahjongTile tile) {
        return tile.ordinal() >= MahjongTile.EAST.ordinal() && tile.ordinal() <= MahjongTile.RED_DRAGON.ordinal();
    }

    static int tileNumber(MahjongTile tile) {
        if (tile == null || tile.isFlower() || isHonor(tile)) {
            return 0;
        }
        return Integer.parseInt(tile.name().substring(1, 2));
    }

    static MahjongTile offsetTile(MahjongTile tile, int delta) {
        if (tile == null || isHonor(tile) || tile.isFlower()) {
            return MahjongTile.UNKNOWN;
        }
        char suit = tile.name().charAt(0);
        int number = tileNumber(tile) + delta;
        if (number < 1 || number > 9) {
            return MahjongTile.UNKNOWN;
        }
        return MahjongTile.valueOf("" + suit + number);
    }

    static doublemoon.mahjongcraft.paper.riichi.model.MahjongTile toRiichiTile(MahjongTile tile) {
        return doublemoon.mahjongcraft.paper.riichi.model.MahjongTile.valueOf(tile.name());
    }

    static List<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> toRiichiTiles(List<MahjongTile> tiles) {
        List<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> converted = new ArrayList<>(tiles.size());
        for (MahjongTile tile : tiles) {
            converted.add(toRiichiTile(tile));
        }
        return List.copyOf(converted);
    }

    static int requireValidDicePoints(int value) {
        if (value < 2 || value > 12) {
            throw new IllegalStateException("Dice points must be between 2 and 12 but was " + value);
        }
        return value;
    }

    static int rollDicePoints() {
        return ThreadLocalRandom.current().nextInt(1, 7) + ThreadLocalRandom.current().nextInt(1, 7);
    }

    static List<MahjongTile> buildWall() {
        List<MahjongTile> wall = new ArrayList<>(144);
        for (MahjongTile tile : MahjongTile.values()) {
            if (tile == MahjongTile.UNKNOWN || tile.isRedFive()) {
                continue;
            }
            int copies = tile.isFlower() ? 1 : 4;
            for (int i = 0; i < copies; i++) {
                wall.add(tile);
            }
        }
        Collections.shuffle(wall);
        return List.copyOf(wall);
    }

    static List<MahjongTile> reorderWallForDice(List<MahjongTile> wall, int dicePoints, int roundIndex) {
        if (wall == null || wall.isEmpty()) {
            return List.of();
        }
        int seatCount = SeatWind.values().length;
        int wallTilesPerSide = wall.size() / seatCount;
        int directionIndex = seatCount - (((dicePoints % seatCount) - 1 + roundIndex) % seatCount);
        int startingStackIndex = 2 * dicePoints;
        List<MahjongTile> reordered = new ArrayList<>(wall.size());
        for (int i = 0; i < wall.size(); i++) {
            int tileIndex = Math.floorMod(directionIndex * wallTilesPerSide + startingStackIndex + i, wall.size());
            reordered.add(wall.get(tileIndex));
        }
        return List.copyOf(reordered);
    }
}
