package top.ellan.mahjong.table.runtime;

import top.ellan.mahjong.table.core.MahjongTableSession;

public final class TableLifecycleCoordinator {
    private final MahjongTableSession session;

    public TableLifecycleCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    public void shutdown() {
        this.stopActiveFlows();
        this.session.clearRenderDisplays();
        this.clearRoundTracking();
        this.session.shutdownViewerPresentation();
        this.clearReadyAndLeaveQueues();
        this.session.clearSpectatorsForLifecycle();
        this.session.clearEngineForLifecycle();
    }

    public void forceEndMatch() {
        this.stopActiveFlows();
        this.clearRoundTracking();
        this.session.invalidateRenderFingerprints();
        this.session.resetViewerPresentationForLifecycleChange();
        this.session.clearLeaveQueueForLifecycle();
        this.session.clearEngineForLifecycle();
        this.session.resetReadyStateForNextRound();
        this.session.render();
    }

    public void resetForServerStartup() {
        this.stopActiveFlows();
        this.session.clearRenderDisplays();
        this.clearRoundTracking();
        this.clearReadyAndLeaveQueues();
        this.session.clearSpectatorsForLifecycle();
        this.session.clearBotNamesForLifecycle();
        this.session.clearSeatAssignmentsForLifecycle();
        this.session.shutdownViewerPresentation();
        this.session.clearEngineForLifecycle();
        this.session.resetBotCounterForLifecycle();
    }

    private void stopActiveFlows() {
        this.session.cancelBotTask();
        this.session.cancelNextRoundCountdown();
        this.session.shutdownRenderFlow();
    }

    private void clearRoundTracking() {
        this.session.clearFeedbackTracking();
        this.session.clearRoundTrackingState();
    }

    private void clearReadyAndLeaveQueues() {
        this.session.clearReadyPlayersForLifecycle();
        this.session.clearLeaveQueueForLifecycle();
    }
}


