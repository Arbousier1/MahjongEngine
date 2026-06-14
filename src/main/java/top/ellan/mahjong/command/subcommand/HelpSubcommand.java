package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;

public final class HelpSubcommand extends AbstractMahjongSubcommand {
    public HelpSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("help"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) { this.context.sendHelp(player); }
}