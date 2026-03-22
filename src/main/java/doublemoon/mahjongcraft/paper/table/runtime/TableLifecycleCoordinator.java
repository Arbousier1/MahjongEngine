package doublemoon.mahjongcraft.paper.table.runtime;

import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;

public final class TableLifecycleCoordinator {
    private final MahjongTableSession session;

    public TableLifecycleCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    public void shutdown() {
        this.session.cancelBotTask();
        this.session.cancelNextRoundCountdown();
        this.session.shutdownRenderFlow();
        this.session.clearRenderDisplays();
        this.session.clearFeedbackTracking();
        this.session.clearRoundTrackingState();
        this.session.shutdownViewerPresentation();
        this.session.clearReadyPlayersForLifecycle();
        this.session.clearLeaveQueueForLifecycle();
        this.session.clearSpectatorsForLifecycle();
        this.session.clearEngineForLifecycle();
    }

    public void forceEndMatch() {
        this.session.cancelBotTask();
        this.session.cancelNextRoundCountdown();
        this.session.shutdownRenderFlow();
        this.session.clearFeedbackTracking();
        this.session.clearRoundTrackingState();
        this.session.invalidateRenderFingerprints();
        this.session.resetViewerPresentationForLifecycleChange();
        this.session.clearLeaveQueueForLifecycle();
        this.session.clearEngineForLifecycle();
        this.session.resetReadyStateForNextRound();
        this.session.render();
    }

    public void resetForServerStartup() {
        this.session.cancelBotTask();
        this.session.cancelNextRoundCountdown();
        this.session.shutdownRenderFlow();
        this.session.clearRenderDisplays();
        this.session.clearFeedbackTracking();
        this.session.clearRoundTrackingState();
        this.session.clearReadyPlayersForLifecycle();
        this.session.clearLeaveQueueForLifecycle();
        this.session.clearSpectatorsForLifecycle();
        this.session.clearBotNamesForLifecycle();
        this.session.clearSeatAssignmentsForLifecycle();
        this.session.shutdownViewerPresentation();
        this.session.clearEngineForLifecycle();
        this.session.resetBotCounterForLifecycle();
    }
}

