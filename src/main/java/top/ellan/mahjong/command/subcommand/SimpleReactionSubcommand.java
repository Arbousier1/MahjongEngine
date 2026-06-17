package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.riichi.ReactionType;

public final class SimpleReactionSubcommand extends AbstractMahjongSubcommand {
    private final String name;
    private final ReactionType reactionType;
    private final String actionMessageKey;

    public SimpleReactionSubcommand(MahjongCommandContext context, String name, ReactionType reactionType, String actionMessageKey) {
        super(context);
        this.name = name;
        this.reactionType = reactionType;
        this.actionMessageKey = actionMessageKey;
    }

    public MahjongSubcommand create() {
        return this.subcommand(this.name);
    }

    @Override
    protected void execute(CommandSender sender, Player player, String[] args) {
        this.context.sendReaction(player, this.reactionType, null, this.actionMessageKey);
    }
}
