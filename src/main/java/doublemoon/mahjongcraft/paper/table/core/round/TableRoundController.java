package doublemoon.mahjongcraft.paper.table.core.round;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.scene.MeldView;
import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.RoundResolution;
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule;
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick;
import doublemoon.mahjongcraft.paper.table.core.MahjongVariant;
import java.util.List;
import java.util.UUID;

public interface TableRoundController {
    MahjongVariant variant();

    MahjongRule rule();

    boolean started();

    boolean gameFinished();

    void startRound();

    default void setPendingDiceRoll(OpeningDiceRoll diceRoll) {
    }

    boolean discard(UUID playerId, int tileIndex);

    default boolean declareRiichi(UUID playerId, int tileIndex) {
        return false;
    }

    default boolean declareTsumo(UUID playerId) {
        return false;
    }

    default boolean declareKyuushuKyuuhai(UUID playerId) {
        return false;
    }

    default boolean react(UUID playerId, ReactionResponse response) {
        return false;
    }

    default boolean declareKan(UUID playerId, String tileName) {
        return false;
    }

    UUID playerAt(SeatWind wind);

    int points(UUID playerId);

    boolean isRiichi(UUID playerId);

    int dicePoints();

    int kanCount();

    int roundIndex();

    int honbaCount();

    SeatWind roundWind();

    SeatWind dealerSeat();

    SeatWind currentSeat();

    String currentPlayerDisplayName();

    List<MahjongTile> hand(UUID playerId);

    List<MahjongTile> discards(UUID playerId);

    List<MahjongTile> remainingWall();

    default int remainingWallCount() {
        return this.remainingWall().size();
    }

    List<MeldView> fuuro(UUID playerId);

    List<ScoringStick> scoringSticks(UUID playerId);

    List<MahjongTile> doraIndicators();

    List<MahjongTile> uraDoraIndicators();

    RoundResolution lastResolution();

    default ReactionOptions availableReactions(UUID playerId) {
        return null;
    }

    default boolean hasPendingReaction() {
        return false;
    }

    default String pendingReactionFingerprint() {
        return "";
    }

    default String pendingReactionTileKey() {
        return "";
    }

    default boolean isCurrentPlayer(UUID playerId) {
        return false;
    }

    default boolean canSelectHandTile(UUID playerId, int tileIndex) {
        return false;
    }

    default boolean canDeclareRiichi(UUID playerId) {
        return false;
    }

    default boolean canDeclareKan(UUID playerId) {
        return false;
    }

    default boolean canDeclareKyuushu(UUID playerId) {
        return false;
    }

    default List<Integer> suggestedRiichiIndices(UUID playerId) {
        return List.of();
    }

    default List<String> suggestedKanTiles(UUID playerId) {
        return List.of();
    }

    default RiichiRoundEngine asRiichiEngine() {
        return null;
    }
}
