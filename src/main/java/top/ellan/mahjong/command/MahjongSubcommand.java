package top.ellan.mahjong.command;

import java.util.List;

public record MahjongSubcommand(
    String name,
    List<String> aliases,
    boolean adminOnly,
    boolean playerOnly,
    MahjongCommandExecutor executor,
    MahjongSuggestionProvider suggestions
) {
}
