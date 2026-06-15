package top.ellan.mahjong.render.snapshot;

import net.kyori.adventure.text.format.NamedTextColor;

public record TableViewerActionButtonSnapshot(
    String actionId,
    String label,
    NamedTextColor color,
    String command,
    float hitboxWidth
) {
}
