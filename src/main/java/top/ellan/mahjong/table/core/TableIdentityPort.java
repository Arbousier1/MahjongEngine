package top.ellan.mahjong.table.core;

import top.ellan.mahjong.model.MahjongVariant;
import top.ellan.mahjong.riichi.model.MahjongRule;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Minimal identity port for a mahjong table session, exposing only the
 * immutable identifying attributes needed by external coordinators that do
 * not participate in round or membership operations.
 */
public interface TableIdentityPort {
    String id();

    Location center();

    boolean isPersistentRoom();

    boolean isBotMatchRoom();

    MahjongVariant configuredVariant();

    MahjongRule configuredRuleSnapshot();

    UUID owner();
}
