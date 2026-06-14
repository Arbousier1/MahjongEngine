package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.riichi.ReactionType;

public final class MinkanSubcommand extends AbstractMahjongSubcommand {
    public MinkanSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("minkan"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) { this.context.sendReaction(player, ReactionType.MINKAN, null, "command.action.minkan"); }
}
