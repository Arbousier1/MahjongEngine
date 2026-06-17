package top.ellan.mahjong.command.subcommand;

import java.util.List;
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
        java.util.Optional<top.ellan.mahjong.model.MahjongTile> first = this.context.parseTile(args[1]);
        java.util.Optional<top.ellan.mahjong.model.MahjongTile> second = this.context.parseTile(args[2]);
        if (first.isEmpty() || second.isEmpty()) { this.context.messages().send(player, "command.invalid_tile"); return; }
        this.context.sendReaction(
            player,
            ReactionType.CHII,
            new Pair<>(MahjongTile.valueOf(first.get().name()), MahjongTile.valueOf(second.get().name())),
            "command.action.chii"
        );
    }
    @Override protected List<String> suggest(Player player, String[] args) {
        if (args.length == 2) { return this.context.matchPrefix(args[1], this.context.suggestedChiiFirstTiles(player)); }
        if (args.length == 3) { return this.context.matchPrefix(args[2], this.context.suggestedChiiSecondTiles(player, args[1])); }
        return List.of();
    }
}
