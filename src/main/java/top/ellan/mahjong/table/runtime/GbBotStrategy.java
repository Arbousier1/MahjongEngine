package top.ellan.mahjong.table.runtime;

import top.ellan.mahjong.table.core.MahjongTableSession;
import java.util.Objects;
import java.util.UUID;

final class GbBotStrategy implements BotStrategy {
    private static final int MAX_GB_TURN_RETRY_ATTEMPTS = 8;

    @Override
    public void schedule(MahjongTableSession session) {
        if (!session.isStarted()) {
            return;
        }
        for (UUID playerId : session.players()) {
            if (!session.isBot(playerId) || session.availableReactions(playerId) == null) {
                continue;
            }
            session.setBotTask(session.plugin().scheduler().runRegionDelayed(session.center(), () -> this.handleGbReaction(session, playerId), 20L));
            return;
        }
        UUID current = session.playerAt(session.currentSeat());
        if (current != null && session.isBot(current)) {
            session.setBotTask(session.plugin().scheduler().runRegionDelayed(session.center(), () -> this.handleGbTurn(session, current, 0), 20L));
        }
    }

    private void handleGbReaction(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        if (session.availableReactions(playerId) == null) {
            return;
        }
        session.react(playerId, session.gbSuggestedReaction(playerId));
    }

    private void handleGbTurn(MahjongTableSession session, UUID playerId, int retryAttempts) {
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
        if (!session.canSelectHandTile(playerId, discardIndex)) {
            discardIndex = findSelectableDiscardIndex(session, playerId, hand.size());
        }
        if (discardIndex < 0) {
            this.scheduleGbTurnRetry(session, playerId, retryAttempts, "no-selectable-discard");
            return;
        }
        if (!session.discard(playerId, discardIndex)
            && session.isStarted()
            && !session.hasPendingReaction()
            && Objects.equals(session.playerAt(session.currentSeat()), playerId)) {
            this.scheduleGbTurnRetry(session, playerId, retryAttempts, "discard-failed");
        }
    }

    private void scheduleGbTurnRetry(MahjongTableSession session, UUID playerId, int retryAttempts, String reason) {
        int nextRetry = retryAttempts + 1;
        if (nextRetry > MAX_GB_TURN_RETRY_ATTEMPTS) {
            session.plugin().getLogger().warning(
                "GB bot turn retry exhausted for table " + session.id()
                    + ", player " + playerId
                    + ", reason=" + reason
                    + ", maxRetries=" + MAX_GB_TURN_RETRY_ATTEMPTS
            );
            // Keep the bot loop alive with a normal-paced retry, so the table cannot deadlock.
            session.setBotTask(session.plugin().scheduler().runRegionDelayed(session.center(), () -> this.handleGbTurn(session, playerId, 0), 20L));
            return;
        }
        session.setBotTask(session.plugin().scheduler().runRegionDelayed(session.center(), () -> this.handleGbTurn(session, playerId, nextRetry), 10L));
    }

    private static int findSelectableDiscardIndex(MahjongTableSession session, UUID playerId, int handSize) {
        for (int i = handSize - 1; i >= 0; i--) {
            if (session.canSelectHandTile(playerId, i)) {
                return i;
            }
        }
        return -1;
    }
}
