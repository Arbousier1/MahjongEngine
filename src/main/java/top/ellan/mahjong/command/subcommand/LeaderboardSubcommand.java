package top.ellan.mahjong.command.subcommand;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;
import top.ellan.mahjong.model.MahjongVariant;

public final class LeaderboardSubcommand extends AbstractMahjongSubcommand {
    public LeaderboardSubcommand(MahjongCommandContext context) { super(context); }
    public MahjongSubcommand create() { return this.subcommand("leaderboard", List.of("lb"), false, true); }
    @Override protected void execute(CommandSender sender, Player player, String[] args) {
        MahjongVariant mode = args.length >= 2 ? this.context.parseMode(args[1]) : null;
        if (args.length >= 2 && mode == null) {
            this.context.messages().send(player, "command.leaderboard_usage");
            return;
        }
        this.context.showLeaderboard(player, mode);
    }
    @Override protected List<String> suggest(Player player, String[] args) {
        return args.length == 2 ? this.context.suggestModes(args[1]) : List.of();
    }
}
