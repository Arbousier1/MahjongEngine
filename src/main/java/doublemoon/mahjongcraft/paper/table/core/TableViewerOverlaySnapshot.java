package doublemoon.mahjongcraft.paper.table.core;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public record TableViewerOverlaySnapshot(
    UUID viewerId,
    String regionKey,
    boolean spectator,
    Component overlay,
    List<TableSpectatorSeatOverlaySnapshot> spectatorSeatOverlays,
    String fingerprint
) {
}
