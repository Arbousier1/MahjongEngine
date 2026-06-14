package top.ellan.mahjong.command.subcommand;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class KanSubcommand extends AbstractMahjongSubcommand {
    public KanSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("kan"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireTable(player);
        if (table == null || args.length < 2) { this.context.messages().send(player, "command.kan_usage"); return; }
        this.context.messages().send(player, table.declareKan(player.getUniqueId(), args[1]) ? "command.kan_success" : "command.kan_failed");
    }
    @Override protected List<String> suggest(Player player, String[] args) { return args.length == 2 ? this.context.matchPrefix(args[1], this.context.suggestedKanTiles(player)) : List.of(); }
}
