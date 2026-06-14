package top.ellan.mahjong.command.subcommand;

import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;

public final class ListSubcommand extends AbstractMahjongSubcommand {
    public ListSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("list", true); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        if (this.context.tableManager().tables().isEmpty()) { this.context.messages().send(player, "command.no_active_tables"); return; }
        Locale locale = this.context.messages().resolveLocale(player);
        this.context.tableManager().tables().forEach(table -> player.sendMessage(this.context.messages().render(
            locale,
            "command.table_list_entry",
            this.context.messages().tag("table_id", table.id()),
            this.context.messages().tag("summary", table.waitingSummary(locale)),
            this.context.messages().number(locale, "x", table.center().getBlockX()),
            this.context.messages().number(locale, "y", table.center().getBlockY()),
            this.context.messages().number(locale, "z", table.center().getBlockZ())
        )));
    }
}