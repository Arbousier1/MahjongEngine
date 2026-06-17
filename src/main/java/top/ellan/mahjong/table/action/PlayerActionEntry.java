package top.ellan.mahjong.table.action;

import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;

public record PlayerActionEntry(
    PlayerActionId actionId,
    String command,
    String labelKey,
    NamedTextColor color,
    boolean submenu,
    List<String> arguments
) {
    public PlayerActionEntry {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
