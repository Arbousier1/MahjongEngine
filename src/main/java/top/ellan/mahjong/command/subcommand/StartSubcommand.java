package top.ellan.mahjong.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;

public final class StartSubcommand extends AbstractMahjongSubcommand {
    public StartSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("start"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        try { this.context.tableManager().sendReadyResult(player, this.context.tableManager().start(player)); }
        catch (IllegalStateException ex) { this.context.messages().send(player, "command.start_failed", this.context.messages().tag("reason", ex.getMessage())); }
    }
}
