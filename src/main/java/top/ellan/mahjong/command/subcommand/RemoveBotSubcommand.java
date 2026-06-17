package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class RemoveBotSubcommand extends AbstractMahjongSubcommand {
    public RemoveBotSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("removebot"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireTable(player);
        if (table == null) { return; }
        if (!this.context.requireTableManager(player, table)) { return; }
        this.context.messages().send(player, table.removeBot() ? "command.bot_removed" : "command.bot_remove_failed");
    }
}
