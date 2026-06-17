package top.ellan.mahjong.command.subcommand;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.ellan.mahjong.command.MahjongCommandContext;
import top.ellan.mahjong.command.MahjongSubcommand;

abstract class AbstractMahjongSubcommand {
    protected final MahjongCommandContext context;

    AbstractMahjongSubcommand(MahjongCommandContext context) {
        this.context = context;
    }

    final MahjongSubcommand subcommand(String name) {
        return this.subcommand(name, List.of(), false, true);
    }

    final MahjongSubcommand subcommand(String name, boolean adminOnly) {
        return this.subcommand(name, List.of(), adminOnly, true);
    }

    final MahjongSubcommand subcommand(String name, boolean adminOnly, boolean playerOnly) {
        return this.subcommand(name, List.of(), adminOnly, playerOnly);
    }

    final MahjongSubcommand subcommand(String name, List<String> aliases, boolean adminOnly, boolean playerOnly) {
        return new MahjongSubcommand(name, aliases, adminOnly, playerOnly, this::execute, this::suggest);
    }

    protected void execute(CommandSender sender, Player player, String[] args) {
    }

    protected List<String> suggest(Player player, String[] args) {
        return List.of();
    }
}
