package top.ellan.mahjong.table.core;

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.riichi.ReactionResponse;
import top.ellan.mahjong.riichi.model.MahjongRule;
import top.ellan.mahjong.table.core.round.OpeningDiceRoll;
import top.ellan.mahjong.table.core.round.TableRoundController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

interface SessionState {
    MahjongPaperPlugin plugin();

    MahjongVariant currentVariant();

    boolean isStarted();

    int size();

    boolean isReady(UUID playerId);

    UUID playerAt(SeatWind wind);

    SeatWind seatOf(UUID playerId);

    boolean removePlayer(UUID playerId);

    int botCount();

    boolean isBotMatchRoom();

    void render();

    boolean discard(UUID playerId, int tileIndex);

    boolean canSelectHandTileInternal(UUID playerId, int tileIndex);

    void refreshSelectedHandTileViewInternal(UUID playerId);

    top.ellan.mahjong.model.MahjongTile handTileAtInternal(UUID playerId, int tileIndex);

    void clearSelectedHandTilesInternal();

    void clearLastPublicDiscardInternal();

    void clearLastPublicActionInternal();

    void rememberPublicDiscardInternal(UUID playerId, top.ellan.mahjong.model.MahjongTile discardedTile);

    void rememberPublicActionInternal(UUID playerId, String actionKey);

    void playReactionSoundInternal(ReactionResponse response);

    MahjongRule configuredRuleInternal();

    void setConfiguredRuleInternal(MahjongRule configuredRule);

    void setConfiguredVariantInternal(MahjongVariant configuredVariant);

    void persistRoomMetadataIfNeededInternal();

    TableRoundController roundControllerInternal();

    boolean seatAssignmentsMatchControllerInternal(TableRoundController controller);

    void setRoundControllerInternal(TableRoundController roundController);

    TableRoundController createRoundControllerInternal();

    boolean isRoundStartInProgress();

    void setRoundStartInProgressInternal(boolean roundStartInProgress);

    void resetRoundPresentationForStartInternal();

    boolean shouldAnimateOpeningDiceInternal();

    void startOpeningDiceAnimationInternal(OpeningDiceRoll diceRoll, Runnable completion);

    void completeRoundStartInternal();

    void playRoundStartSoundInternal();

    void restoreDisplaysIfNeededInternal();

    void flushViewerPresentationIfNeededInternal();

    boolean hasQueuedLeavesInternal();

    Set<UUID> queuedLeavePlayersInternal();

    void removeQueuedLeavesInternal(List<UUID> removedPlayerIds);

    void finalizeDeferredLeaves(Map<UUID, SeatWind> removed);

    void scheduleNextRoundCountdownInternal();

    boolean hasNextRoundCountdownInternal();

    void cancelNextRoundCountdownInternal();

    long nextRoundSecondsRemainingInternal();
}
