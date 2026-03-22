package doublemoon.mahjongcraft.paper.table.core.round;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.ReactionType;
import doublemoon.mahjongcraft.paper.gb.jni.GbWinResponse;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import kotlin.Pair;

final class GbReactionResolver {
    @FunctionalInterface
    interface RonAvailability {
        boolean canRon(UUID playerId, SeatWind discarderSeat, MahjongTile discardedTile);
    }

    @FunctionalInterface
    interface ChiiPairsProvider {
        List<Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile>>
            availableChiiPairs(UUID playerId, MahjongTile discardedTile);
    }

    @FunctionalInterface
    interface RonWinEvaluator {
        ResolvedGbWin evaluate(UUID playerId, UUID discarderId, MahjongTile tile, List<String> flags);
    }

    static PendingReactionWindow buildPendingReactionWindow(
        UUID discarderId,
        MahjongTile discardedTile,
        SeatWind discarderSeat,
        EnumMap<SeatWind, UUID> seats,
        Map<UUID, List<MahjongTile>> hands,
        RonAvailability ronAvailability,
        ChiiPairsProvider chiiPairsProvider,
        List<String> flags
    ) {
        if (discarderId == null || discardedTile == null || discarderSeat == null) {
            return null;
        }
        LinkedHashMap<UUID, ReactionOptions> options = new LinkedHashMap<>();
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = seats.get(wind);
            if (playerId == null || playerId.equals(discarderId)) {
                continue;
            }
            boolean canRon = ronAvailability.canRon(playerId, discarderSeat, discardedTile);
            boolean canPon = GbRoundSupport.countMatchingTiles(hands.get(playerId), discardedTile) >= 2;
            boolean canMinkan = GbRoundSupport.countMatchingTiles(hands.get(playerId), discardedTile) >= 3;
            List<Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile>> chiiPairs =
                GbRoundSupport.canChii(wind, discarderSeat) ? chiiPairsProvider.availableChiiPairs(playerId, discardedTile) : List.of();
            if (canRon || canPon || canMinkan || !chiiPairs.isEmpty()) {
                options.put(playerId, new ReactionOptions(canRon, canPon, canMinkan, chiiPairs));
            }
        }
        return options.isEmpty()
            ? null
            : new PendingReactionWindow(
                discarderId,
                discardedTile,
                options,
                new HashMap<>(),
                List.copyOf(flags),
                false,
                null
            );
    }

    static Resolution resolvePendingReactions(
        PendingReactionWindow pending,
        SeatWind discarderSeat,
        Function<SeatWind, UUID> playerAt,
        RonWinEvaluator ronWinEvaluator
    ) {
        if (pending == null || discarderSeat == null || playerAt == null || ronWinEvaluator == null) {
            return Resolution.noop();
        }
        List<ResolvedGbWin> wins = new ArrayList<>();
        for (SeatWind wind : GbRoundSupport.orderedAfter(discarderSeat)) {
            UUID playerId = playerAt.apply(wind);
            if (playerId == null) {
                continue;
            }
            ReactionResponse response = pending.responses().get(playerId);
            if (response != null && response.getType() == ReactionType.RON) {
                ResolvedGbWin win = ronWinEvaluator.evaluate(playerId, pending.discarderId(), pending.tile(), pending.flags());
                if (win != null) {
                    wins.add(win);
                }
            }
        }
        if (!wins.isEmpty()) {
            return new Resolution(List.copyOf(wins), null, false, false);
        }
        if (pending.robbingKong()) {
            return new Resolution(List.of(), null, true, false);
        }
        for (SeatWind wind : GbRoundSupport.orderedAfter(discarderSeat)) {
            UUID playerId = playerAt.apply(wind);
            if (playerId == null) {
                continue;
            }
            ReactionResponse response = pending.responses().get(playerId);
            if (response == null) {
                continue;
            }
            if (response.getType() == ReactionType.MINKAN
                || response.getType() == ReactionType.PON
                || response.getType() == ReactionType.CHII) {
                return new Resolution(List.of(), new Claim(playerId, response), false, false);
            }
        }
        return new Resolution(List.of(), null, false, true);
    }

    record Resolution(List<ResolvedGbWin> wins, Claim claim, boolean finishAddedKong, boolean advanceAfterDiscard) {
        static Resolution noop() {
            return new Resolution(List.of(), null, false, false);
        }
    }

    record Claim(UUID playerId, ReactionResponse response) {
    }

    record PendingReactionWindow(
        UUID discarderId,
        MahjongTile tile,
        LinkedHashMap<UUID, ReactionOptions> options,
        Map<UUID, ReactionResponse> responses,
        List<String> flags,
        boolean robbingKong,
        Integer upgradeMeldIndex
    ) {
    }

    record ResolvedGbWin(
        UUID winnerId,
        UUID discarderId,
        MahjongTile winningTile,
        GbWinResponse response
    ) {
    }
}
