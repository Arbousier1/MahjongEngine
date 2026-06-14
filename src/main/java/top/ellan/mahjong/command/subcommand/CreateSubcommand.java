package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class CreateSubcommand extends AbstractMahjongSubcommand {
    public CreateSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("create", true); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.tableManager().createTable(player);
        table.render();
        this.context.messages().send(player, "command.created_table", this.context.messages().tag("table_id", table.id()));
    }
}