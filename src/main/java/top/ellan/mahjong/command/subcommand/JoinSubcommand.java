package top.ellan.mahjong.command.subcommand;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class JoinSubcommand extends AbstractMahjongSubcommand {
    public JoinSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("join"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        if (args.length < 2) { this.context.messages().send(player, "command.join_usage"); return; }
        MahjongTableSession table = this.context.tableManager().join(player, args[1]);
        if (table == null) {
            this.context.messages().send(player, "command.join_failed");
        } else {
            this.context.messages().send(player, "command.joined_table", this.context.messages().tag("table_id", table.id()));
        }
    }
    @Override protected List<String> suggest(Player player, String[] args) {
        return args.length == 2 ? this.context.matchPrefix(args[1], this.context.tableManager().tableIds()) : List.of();
    }
}