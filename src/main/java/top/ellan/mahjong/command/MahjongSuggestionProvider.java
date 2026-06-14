package top.ellan.mahjong.command;

import java.util.List;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface MahjongSuggestionProvider {
    List<String> suggest(Player player, String[] args);
}
