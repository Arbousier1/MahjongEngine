package top.ellan.mahjong.render.snapshot;

import java.util.UUID;
import net.kyori.adventure.text.Component;

public record TableViewerPromptSnapshot(
    UUID viewerId,
    String regionKey,
    boolean visible,
    Component prompt,
    String fingerprint
) {
}
