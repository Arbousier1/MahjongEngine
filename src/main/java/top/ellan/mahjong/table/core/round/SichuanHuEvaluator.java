package top.ellan.mahjong.table.core.round;

import top.ellan.mahjong.model.MahjongTile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SichuanHuEvaluator {
    private static final List<MahjongTile> SICHUAN_TILES = List.of(
        MahjongTile.M1, MahjongTile.M2, MahjongTile.M3, MahjongTile.M4, MahjongTile.M5, MahjongTile.M6, MahjongTile.M7, MahjongTile.M8, MahjongTile.M9,
        MahjongTile.P1, MahjongTile.P2, MahjongTile.P3, MahjongTile.P4, MahjongTile.P5, MahjongTile.P6, MahjongTile.P7, MahjongTile.P8, MahjongTile.P9,
        MahjongTile.S1, MahjongTile.S2, MahjongTile.S3, MahjongTile.S4, MahjongTile.S5, MahjongTile.S6, MahjongTile.S7, MahjongTile.S8, MahjongTile.S9
    );

    private SichuanHuEvaluator() {
    }

    static boolean canWin(List<MahjongTile> concealedHand, MahjongTile winningTile, int fixedMeldCount) {
        if (winningTile == null || concealedHand == null) {
            return false;
        }
        int requiredMelds = 4 - fixedMeldCount;
        if (requiredMelds < 0) {
            return false;
        }
        int expectedTileCount = requiredMelds * 3 + 2;
        if (concealedHand.size() + 1 != expectedTileCount) {
            return false;
        }
        List<MahjongTile> totalTiles = new ArrayList<>(concealedHand.size() + 1);
        totalTiles.addAll(concealedHand);
        totalTiles.add(winningTile);
        int[] counts = buildCounts(totalTiles);
        if (counts == null) {
            return false;
        }
        if (fixedMeldCount == 0 && totalTiles.size() == 14 && isSevenPairs(counts)) {
            return true;
        }
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] < 2) {
                continue;
            }
            counts[index] -= 2;
            if (canFormMelds(counts, requiredMelds, new HashMap<>())) {
                counts[index] += 2;
                return true;
            }
            counts[index] += 2;
        }
        return false;
    }

    static List<MahjongTile> waitingTiles(List<MahjongTile> concealedHand, int fixedMeldCount) {
        if (concealedHand == null) {
            return List.of();
        }
        int requiredMelds = 4 - fixedMeldCount;
        if (requiredMelds < 0) {
            return List.of();
        }
        int expectedConcealedCount = requiredMelds * 3 + 1;
        if (concealedHand.size() != expectedConcealedCount) {
            return List.of();
        }
        EnumSet<MahjongTile> waits = EnumSet.noneOf(MahjongTile.class);
        for (MahjongTile wait : SICHUAN_TILES) {
            if (canWin(concealedHand, wait, fixedMeldCount)) {
                waits.add(wait);
            }
        }
        return List.copyOf(waits);
    }

    private static int[] buildCounts(List<MahjongTile> tiles) {
        int[] counts = new int[SICHUAN_TILES.size()];
        for (MahjongTile tile : tiles) {
            MahjongTile base = normalize(tile);
            int index = tileIndex(base);
            if (index < 0) {
                return null;
            }
            counts[index]++;
        }
        return counts;
    }

    private static MahjongTile normalize(MahjongTile tile) {
        if (tile == null) {
            return MahjongTile.UNKNOWN;
        }
        if (!tile.isRedFive()) {
            return tile;
        }
        return switch (tile) {
            case M5_RED -> MahjongTile.M5;
            case P5_RED -> MahjongTile.P5;
            case S5_RED -> MahjongTile.S5;
            default -> tile;
        };
    }

    private static int tileIndex(MahjongTile tile) {
        if (tile == null || tile.isFlower() || GbRoundSupport.isHonor(tile)) {
            return -1;
        }
        String name = tile.name();
        if (name.length() < 2) {
            return -1;
        }
        char suit = name.charAt(0);
        int number = Character.digit(name.charAt(1), 10);
        if (number < 1 || number > 9) {
            return -1;
        }
        int suitOffset = switch (suit) {
            case 'M' -> 0;
            case 'P' -> 9;
            case 'S' -> 18;
            default -> -1;
        };
        return suitOffset < 0 ? -1 : suitOffset + (number - 1);
    }

    private static boolean isSevenPairs(int[] counts) {
        int pairs = 0;
        for (int count : counts) {
            if (count == 0) {
                continue;
            }
            if (count != 2 && count != 4) {
                return false;
            }
            pairs += count / 2;
        }
        return pairs == 7;
    }

    private static boolean canFormMelds(int[] counts, int requiredMelds, Map<String, Boolean> memo) {
        if (requiredMelds == 0) {
            for (int count : counts) {
                if (count != 0) {
                    return false;
                }
            }
            return true;
        }
        String key = encodeState(counts, requiredMelds);
        Boolean cached = memo.get(key);
        if (cached != null) {
            return cached;
        }
        int first = firstNonZero(counts);
        if (first < 0) {
            memo.put(key, false);
            return false;
        }
        if (counts[first] >= 3) {
            counts[first] -= 3;
            if (canFormMelds(counts, requiredMelds - 1, memo)) {
                counts[first] += 3;
                memo.put(key, true);
                return true;
            }
            counts[first] += 3;
        }
        int suitBase = (first / 9) * 9;
        int rank = first - suitBase;
        if (rank <= 6 && counts[first + 1] > 0 && counts[first + 2] > 0) {
            counts[first]--;
            counts[first + 1]--;
            counts[first + 2]--;
            if (canFormMelds(counts, requiredMelds - 1, memo)) {
                counts[first]++;
                counts[first + 1]++;
                counts[first + 2]++;
                memo.put(key, true);
                return true;
            }
            counts[first]++;
            counts[first + 1]++;
            counts[first + 2]++;
        }
        memo.put(key, false);
        return false;
    }

    private static int firstNonZero(int[] counts) {
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] > 0) {
                return index;
            }
        }
        return -1;
    }

    private static String encodeState(int[] counts, int requiredMelds) {
        StringBuilder builder = new StringBuilder(32);
        builder.append(requiredMelds).append(':');
        for (int count : counts) {
            builder.append((char) ('0' + count));
        }
        return builder.toString();
    }
}
