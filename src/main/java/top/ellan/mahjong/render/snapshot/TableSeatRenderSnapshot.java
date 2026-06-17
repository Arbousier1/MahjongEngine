package top.ellan.mahjong.render.snapshot;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.scene.MeldView;
import top.ellan.mahjong.riichi.model.ScoringStick;
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
    List<Integer> selectedHandTileIndices,
    int riichiDiscardIndex,
    int stickLayoutCount,
    List<UUID> viewerIdsExcluding,
    List<top.ellan.mahjong.model.MahjongTile> hand,
    List<top.ellan.mahjong.model.MahjongTile> discards,
    List<MeldView> melds,
    List<ScoringStick> scoringSticks,
    List<ScoringStick> cornerSticks
) {
}
