package top.ellan.mahjong.command.subcommand;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class ModeSubcommand extends AbstractMahjongSubcommand {
    public ModeSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("mode"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireTable(player);
        if (table == null) { return; }
        if (!this.context.requireTableManager(player, table)) { return; }
        if (args.length < 2) { this.context.messages().send(player, "command.mode_usage"); return; }
        boolean updated = table.applyRulePreset(args[1]);
        Locale locale = this.context.messages().resolveLocale(player);
        this.context.messages().send(player, updated ? "command.mode_updated" : "command.mode_failed", this.context.messages().tag("summary", table.ruleSummary(locale)));
        if (updated) { table.render(); }
    }
    @Override protected List<String> suggest(Player player, String[] args) {
        if (args.length != 2) { return List.of(); }
        MahjongTableSession table = this.context.tableManager().tableFor(player.getUniqueId());
        return table == null ? List.of() : this.context.matchPrefix(args[1], table.ruleValues("mode"));
    }
}
