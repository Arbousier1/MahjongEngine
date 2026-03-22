package top.ellan.mahjong.table.core;

import top.ellan.mahjong.model.SeatWind;
import net.kyori.adventure.text.Component;

public record TableSpectatorSeatOverlaySnapshot(
    SeatWind wind,
    Component overlay,
    String signature
) {
}

