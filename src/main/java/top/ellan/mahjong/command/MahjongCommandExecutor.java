package top.ellan.mahjong.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface MahjongCommandExecutor {
    void execute(CommandSender sender, Player player, String[] args);
}
