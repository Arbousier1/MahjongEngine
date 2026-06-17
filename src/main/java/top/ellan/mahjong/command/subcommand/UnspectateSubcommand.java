package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class UnspectateSubcommand extends AbstractMahjongSubcommand {
    public UnspectateSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("unspectate"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.tableManager().unspectate(player.getUniqueId());
        this.context.messages().send(player, table == null ? "command.not_in_table" : "command.unspectated");
    }
}
