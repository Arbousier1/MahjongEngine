package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class SettlementSubcommand extends AbstractMahjongSubcommand {
    public SettlementSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("settlement"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireViewedTable(player);
        if (table == null) { return; }
        this.context.messages().send(player, table.openSettlementUi(player) ? "command.settlement_opened" : "command.settlement_unavailable");
    }
}