package doublemoon.mahjongcraft.paper.table.presentation;

import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class TableViewerPresentationCoordinator {
    private final MahjongTableSession session;
    private final Map<UUID, BossBar> viewerHudBars = new HashMap<>();
    private final Map<UUID, String> viewerHudState = new HashMap<>();
    private boolean viewerOverlayDirty = true;
    private boolean viewerHudDirty = true;

    public TableViewerPresentationCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    public void markDirty() {
        this.viewerOverlayDirty = true;
        this.viewerHudDirty = true;
    }

    public void flushIfNeeded() {
        if (this.viewerOverlayDirty || this.hasViewerOverlayRegions()) {
            this.updateViewerOverlayRegions();
            this.viewerOverlayDirty = false;
        }
        if (this.viewerHudDirty || !this.viewerHudBars.isEmpty()) {
            this.syncHud();
            this.viewerHudDirty = false;
        }
    }

    public boolean hasPresentationState() {
        return !this.session.viewers().isEmpty() || this.hasViewerOverlayRegions() || !this.viewerHudBars.isEmpty();
    }

    public void resetForLifecycleChange() {
        this.markDirty();
    }

    public void shutdown() {
        this.markDirty();
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

    private void updateViewerOverlayRegions() {
        List<Player> viewers = this.session.viewers();
        if (viewers.isEmpty()) {
            this.clearViewerOverlayRegions();
            return;
        }
        Set<String> activeKeys = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            MahjongTableSession.ViewerOverlaySnapshot snapshot = this.session.captureViewerOverlaySnapshot(viewer);
            activeKeys.add(snapshot.regionKey());
            this.session.updateViewerOverlayRegion(snapshot);
        }
        for (String regionKey : this.session.viewerOverlayRegionKeys()) {
            if (regionKey.startsWith("viewer-overlay:") && !activeKeys.contains(regionKey)) {
                this.session.removeManagedRegionDisplays(regionKey);
            }
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
        MahjongTableSession.ViewerHudSnapshot snapshot = this.session.captureViewerHudSnapshot(locale, viewerId);
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

    private boolean hasViewerOverlayRegions() {
        for (String regionKey : this.session.viewerOverlayRegionKeys()) {
            if (regionKey.startsWith("viewer-overlay:")) {
                return true;
            }
        }
        return false;
    }

    private void clearViewerOverlayRegions() {
        for (String regionKey : this.session.viewerOverlayRegionKeys()) {
            if (regionKey.startsWith("viewer-overlay:")) {
                this.session.removeManagedRegionDisplays(regionKey);
            }
        }
    }
}

