package top.ellan.mahjong.table.presentation;

import top.ellan.mahjong.table.core.TableSessionMutator;
import top.ellan.mahjong.render.snapshot.TableViewerHudSnapshot;
import top.ellan.mahjong.render.snapshot.TableViewerOverlaySnapshot;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TableViewerPresentationCoordinator {
    private static final long OVERLAY_REFRESH_INTERVAL_TICKS = 20L;
    private static final long HUD_REFRESH_INTERVAL_TICKS = 20L;

    private final TableSessionMutator session;
    private final Map<UUID, BossBar> viewerHudBars = new HashMap<>();
    private final Map<UUID, String> viewerHudState = new HashMap<>();
    private final Set<UUID> viewerActionDirtyIds = new LinkedHashSet<>();
    private boolean viewerOverlayDirty = true;
    private boolean viewerHudDirty = true;
    private long nextOverlayRefreshTick;
    private long nextHudRefreshTick;

    public TableViewerPresentationCoordinator(TableSessionMutator session) {
        this.session = session;
    }

    public void markDirty() {
        this.viewerOverlayDirty = true;
        this.viewerHudDirty = true;
    }

    public void markViewerActionsDirty(UUID viewerId) {
        if (viewerId != null) {
            this.viewerActionDirtyIds.add(viewerId);
        }
    }

    public void flushIfNeeded() {
        long nowTick = Bukkit.getCurrentTick();
        if (this.shouldRefreshOverlay(nowTick)) {
            this.flushOverlay(nowTick);
        } else if (!this.viewerActionDirtyIds.isEmpty()) {
            this.flushDirtyViewerActions();
        }
        if (this.shouldRefreshHud(nowTick)) {
            this.flushHud(nowTick);
        }
    }

    public void flushNow() {
        long nowTick = Bukkit.getCurrentTick();
        this.flushOverlay(nowTick);
        if (this.viewerHudDirty || !this.viewerHudBars.isEmpty()) {
            this.flushHud(nowTick);
        }
    }

    public void flushViewerActionsNow(UUID viewerId) {
        Player viewer = viewerId == null ? null : this.session.onlinePlayer(viewerId);
        if (viewer == null) {
            return;
        }
        TableViewerOverlaySnapshot snapshot = this.session.captureViewerOverlaySnapshot(viewer);
        this.session.updateViewerActionRegions(snapshot);
        this.viewerActionDirtyIds.remove(viewerId);
    }

    public boolean hasPresentationState() {
        return !this.session.viewers().isEmpty() || this.hasViewerOverlayRegions() || !this.viewerHudBars.isEmpty();
    }

    public void resetForLifecycleChange() {
        this.markDirty();
    }

    public void shutdown() {
        this.markDirty();
        this.nextOverlayRefreshTick = 0L;
        this.nextHudRefreshTick = 0L;
        this.clearHud();
    }

    public void hideHud(UUID viewerId) {
        BossBar bar = this.viewerHudBars.remove(viewerId);
        this.viewerHudState.remove(viewerId);
        Player player = this.session.onlinePlayer(viewerId);
        if (bar != null && player != null) {
            this.session.plugin().scheduler().runEntity(player, () -> player.hideBossBar(bar));
        }
    }

    public void clearHud() {
        for (UUID viewerId : List.copyOf(this.viewerHudBars.keySet())) {
            this.hideHud(viewerId);
        }
    }

    private void flushOverlay(long nowTick) {
        this.updateViewerOverlayRegions();
        this.viewerOverlayDirty = false;
        this.viewerActionDirtyIds.clear();
        this.nextOverlayRefreshTick = nowTick + OVERLAY_REFRESH_INTERVAL_TICKS;
    }

    private void flushHud(long nowTick) {
        this.syncHud();
        this.viewerHudDirty = false;
        this.nextHudRefreshTick = nowTick + HUD_REFRESH_INTERVAL_TICKS;
    }

    private void updateViewerOverlayRegions() {
        List<Player> viewers = this.session.viewers();
        if (viewers.isEmpty()) {
            this.clearViewerOverlayRegions();
            this.viewerActionDirtyIds.clear();
            return;
        }
        Set<String> activeKeys = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            TableViewerOverlaySnapshot snapshot = this.session.captureViewerOverlaySnapshot(viewer);
            activeKeys.add(snapshot.regionKey());
            activeKeys.add(snapshot.prompt().regionKey());
            activeKeys.add(snapshot.actions().regionKey());
            this.session.updateViewerOverlayRegion(snapshot);
        }
        for (String regionKey : this.session.viewerOverlayRegionKeys()) {
            if (this.isViewerPresentationRegion(regionKey) && !activeKeys.contains(regionKey)) {
                this.session.removeManagedRegionDisplays(regionKey);
            }
        }
    }

    private void flushDirtyViewerActions() {
        for (UUID viewerId : List.copyOf(this.viewerActionDirtyIds)) {
            this.flushViewerActionsNow(viewerId);
        }
    }

    private void syncHud() {
        List<Player> viewers = this.session.viewers();
        if (viewers.isEmpty()) {
            this.hideOfflineHud(Set.of());
            return;
        }
        Set<UUID> onlineViewerIds = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            this.syncViewerHud(viewer, onlineViewerIds);
        }
        this.hideOfflineHud(onlineViewerIds);
    }

    private void syncViewerHud(Player viewer, Set<UUID> onlineViewerIds) {
        UUID viewerId = viewer.getUniqueId();
        onlineViewerIds.add(viewerId);
        Locale locale = this.session.plugin().messages().resolveLocale(viewer);
        TableViewerHudSnapshot snapshot = this.session.captureViewerHudSnapshot(locale, viewerId);
        BossBar bar = this.viewerHudBars.get(viewerId);
        if (bar == null) {
            bar = this.createHudBar(viewerId, viewer);
        }
        if (snapshot.stateSignature().equals(this.viewerHudState.get(viewerId))) {
            return;
        }
        bar.name(snapshot.title());
        bar.progress(snapshot.progress());
        bar.color(snapshot.color());
        this.viewerHudState.put(viewerId, snapshot.stateSignature());
    }

    private BossBar createHudBar(UUID viewerId, Player viewer) {
        BossBar bar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        this.viewerHudBars.put(viewerId, bar);
        this.session.plugin().scheduler().runEntity(viewer, () -> viewer.showBossBar(bar));
        return bar;
    }

    private void hideOfflineHud(Set<UUID> onlineViewerIds) {
        for (UUID viewerId : List.copyOf(this.viewerHudBars.keySet())) {
            if (!onlineViewerIds.contains(viewerId)) {
                this.hideHud(viewerId);
            }
        }
    }

    private boolean shouldRefreshOverlay(long nowTick) {
        return this.viewerOverlayDirty || (this.hasViewerOverlayRegions() && nowTick >= this.nextOverlayRefreshTick);
    }

    private boolean shouldRefreshHud(long nowTick) {
        return this.viewerHudDirty || (!this.viewerHudBars.isEmpty() && nowTick >= this.nextHudRefreshTick);
    }

    private boolean hasViewerOverlayRegions() {
        for (String regionKey : this.session.viewerOverlayRegionKeys()) {
            if (this.isViewerPresentationRegion(regionKey)) {
                return true;
            }
        }
        return false;
    }

    private void clearViewerOverlayRegions() {
        for (String regionKey : this.session.viewerOverlayRegionKeys()) {
            if (this.isViewerPresentationRegion(regionKey)) {
                this.session.removeManagedRegionDisplays(regionKey);
            }
        }
    }

    private boolean isViewerPresentationRegion(String regionKey) {
        return regionKey != null
            && (regionKey.startsWith("viewer-overlay:")
                || regionKey.startsWith("viewer-prompt:")
                || regionKey.startsWith("viewer-actions:"));
    }
}
