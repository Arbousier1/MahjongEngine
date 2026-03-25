package top.ellan.mahjong.table.core;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.table.core.round.OpeningDiceRoll;
import top.ellan.mahjong.table.core.round.TableRoundController;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SessionRoundFlowCoordinator {
    private final MahjongTableSession session;

    SessionRoundFlowCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    void startRound() {
        if (this.session.isRoundStartInProgress()) {
            return;
        }
        if (this.session.size() != 4) {
            throw new IllegalStateException("A table needs exactly 4 players");
        }
        if (!this.allSeatsReady()) {
            throw new IllegalStateException("All seated players must be ready");
        }

        this.session.cancelNextRoundCountdownInternal();
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null
            || controller.gameFinished()
            || controller.variant() != this.session.currentVariant()
            || !this.session.seatAssignmentsMatchControllerInternal(controller)) {
            controller = this.session.createRoundControllerInternal();
            this.session.setRoundControllerInternal(controller);
        }

        this.session.clearSelectedHandTilesInternal();
        this.session.clearLastPublicDiscardInternal();
        this.session.clearLastPublicActionInternal();
        this.session.resetRoundPresentationForStartInternal();
        this.session.setRoundStartInProgressInternal(true);
        // Hide waiting-state seat controls as soon as dice roll starts.
        this.session.render();
        OpeningDiceRoll diceRoll = OpeningDiceRoll.random();
        controller.setPendingDiceRoll(diceRoll);
        if (!this.session.shouldAnimateOpeningDiceInternal()) {
            this.session.completeRoundStartInternal();
            return;
        }
        this.session.startOpeningDiceAnimationInternal(diceRoll, this.session::completeRoundStartInternal);
    }

    boolean maybeStartRoundIfReady() {
        if (this.session.isStarted() || this.session.isRoundStartInProgress() || this.session.size() != 4 || !this.allSeatsReady()) {
            return false;
        }
        this.startRound();
        this.session.playRoundStartSoundInternal();
        return true;
    }

    void tick() {
        this.session.restoreDisplaysIfNeededInternal();
        this.session.flushViewerPresentationIfNeededInternal();
        TableRoundController controller = this.session.roundControllerInternal();
        if (this.session.isRoundStartInProgress() || controller == null) {
            return;
        }
        if (controller.started() && !controller.gameFinished()) {
            return;
        }
        if (controller.lastResolution() == null && !controller.gameFinished()) {
            return;
        }
        this.processDeferredLeaves();
        this.handleBotMatchAutoNextRound();
    }

    private void processDeferredLeaves() {
        if (!this.session.hasQueuedLeavesInternal() || this.session.isStarted()) {
            return;
        }
        Map<UUID, SeatWind> removed = new LinkedHashMap<>();
        for (UUID playerId : this.session.queuedLeavePlayersInternal()) {
            SeatWind wind = this.session.seatOf(playerId);
            if (this.session.removePlayer(playerId)) {
                removed.put(playerId, wind);
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        this.session.removeQueuedLeavesInternal(new ArrayList<>(removed.keySet()));
        if (this.session.plugin().tableManager() != null) {
            this.session.plugin().tableManager().finalizeDeferredLeaves(this.session, removed);
        }
    }

    private void handleBotMatchAutoNextRound() {
        if (!this.session.isBotMatchRoom() || this.session.size() != 4 || this.session.botCount() != 4) {
            this.session.cancelNextRoundCountdownInternal();
            return;
        }
        if (!this.session.hasNextRoundCountdownInternal()) {
            this.session.scheduleNextRoundCountdownInternal();
            this.session.render();
            return;
        }
        if (this.session.nextRoundSecondsRemainingInternal() > 0L) {
            return;
        }
        if (this.maybeStartRoundIfReady()) {
            return;
        }
        this.session.cancelNextRoundCountdownInternal();
    }

    private boolean allSeatsReady() {
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.session.playerAt(wind);
            if (playerId == null || !this.session.isReady(playerId)) {
                return false;
            }
        }
        return true;
    }
}

