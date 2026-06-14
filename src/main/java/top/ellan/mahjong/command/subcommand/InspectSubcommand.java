package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class InspectSubcommand extends AbstractMahjongSubcommand {
    public InspectSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("inspect", true); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireViewedTable(player);
        if (table == null) { return; }
        table.inspectRender(player);
        this.context.messages().send(player, "command.inspect_sent");
    }
}