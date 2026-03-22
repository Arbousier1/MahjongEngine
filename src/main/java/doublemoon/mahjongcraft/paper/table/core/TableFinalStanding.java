package doublemoon.mahjongcraft.paper.table.core;

import java.util.UUID;

public record TableFinalStanding(
    UUID playerId,
    String displayName,
    int place,
    int points,
    double gameScore,
    boolean bot
) {
}
