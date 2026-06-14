package top.ellan.mahjong.command.subcommand;

import java.util.List;
import java.util.Locale;
import kotlin.Pair;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.riichi.ReactionType;
import top.ellan.mahjong.riichi.model.MahjongTile;

public final class ChiiSubcommand extends AbstractMahjongSubcommand {
    public ChiiSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("chii"); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        if (args.length < 3) { this.context.messages().send(player, "command.chii_usage"); return; }
        try {
            MahjongTile first = MahjongTile.valueOf(args[1].toUpperCase(Locale.ROOT));
            MahjongTile second = MahjongTile.valueOf(args[2].toUpperCase(Locale.ROOT));
            this.context.sendReaction(player, ReactionType.CHII, new Pair<>(first, second), "command.action.chii");
        } catch (IllegalArgumentException ex) { this.context.messages().send(player, "command.invalid_tile"); }
    }
    @Override protected List<String> suggest(Player player, String[] args) {
        if (args.length == 2) { return this.context.matchPrefix(args[1], this.context.suggestedChiiFirstTiles(player)); }
        if (args.length == 3) { return this.context.matchPrefix(args[2], this.context.suggestedChiiSecondTiles(player, args[1])); }
        return List.of();
    }
}
