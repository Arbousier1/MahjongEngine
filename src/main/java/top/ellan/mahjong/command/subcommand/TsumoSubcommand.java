package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class TsumoSubcommand extends AbstractMahjongSubcommand {
    public TsumoSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("tsumo"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireTable(player);
        if (table == null) { return; }
        this.context.messages().send(player, table.declareTsumo(player.getUniqueId()) ? "command.tsumo_success" : "command.tsumo_failed");
    }
}
