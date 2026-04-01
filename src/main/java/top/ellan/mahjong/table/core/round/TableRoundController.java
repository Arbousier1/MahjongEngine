package top.ellan.mahjong.table.core.round;

import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.scene.MeldView;
import top.ellan.mahjong.riichi.ReactionOptions;
import top.ellan.mahjong.riichi.ReactionResponse;
import top.ellan.mahjong.riichi.RiichiDiscardSuggestion;
import top.ellan.mahjong.riichi.RoundResolution;
import top.ellan.mahjong.riichi.model.MahjongRule;
import top.ellan.mahjong.riichi.model.ScoringStick;
import top.ellan.mahjong.table.core.MahjongVariant;
import java.util.List;
import java.util.UUID;

public interface TableRoundController {
    interface VariantVisitor<T> {
        T visitRiichi(RiichiTableRoundController controller);

        T visitGb(GbTableRoundController controller);
    }

    <T> T accept(VariantVisitor<T> visitor);

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

    default boolean canDeclareConcealedKan(UUID playerId) {
        return false;
    }

    default boolean canDeclareAddedKan(UUID playerId) {
        return false;
    }

    default boolean canDeclareKyuushu(UUID playerId) {
        return false;
    }

    default boolean canDeclareTsumo(UUID playerId) {
        return false;
    }

    default List<Integer> suggestedRiichiIndices(UUID playerId) {
        return List.of();
    }

    default List<String> suggestedKanTiles(UUID playerId) {
        return List.of();
    }

    default List<String> suggestedConcealedKanTiles(UUID playerId) {
        return List.of();
    }

    default List<String> suggestedAddedKanTiles(UUID playerId) {
        return List.of();
    }

    default List<String> suggestedDiscardTiles(UUID playerId) {
        return List.of();
    }

    default List<RiichiDiscardSuggestion> suggestedDiscardSuggestions(UUID playerId) {
        return List.of();
    }
}

