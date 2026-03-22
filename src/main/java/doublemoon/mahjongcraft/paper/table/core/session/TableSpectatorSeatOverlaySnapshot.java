package doublemoon.mahjongcraft.paper.table.core;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import net.kyori.adventure.text.Component;

public record TableSpectatorSeatOverlaySnapshot(
    SeatWind wind,
    Component overlay,
    String signature
) {
}
