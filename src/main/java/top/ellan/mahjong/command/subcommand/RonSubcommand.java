package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.riichi.ReactionType;

public final class RonSubcommand extends AbstractMahjongSubcommand {
    public RonSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("ron"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) { this.context.sendReaction(player, ReactionType.RON, null, "command.action.ron"); }
}
