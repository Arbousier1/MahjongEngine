package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.riichi.ReactionType;

public final class SkipSubcommand extends AbstractMahjongSubcommand {
    public SkipSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("skip"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) { this.context.sendReaction(player, ReactionType.SKIP, null, "command.action.skip"); }
}
