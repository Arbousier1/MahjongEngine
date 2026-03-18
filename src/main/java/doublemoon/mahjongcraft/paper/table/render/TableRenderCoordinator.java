package doublemoon.mahjongcraft.paper.table.render;

import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import org.bukkit.Bukkit;

public final class TableRenderCoordinator {
    private static final long DISPLAY_RESTORE_CHECK_INTERVAL_TICKS = 60L;

    private final MahjongTableSession session;
    private long renderRequestVersion;
    private long renderCancellationNonce;
    private boolean renderPrecomputeRunning;
    private boolean renderFlushScheduled;
    private long nextDisplayRestoreCheckTick;
    private MahjongTableSession.RenderSnapshot pendingRenderSnapshot;

    public TableRenderCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    public void render() {
        this.session.prepareRenderRequest();
        if (this.renderFlushScheduled) {
            return;
        }
        this.renderFlushScheduled = true;
        this.session.plugin().scheduler().runRegion(this.session.center(), this::flushRender);
    }

    public void clearDisplays() {
        this.invalidatePendingRenderPrecompute();
        this.nextDisplayRestoreCheckTick = 0L;
        this.session.clearRenderDisplays();
    }

    public void shutdown() {
        this.invalidatePendingRenderPrecompute();
        this.renderFlushScheduled = false;
        this.nextDisplayRestoreCheckTick = 0L;
    }

    public void restoreDisplaysIfNeeded() {
        if (!this.session.hasRegionDisplays() || this.renderFlushScheduled || this.renderPrecomputeRunning) {
            return;
        }
        long nowTick = Bukkit.getCurrentTick();
        if (nowTick < this.nextDisplayRestoreCheckTick) {
            return;
        }
        this.nextDisplayRestoreCheckTick = nowTick + DISPLAY_RESTORE_CHECK_INTERVAL_TICKS;
        if (!this.session.isCenterChunkLoaded() || !this.session.hasStaleDisplayRegions()) {
            return;
        }
        this.render();
    }

    private void flushRender() {
        this.renderFlushScheduled = false;
        this.scheduleAsyncRenderPrecompute();
        this.session.completeRenderFlush();
    }

    private void scheduleAsyncRenderPrecompute() {
        MahjongTableSession.RenderSnapshot snapshot = this.session.captureRenderSnapshot(++this.renderRequestVersion, this.renderCancellationNonce);
        this.pendingRenderSnapshot = snapshot;
        if (this.renderPrecomputeRunning) {
            return;
        }
        this.startAsyncRenderPrecompute(snapshot);
    }

    private void startAsyncRenderPrecompute(MahjongTableSession.RenderSnapshot snapshot) {
        this.renderPrecomputeRunning = true;
        this.session.plugin().async().execute("render-precompute-" + this.session.id(), () -> {
            MahjongTableSession.RenderPrecomputeResult result = this.session.precomputeRender(snapshot);
            this.session.plugin().scheduler().runRegion(this.session.center(), () -> this.finishAsyncRenderPrecompute(result));
        });
    }

    private void finishAsyncRenderPrecompute(MahjongTableSession.RenderPrecomputeResult result) {
        this.renderPrecomputeRunning = false;
        if (result.snapshot().cancellationNonce() != this.renderCancellationNonce) {
            this.startNextPendingRenderIfNeeded(result.snapshot().version());
            return;
        }

        MahjongTableSession.RenderSnapshot latestSnapshot = this.pendingRenderSnapshot;
        if (latestSnapshot != null && latestSnapshot.version() > result.snapshot().version()) {
            this.startNextPendingRenderIfNeeded(result.snapshot().version());
            return;
        }

        this.session.applyRenderPrecompute(result);
        this.nextDisplayRestoreCheckTick = Bukkit.getCurrentTick() + DISPLAY_RESTORE_CHECK_INTERVAL_TICKS;
        this.startNextPendingRenderIfNeeded(result.snapshot().version());
    }

    private void startNextPendingRenderIfNeeded(long completedVersion) {
        MahjongTableSession.RenderSnapshot latestSnapshot = this.pendingRenderSnapshot;
        if (latestSnapshot == null || latestSnapshot.version() <= completedVersion) {
            return;
        }
        this.startAsyncRenderPrecompute(latestSnapshot);
    }

    private void invalidatePendingRenderPrecompute() {
        this.renderCancellationNonce++;
        this.pendingRenderSnapshot = null;
        this.renderPrecomputeRunning = false;
    }
}

