package top.ellan.mahjong.table.core;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public record TableViewerHudSnapshot(
    Component title,
    float progress,
    BossBar.Color color,
    String stateSignature
) {
}

