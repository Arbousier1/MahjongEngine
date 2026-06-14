package top.ellan.mahjong.command.subcommand;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.table.core.MahjongVariant;

public final class RiichiSubcommand extends AbstractMahjongSubcommand {
    public RiichiSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("riichi"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongTableSession table = this.context.requireTable(player);
        if (table == null) { return; }
        if (table.currentVariant() != MahjongVariant.RIICHI) {
            this.context.messages().send(player, "command.variant_action_unavailable", this.context.messages().tag("action", "riichi"), this.context.messages().tag("variant", table.currentVariant().name()));
            return;
        }
        if (args.length < 2) { this.context.messages().send(player, "command.riichi_usage"); return; }
        java.util.OptionalInt index = this.context.parseIndex(args[1]);
        this.context.messages().send(player, index.isPresent() && table.declareRiichi(player.getUniqueId(), index.getAsInt()) ? "command.riichi_success" : "command.riichi_failed");
    }
    @Override protected List<String> suggest(Player player, String[] args) { return args.length == 2 ? this.context.matchPrefix(args[1], this.context.suggestedRiichiIndices(player)) : List.of(); }
}
