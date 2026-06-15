package top.ellan.mahjong.render.snapshot;

import top.ellan.mahjong.model.SeatWind;
import net.kyori.adventure.text.Component;

public record TableSpectatorSeatOverlaySnapshot(
    SeatWind wind,
    Component overlay,
    String signature
) {
}
