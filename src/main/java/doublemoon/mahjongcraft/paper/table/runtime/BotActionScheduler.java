package doublemoon.mahjongcraft.paper.table.runtime;

import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.ReactionType;
import doublemoon.mahjongcraft.paper.riichi.RiichiPlayerState;
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile;
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance;
import doublemoon.mahjongcraft.paper.table.core.MahjongVariant;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import java.util.Objects;
import java.util.UUID;
import kotlin.Pair;
public final class BotActionScheduler {
    private BotActionScheduler() {
    }

    public static void schedule(MahjongTableSession session) {
        if (session.currentVariant() == MahjongVariant.GB) {
            scheduleGb(session);
            return;
        }
        RiichiRoundEngine engine = session.engine();
        if (engine == null || !engine.getStarted()) {
            return;
        }

        UUID pendingBot = findPendingBotReaction(session, engine);
        if (pendingBot != null) {
            session.setBotTask(session.plugin().scheduler().runRegionDelayed(session.center(), () -> handleBotReaction(session, pendingBot), 20L));
            return;
        }

        UUID current = UUID.fromString(engine.getCurrentPlayer().getUuid());
        if (session.isBot(current)) {
            session.setBotTask(session.plugin().scheduler().runRegionDelayed(session.center(), () -> handleBotTurn(session, current), 20L));
        }
    }

    private static void scheduleGb(MahjongTableSession session) {
        if (!session.isStarted()) {
            return;
        }
        for (UUID playerId : session.players()) {
            if (!session.isBot(playerId) || session.availableReactions(playerId) == null) {
                continue;
            }
            session.setBotTask(session.plugin().scheduler().runRegionDelayed(session.center(), () -> handleGbReaction(session, playerId), 20L));
            return;
        }
        UUID current = session.playerAt(session.currentSeat());
        if (current != null && session.isBot(current)) {
            session.setBotTask(session.plugin().scheduler().runRegionDelayed(session.center(), () -> handleGbTurn(session, current), 20L));
        }
    }

    private static UUID findPendingBotReaction(MahjongTableSession session, RiichiRoundEngine engine) {
        if (engine.getPendingReaction() == null) {
            return null;
        }
        for (String uuid : engine.getPendingReaction().getOptions().keySet()) {
            UUID playerId = UUID.fromString(uuid);
            if (session.isBot(playerId) && !engine.getPendingReaction().getResponses().containsKey(uuid)) {
                return playerId;
            }
        }
        return null;
    }

    private static void handleBotReaction(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        RiichiRoundEngine engine = session.engine();
        if (engine == null) {
            return;
        }
        ReactionOptions options = engine.availableReactions(playerId.toString());
        if (options == null) {
            return;
        }
        ReactionResponse suggestion = options.getSuggestedResponse();
        if (suggestion != null) {
            session.react(playerId, suggestion);
            return;
        }
        session.react(playerId, new ReactionResponse(ReactionType.SKIP, null));
    }

    private static void handleGbReaction(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        if (session.availableReactions(playerId) == null) {
            return;
        }
        session.react(playerId, session.gbSuggestedReaction(playerId));
    }

    private static void handleBotTurn(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        RiichiRoundEngine engine = session.engine();
        if (engine == null || !engine.getStarted() || engine.getPendingReaction() != null) {
            return;
        }
        if (!Objects.equals(engine.getCurrentPlayer().getUuid(), playerId.toString())) {
            return;
        }
        RiichiPlayerState player = engine.seatPlayer(playerId.toString());
        if (player == null) {
            return;
        }

        if (session.declareTsumo(playerId)) {
            return;
        }
        if (player.getCanAnkan() && !player.getTilesCanAnkan().isEmpty()) {
            if (session.declareKan(playerId, player.getTilesCanAnkan().iterator().next().getMahjongTile().name())) {
                return;
            }
        }
        if (player.getCanKakan()) {
            for (TileInstance tile : player.getHands()) {
                boolean matchFuuro = player.getFuuroList().stream()
                    .anyMatch(fuuro -> fuuro.getTileInstances().stream().anyMatch(existing -> existing.getCode() == tile.getCode()));
                if (matchFuuro && session.declareKan(playerId, tile.getMahjongTile().name())) {
                    return;
                }
            }
        }
        if (engine.canKyuushuKyuuhai(playerId.toString()) && player.getNumbersOfYaochuuhaiTypes() >= 11) {
            if (session.declareKyuushuKyuuhai(playerId)) {
                return;
            }
        }
        java.util.List<Pair<MahjongTile, java.util.List<MahjongTile>>> riichiPairs = player.getTilePairsForRiichi();
        if (player.isMenzenchin() && !player.getRiichi() && !player.getDoubleRiichi() && player.getPoints() >= 1000 && !riichiPairs.isEmpty()) {
            MahjongTile discardTile = riichiPairs.getFirst().getFirst();
            int discardIndex = findDiscardIndex(player, discardTile);
            if (discardIndex >= 0 && session.declareRiichi(playerId, discardIndex)) {
                return;
            }
        }
        session.discard(playerId, chooseBotDiscardIndex(player));
    }

    private static void handleGbTurn(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        if (!session.isStarted() || session.hasPendingReaction() || !Objects.equals(session.playerAt(session.currentSeat()), playerId)) {
            return;
        }
        if (session.gbCanWinByTsumo(playerId) && session.declareTsumo(playerId)) {
            return;
        }
        String kanTile = session.gbSuggestedKanTile(playerId);
        if (kanTile != null && session.declareKan(playerId, kanTile)) {
            return;
        }
        var hand = session.hand(playerId);
        if (hand.isEmpty()) {
            return;
        }
        int discardIndex = session.gbSuggestedDiscardIndex(playerId);
        if (discardIndex < 0 || discardIndex >= hand.size()) {
            discardIndex = hand.size() - 1;
        }
        session.discard(playerId, discardIndex);
    }

    private static int findDiscardIndex(RiichiPlayerState player, MahjongTile tile) {
        for (int i = player.getHands().size() - 1; i >= 0; i--) {
            if (player.getHands().get(i).getMahjongTile() == tile) {
                return i;
            }
        }
        return -1;
    }

    private static int chooseBotDiscardIndex(RiichiPlayerState player) {
        if (player.getRiichi() || player.getDoubleRiichi()) {
            return player.getHands().size() - 1;
        }
        for (MahjongTile tile : player.bestDiscardSuggestions()) {
            int index = findDiscardIndex(player, tile);
            if (index >= 0) {
                return index;
            }
        }
        return player.getHands().size() - 1;
    }
}

