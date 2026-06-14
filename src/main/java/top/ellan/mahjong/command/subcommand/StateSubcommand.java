package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class StateSubcommand extends AbstractMahjongSubcommand {
    public StateSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("state"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireViewedTable(player);
        if (table == null) { return; }
        player.sendMessage(table.stateSummary(player));
    }
}
