package top.ellan.mahjong.render.snapshot;

import java.util.List;
import java.util.UUID;

public record TableViewerActionOverlaySnapshot(
    UUID viewerId,
    String regionKey,
    List<TableViewerActionButtonSnapshot> actionButtons,
    String fingerprint
) {
}
