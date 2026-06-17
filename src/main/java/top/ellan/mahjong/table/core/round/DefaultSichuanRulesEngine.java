package top.ellan.mahjong.table.core.round;

import top.ellan.mahjong.gb.jni.GbFanEntry;
import top.ellan.mahjong.gb.jni.GbScoreDelta;
import top.ellan.mahjong.gb.jni.GbTingCandidate;
import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.SeatWind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DefaultSichuanRulesEngine implements SichuanRulesEngine {
    private static final String SICHUAN_PING_HU = "PING_HU";
    private static final String SICHUAN_DUI_DUI_HU = "DUI_DUI_HU";
    private static final String SICHUAN_QING_YI_SE = "QING_YI_SE";
    private static final String SICHUAN_QI_DUI = "QI_DUI";
    private static final String SICHUAN_LONG_QI_DUI = "LONG_QI_DUI";
    private static final String SICHUAN_SHUANG_LONG_QI_DUI = "SHUANG_LONG_QI_DUI";
    private static final String SICHUAN_HAO_HUA_LONG_QI_DUI = "HAO_HUA_LONG_QI_DUI";
    private static final String SICHUAN_JIANG_DUI = "JIANG_DUI";
    private static final String SICHUAN_GEN = "GEN";
    private static final String SICHUAN_JIN_GOU_DIAO = "JIN_GOU_DIAO";
    private static final String SICHUAN_HAI_DI = "HAI_DI";
    private static final String SICHUAN_MIAO_SHOU_HUI_CHUN = "MIAO_SHOU_HUI_CHUN";
    private static final String SICHUAN_GANG_SHANG_HUA = "GANG_SHANG_HUA";
    private static final String SICHUAN_GANG_SHANG_PAO = "GANG_SHANG_PAO";
    private static final String SICHUAN_QIANG_GANG_HU = "QIANG_GANG_HU";
    private static final List<MahjongTile> SICHUAN_TILES = List.of(
        MahjongTile.M1, MahjongTile.M2, MahjongTile.M3, MahjongTile.M4, MahjongTile.M5, MahjongTile.M6, MahjongTile.M7, MahjongTile.M8, MahjongTile.M9,
        MahjongTile.P1, MahjongTile.P2, MahjongTile.P3, MahjongTile.P4, MahjongTile.P5, MahjongTile.P6, MahjongTile.P7, MahjongTile.P8, MahjongTile.P9,
        MahjongTile.S1, MahjongTile.S2, MahjongTile.S3, MahjongTile.S4, MahjongTile.S5, MahjongTile.S6, MahjongTile.S7, MahjongTile.S8, MahjongTile.S9
    );

    @Override
    public FanResult evaluateFan(
        List<MahjongTile> concealedHand,
        List<? extends GbNativeRequestFactory.MeldStateView> melds,
        MahjongTile winningTile,
        String winType,
        List<String> flags,
        boolean goldenSingleWait
    ) {
        int fixedMeldCount = melds == null ? 0 : melds.size();
        SichuanHuEvaluator.Result result = SichuanHuEvaluator.evaluate(concealedHand, winningTile, fixedMeldCount);
        if (!result.valid()) {
            return new FanResult(false, 0, List.of(), "Hand is not a valid Sichuan Mahjong winning hand.");
        }
        List<MahjongTile> totalTiles = fullHandTiles(concealedHand, melds, winningTile);
        if (!isMissingOneSuit(totalTiles)) {
            return new FanResult(false, 0, List.of(), "Sichuan Mahjong hand must be missing one suit.");
        }
        List<GbFanEntry> fans = new ArrayList<>();
        boolean allTriplets = isAllTriplets(result, melds);
        int roots = rootCount(totalTiles);
        boolean is258 = isAll258(totalTiles);
        if (result.sevenPairs()) {
            // Seven pairs is scored as a single layered fan that already folds in any
            // four-of-a-kind "roots", so it must not also receive a separate GEN entry.
            fans.add(sevenPairsFan(roots));
        } else {
            if (allTriplets && is258) {
                // Jiang dui is a standalone three-fan hand, not dui dui hu plus a bonus.
                fans.add(new GbFanEntry(SICHUAN_JIANG_DUI, 3, 1));
            } else if (allTriplets) {
                fans.add(new GbFanEntry(SICHUAN_DUI_DUI_HU, 1, 1));
            } else {
                fans.add(new GbFanEntry(SICHUAN_PING_HU, 1, 1));
            }
            if (allTriplets && goldenSingleWait) {
                fans.add(new GbFanEntry(SICHUAN_JIN_GOU_DIAO, 1, 1));
            }
            if (roots > 0) {
                fans.add(new GbFanEntry(SICHUAN_GEN, 1, roots));
            }
        }
        if (isSingleSuit(totalTiles)) {
            fans.add(new GbFanEntry(SICHUAN_QING_YI_SE, 2, 1));
        }
        if ("SELF_DRAW".equals(winType) && flags.contains("LAST_TILE")) {
            fans.add(new GbFanEntry(SICHUAN_HAI_DI, 1, 1));
        }
        if ("DISCARD".equals(winType) && flags.contains("LAST_TILE")) {
            fans.add(new GbFanEntry(SICHUAN_MIAO_SHOU_HUI_CHUN, 1, 1));
        }
        if ("SELF_DRAW".equals(winType) && flags.contains("AFTER_KONG")) {
            fans.add(new GbFanEntry(SICHUAN_GANG_SHANG_HUA, 1, 1));
        }
        if ("DISCARD".equals(winType) && flags.contains("AFTER_KONG")) {
            fans.add(new GbFanEntry(SICHUAN_GANG_SHANG_PAO, 1, 1));
        }
        if (flags.contains("ROBBING_KONG")) {
            fans.add(new GbFanEntry(SICHUAN_QIANG_GANG_HU, 1, 1));
        }
        int totalFan = Math.max(1, totalFan(fans));
        return new FanResult(true, totalFan, List.copyOf(fans), null);
    }

    @Override
    public List<MahjongTile> waitingTiles(List<MahjongTile> concealedHand, int fixedMeldCount) {
        return SichuanHuEvaluator.waitingTiles(concealedHand, fixedMeldCount);
    }

    @Override
    public boolean isMissingOneSuit(List<MahjongTile> tiles) {
        Set<Character> suits = new HashSet<>();
        for (MahjongTile tile : tiles == null ? List.<MahjongTile>of() : tiles) {
            MahjongTile normalized = normalize(tile);
            if (normalized == null || normalized == MahjongTile.UNKNOWN || normalized.isFlower() || GbRoundSupport.isHonor(normalized)) {
                continue;
            }
            suits.add(normalized.name().charAt(0));
        }
        return !suits.isEmpty() && suits.size() <= 2;
    }

    @Override
    public int scoreUnit(int fan) {
        int normalizedFan = Math.max(1, fan);
        if (normalizedFan >= Integer.SIZE) {
            return Integer.MAX_VALUE;
        }
        return 1 << (normalizedFan - 1);
    }

    @Override
    public int bestReadyUnit(List<GbTingCandidate> waits) {
        if (waits == null || waits.isEmpty()) {
            return 1;
        }
        int bestFan = 1;
        for (GbTingCandidate candidate : waits) {
            bestFan = Math.max(bestFan, Math.max(1, candidate.getTotalFan() == null ? 1 : candidate.getTotalFan()));
        }
        return scoreUnit(bestFan);
    }

    @Override
    public List<GbScoreDelta> winDeltas(SeatWind winnerSeat, SeatWind discarderSeat, String winType, int scoreUnit, List<SeatWind> activeOpponentSeats) {
        if (winnerSeat == null) {
            return List.of();
        }
        int unit = Math.max(1, scoreUnit);
        if ("SELF_DRAW".equals(winType)) {
            List<GbScoreDelta> deltas = new ArrayList<>();
            int loserCount = 0;
            for (SeatWind seat : activeOpponentSeats == null ? List.<SeatWind>of() : activeOpponentSeats) {
                if (seat == null) {
                    continue;
                }
                deltas.add(new GbScoreDelta(seat.name(), -unit));
                loserCount++;
            }
            deltas.add(new GbScoreDelta(winnerSeat.name(), unit * loserCount));
            return List.copyOf(deltas);
        }
        if (discarderSeat == null) {
            return List.of();
        }
        return List.of(new GbScoreDelta(discarderSeat.name(), -unit), new GbScoreDelta(winnerSeat.name(), unit));
    }

    @Override
    public List<GbScoreDelta> kanDeltas(SeatWind winnerSeat, List<SeatWind> payerSeats, int unit) {
        if (winnerSeat == null || unit <= 0 || payerSeats == null || payerSeats.isEmpty()) {
            return List.of();
        }
        List<GbScoreDelta> deltas = new ArrayList<>();
        int collected = 0;
        for (SeatWind seat : payerSeats) {
            if (seat == null || seat == winnerSeat) {
                continue;
            }
            deltas.add(new GbScoreDelta(seat.name(), -unit));
            collected += unit;
        }
        if (collected > 0) {
            deltas.add(new GbScoreDelta(winnerSeat.name(), collected));
        }
        return List.copyOf(deltas);
    }

    @Override
    public List<GbScoreDelta> exhaustiveDrawDeltas(
        List<SeatWind> activeSeats,
        Map<SeatWind, List<MahjongTile>> hands,
        Set<SeatWind> readySeats,
        Map<SeatWind, Integer> readyUnits,
        int huaZhuUnit
    ) {
        if (activeSeats == null || activeSeats.isEmpty()) {
            return List.of();
        }
        List<GbScoreDelta> deltas = new ArrayList<>();
        List<SeatWind> huaZhuSeats = new ArrayList<>();
        for (SeatWind seat : activeSeats) {
            List<MahjongTile> hand = hands == null ? List.of() : hands.getOrDefault(seat, List.of());
            if (!isMissingOneSuit(hand)) {
                huaZhuSeats.add(seat);
            }
        }
        for (SeatWind huaZhu : huaZhuSeats) {
            for (SeatWind receiver : activeSeats) {
                if (receiver == null || receiver == huaZhu) {
                    continue;
                }
                deltas.add(new GbScoreDelta(huaZhu.name(), -huaZhuUnit));
                deltas.add(new GbScoreDelta(receiver.name(), huaZhuUnit));
            }
        }
        for (SeatWind payer : activeSeats) {
            if (payer == null || huaZhuSeats.contains(payer) || (readySeats != null && readySeats.contains(payer))) {
                continue;
            }
            for (SeatWind receiver : readySeats == null ? Set.<SeatWind>of() : readySeats) {
                int unit = readyUnits == null ? 1 : Math.max(1, readyUnits.getOrDefault(receiver, 1));
                deltas.add(new GbScoreDelta(payer.name(), -unit));
                deltas.add(new GbScoreDelta(receiver.name(), unit));
            }
        }
        return List.copyOf(deltas);
    }

    private static boolean isAllTriplets(SichuanHuEvaluator.Result result, List<? extends GbNativeRequestFactory.MeldStateView> melds) {
        if (result.sevenPairs()) {
            return false;
        }
        boolean concealedTripletsOnly = result.concealedMelds().stream().noneMatch(shape -> shape == SichuanHuEvaluator.MeldShape.SEQUENCE);
        boolean openTripletsOnly = melds == null || melds.stream().noneMatch(meld -> "CHOW".equals(meld.nativeType()));
        return concealedTripletsOnly && openTripletsOnly;
    }

    private static GbFanEntry sevenPairsFan(int roots) {
        // Each four-of-a-kind counts as one "root", upgrading the seven-pair tier:
        // 0 roots -> seven pairs (2), 1 -> dragon (3), 2 -> double dragon (4), 3 -> deluxe (5).
        return switch (roots) {
            case 0 -> new GbFanEntry(SICHUAN_QI_DUI, 2, 1);
            case 1 -> new GbFanEntry(SICHUAN_LONG_QI_DUI, 3, 1);
            case 2 -> new GbFanEntry(SICHUAN_SHUANG_LONG_QI_DUI, 4, 1);
            default -> new GbFanEntry(SICHUAN_HAO_HUA_LONG_QI_DUI, 5, 1);
        };
    }

    private static int totalFan(List<GbFanEntry> fans) {
        return fans.stream().mapToInt(fan -> fan.getFan() * Math.max(1, fan.getCount())).sum();
    }

    private static List<MahjongTile> fullHandTiles(
        List<MahjongTile> concealedHand,
        List<? extends GbNativeRequestFactory.MeldStateView> melds,
        MahjongTile winningTile
    ) {
        List<MahjongTile> totalTiles = new ArrayList<>(concealedHand == null ? List.of() : concealedHand);
        if (winningTile != null) {
            totalTiles.add(winningTile);
        }
        for (GbNativeRequestFactory.MeldStateView meld : melds == null ? List.<GbNativeRequestFactory.MeldStateView>of() : melds) {
            totalTiles.addAll(meld.tiles());
        }
        return totalTiles;
    }

    private static boolean isSingleSuit(List<MahjongTile> tiles) {
        return suitSet(tiles).size() == 1;
    }

    private static boolean isAll258(List<MahjongTile> tiles) {
        for (MahjongTile tile : tiles == null ? List.<MahjongTile>of() : tiles) {
            MahjongTile normalized = normalize(tile);
            if (normalized == null || normalized.isFlower() || GbRoundSupport.isHonor(normalized)) {
                return false;
            }
            int number = GbRoundSupport.tileNumber(normalized);
            if (number != 2 && number != 5 && number != 8) {
                return false;
            }
        }
        return tiles != null && !tiles.isEmpty();
    }

    private static int rootCount(List<MahjongTile> tiles) {
        EnumMap<MahjongTile, Integer> counts = new EnumMap<>(MahjongTile.class);
        for (MahjongTile tile : tiles == null ? List.<MahjongTile>of() : tiles) {
            MahjongTile normalized = normalize(tile);
            if (normalized == null || normalized == MahjongTile.UNKNOWN || normalized.isFlower() || GbRoundSupport.isHonor(normalized)) {
                continue;
            }
            counts.merge(normalized, 1, Integer::sum);
        }
        int roots = 0;
        for (int count : counts.values()) {
            roots += count / 4;
        }
        return roots;
    }

    private static Set<Character> suitSet(List<MahjongTile> tiles) {
        Set<Character> suits = new HashSet<>();
        for (MahjongTile tile : tiles == null ? List.<MahjongTile>of() : tiles) {
            MahjongTile normalized = normalize(tile);
            if (normalized == null || normalized == MahjongTile.UNKNOWN || normalized.isFlower() || GbRoundSupport.isHonor(normalized)) {
                continue;
            }
            suits.add(normalized.name().charAt(0));
        }
        return suits;
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
