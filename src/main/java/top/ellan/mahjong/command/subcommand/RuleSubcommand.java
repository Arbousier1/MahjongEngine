package top.ellan.mahjong.command.subcommand;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;

public final class RuleSubcommand extends AbstractMahjongSubcommand {
    public RuleSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("rule"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireTable(player);
        if (table == null) { return; }
        if (args.length == 1) {
            Locale locale = this.context.messages().resolveLocale(player);
            this.context.messages().send(player, "command.rule_summary", this.context.messages().tag("summary", table.ruleSummary(locale)));
            return;
        }
        if (args.length < 3) { this.context.messages().send(player, "command.rule_usage"); return; }
        Locale locale = this.context.messages().resolveLocale(player);
        this.context.messages().send(player, table.setRuleOption(args[1], args[2]) ? "command.rule_updated" : "command.rule_update_failed", this.context.messages().tag("summary", table.ruleSummary(locale)));
    }
    @Override protected List<String> suggest(Player player, String[] args) {
        MahjongTableSession table = this.context.tableManager().tableFor(player.getUniqueId());
        if (table == null) { return List.of(); }
        if (args.length == 2) { return this.context.matchPrefix(args[1], table.ruleKeys()); }
        if (args.length == 3) { return this.context.matchPrefix(args[2], table.ruleValues(args[1])); }
        return List.of();
    }
}
