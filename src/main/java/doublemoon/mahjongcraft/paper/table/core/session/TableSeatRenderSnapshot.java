package doublemoon.mahjongcraft.paper.table.core;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.scene.MeldView;
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick;
import java.util.List;
import java.util.UUID;

public record TableSeatRenderSnapshot(
    SeatWind wind,
    UUID playerId,
    String displayName,
    String publicSeatStatus,
    int points,
    boolean riichi,
    boolean ready,
    boolean queuedToLeave,
    boolean online,
    String viewerMembershipSignature,
    int selectedHandTileIndex,
    int riichiDiscardIndex,
    int stickLayoutCount,
    List<UUID> viewerIdsExcluding,
    List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand,
    List<doublemoon.mahjongcraft.paper.model.MahjongTile> discards,
    List<MeldView> melds,
    List<ScoringStick> scoringSticks,
    List<ScoringStick> cornerSticks
) {
}
