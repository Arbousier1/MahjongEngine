package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableManager;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class LeaveSubcommand extends AbstractMahjongSubcommand {
    public LeaveSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("leave"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession current = this.context.tableManager().sessionForViewer(player.getUniqueId());
        if (current == null) { this.context.messages().send(player, "command.not_in_table"); return; }
        MahjongTableManager.LeaveResult result = this.context.tableManager().leave(player.getUniqueId());
        switch (result.status()) {
            case LEFT -> this.context.messages().send(player, "command.left_table");
            case DEFERRED -> this.context.messages().send(player, "command.leave_deferred");
            case UNSPECTATED -> this.context.messages().send(player, "command.unspectated");
            case NOT_IN_TABLE -> this.context.messages().send(player, "command.not_in_table");
            case BLOCKED -> this.context.messages().send(player, "command.leave_blocked_started");
        }
    }
}