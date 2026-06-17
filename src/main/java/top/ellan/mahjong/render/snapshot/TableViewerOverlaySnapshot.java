package top.ellan.mahjong.render.snapshot;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public record TableViewerOverlaySnapshot(
    UUID viewerId,
    String regionKey,
    boolean spectator,
    Component overlay,
    TableViewerPromptSnapshot prompt,
    TableViewerActionOverlaySnapshot actions,
    List<TableSpectatorSeatOverlaySnapshot> spectatorSeatOverlays,
    String fingerprint
) {
}
