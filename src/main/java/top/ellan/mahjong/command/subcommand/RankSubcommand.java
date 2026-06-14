package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;

public final class RankSubcommand extends AbstractMahjongSubcommand {
    public RankSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("rank"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) { this.context.showRank(player); }
}