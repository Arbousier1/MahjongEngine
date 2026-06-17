package top.ellan.mahjong.table.core;

import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.scene.MeldView;
import top.ellan.mahjong.riichi.ReactionResponse;
import top.ellan.mahjong.riichi.ReactionType;
import top.ellan.mahjong.table.core.round.TableRoundController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SessionRoundActionCoordinator {
    private final TableSessionMutator session;

    SessionRoundActionCoordinator(TableSessionMutator session) {
        this.session = session;
    }

    boolean discard(UUID playerId, int tileIndex) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        MahjongTile discardedTile = this.session.handTileAtInternal(playerId, tileIndex);
        boolean result = controller.discard(playerId, tileIndex);
        return this.completeRoundAction(RoundActionResult.from(result)
            .clearSelectedHandTiles()
            .clearLastPublicAction()
            .publicDiscard(playerId, discardedTile));
    }

    boolean declareRiichi(UUID playerId, int tileIndex) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        MahjongTile discardedTile = this.session.handTileAtInternal(playerId, tileIndex);
        boolean result = controller.declareRiichi(playerId, tileIndex);
        return this.completeRoundAction(RoundActionResult.from(result)
            .clearSelectedHandTiles()
            .publicDiscard(playerId, discardedTile)
            .publicAction(playerId, "table.action.riichi"));
    }

    boolean declareTsumo(UUID playerId) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        boolean result = controller.declareTsumo(playerId);
        return this.completeRoundAction(RoundActionResult.from(result)
            .clearSelectedHandTiles()
            .publicAction(playerId, "table.action.tsumo"));
    }

    boolean declareKyuushuKyuuhai(UUID playerId) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        boolean result = controller.declareKyuushuKyuuhai(playerId);
        return this.completeRoundAction(RoundActionResult.from(result)
            .clearSelectedHandTiles()
            .publicAction(playerId, "table.action.kyuushu"));
    }

    boolean react(UUID playerId, ReactionResponse response) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        Map<UUID, List<MeldView>> meldsBefore = this.captureSeatMelds(controller);
        boolean result = controller.react(playerId, response);
        PublicAction publicAction = result
            ? this.resolvedReactionAction(controller, playerId, response, meldsBefore)
            : PublicAction.none();
        return this.completeRoundAction(RoundActionResult.from(result)
            .clearSelectedHandTiles()
            .reactionSound(response)
            .publicAction(publicAction.playerId(), publicAction.actionKey()));
    }

    boolean declareKan(UUID playerId, String tileName) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        List<MeldView> meldsBefore = playerId == null ? List.of() : List.copyOf(controller.fuuro(playerId));
        boolean result = controller.declareKan(playerId, tileName);
        List<MeldView> meldsAfter = result && playerId != null ? List.copyOf(controller.fuuro(playerId)) : List.of();
        return this.completeRoundAction(RoundActionResult.from(result)
            .clearSelectedHandTiles()
            .publicAction(playerId, this.resolveKanActionKey(meldsBefore, meldsAfter)));
    }

    private PublicAction resolvedReactionAction(
        TableRoundController controller,
        UUID actorId,
        ReactionResponse response,
        Map<UUID, List<MeldView>> meldsBefore
    ) {
        Map<UUID, List<MeldView>> meldsAfter = this.captureSeatMelds(controller);
        UUID claimPlayerId = this.findClaimPlayerId(meldsBefore, meldsAfter);
        if (claimPlayerId != null) {
            List<MeldView> before = meldsBefore.getOrDefault(claimPlayerId, List.of());
            List<MeldView> after = meldsAfter.getOrDefault(claimPlayerId, List.of());
            MeldView addedMeld = after.isEmpty() ? null : after.get(after.size() - 1);
            String actionKey = this.claimActionKey(addedMeld);
            if (!actionKey.isBlank()) {
                return new PublicAction(claimPlayerId, actionKey);
            }
            return PublicAction.none();
        }
        if (response != null && response.getType() == ReactionType.RON) {
            return new PublicAction(actorId, "table.action.ron");
        }
        return PublicAction.none();
    }

    private Map<UUID, List<MeldView>> captureSeatMelds(TableRoundController controller) {
        Map<UUID, List<MeldView>> melds = new LinkedHashMap<>();
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.session.playerAt(wind);
            if (playerId == null) {
                continue;
            }
            melds.put(playerId, List.copyOf(controller.fuuro(playerId)));
        }
        return melds;
    }

    private UUID findClaimPlayerId(Map<UUID, List<MeldView>> before, Map<UUID, List<MeldView>> after) {
        for (Map.Entry<UUID, List<MeldView>> entry : after.entrySet()) {
            UUID playerId = entry.getKey();
            int beforeCount = before.getOrDefault(playerId, List.of()).size();
            if (entry.getValue().size() > beforeCount) {
                return playerId;
            }
        }
        return null;
    }

    private String claimActionKey(MeldView meld) {
        if (meld == null || meld.tiles().isEmpty()) {
            return "";
        }
        if (meld.tiles().size() >= 4) {
            return "table.action.minkan";
        }
        return this.isUniformMeld(meld) ? "table.action.pon" : "table.action.chii";
    }

    private boolean isUniformMeld(MeldView meld) {
        MahjongTile first = meld.tiles().get(0);
        for (MahjongTile tile : meld.tiles()) {
            if (tile != first) {
                return false;
            }
        }
        return true;
    }

    private String resolveKanActionKey(List<MeldView> before, List<MeldView> after) {
        if (this.isAddedKanUpgrade(before, after)) {
            return "table.action.kakan";
        }
        if (after.size() > before.size()) {
            return "table.action.ankan";
        }
        return "";
    }

    private boolean isAddedKanUpgrade(List<MeldView> before, List<MeldView> after) {
        int compare = Math.min(before.size(), after.size());
        for (int i = 0; i < compare; i++) {
            MeldView previous = before.get(i);
            MeldView current = after.get(i);
            boolean previousAdded = previous != null && previous.hasAddedKanTile();
            boolean currentAdded = current != null && current.hasAddedKanTile();
            if (!previousAdded && currentAdded) {
                return true;
            }
            int previousSize = previous == null ? 0 : previous.tiles().size();
            int currentSize = current == null ? 0 : current.tiles().size();
            if (currentSize > previousSize) {
                return true;
            }
        }
        if (after.size() <= before.size()) {
            return false;
        }
        MeldView newest = after.get(after.size() - 1);
        return newest != null && newest.hasAddedKanTile();
    }

    private void renderAndFlushViewerPresentation() {
        this.session.render();
        this.session.flushViewerPresentationIfNeededInternal();
    }

    private boolean completeRoundAction(RoundActionResult result) {
        if (result.changed()) {
            if (result.shouldClearSelectedHandTiles()) {
                this.session.clearSelectedHandTilesInternal();
            }
            if (result.shouldClearLastPublicAction()) {
                this.session.clearLastPublicActionInternal();
            }
            if (result.publicDiscardTile() != null) {
                this.session.rememberPublicDiscardInternal(result.publicDiscardPlayerId(), result.publicDiscardTile());
            }
            if (result.publicActionKey() != null && !result.publicActionKey().isBlank()) {
                this.session.rememberPublicActionInternal(result.publicActionPlayerId(), result.publicActionKey());
            }
            if (result.reactionSound() != null) {
                this.session.playReactionSoundInternal(result.reactionSound());
            }
        }
        this.renderAndFlushViewerPresentation();
        return result.changed();
    }

    private record PublicAction(UUID playerId, String actionKey) {
        private static PublicAction none() {
            return new PublicAction(null, "");
        }
    }

    private static final class RoundActionResult {
        private final boolean changed;
        private boolean clearSelectedHandTiles;
        private boolean clearLastPublicAction;
        private UUID publicDiscardPlayerId;
        private MahjongTile publicDiscardTile;
        private UUID publicActionPlayerId;
        private String publicActionKey;
        private ReactionResponse reactionSound;

        private RoundActionResult(boolean changed) {
            this.changed = changed;
        }

        static RoundActionResult from(boolean changed) {
            return new RoundActionResult(changed);
        }

        RoundActionResult clearSelectedHandTiles() {
            this.clearSelectedHandTiles = true;
            return this;
        }

        RoundActionResult clearLastPublicAction() {
            this.clearLastPublicAction = true;
            return this;
        }

        RoundActionResult publicDiscard(UUID playerId, MahjongTile tile) {
            this.publicDiscardPlayerId = playerId;
            this.publicDiscardTile = tile;
            return this;
        }

        RoundActionResult publicAction(UUID playerId, String actionKey) {
            this.publicActionPlayerId = playerId;
            this.publicActionKey = actionKey;
            return this;
        }

        RoundActionResult reactionSound(ReactionResponse response) {
            this.reactionSound = response;
            return this;
        }

        boolean changed() {
            return this.changed;
        }

        boolean shouldClearSelectedHandTiles() {
            return this.clearSelectedHandTiles;
        }

        boolean shouldClearLastPublicAction() {
            return this.clearLastPublicAction;
        }

        UUID publicDiscardPlayerId() {
            return this.publicDiscardPlayerId;
        }

        MahjongTile publicDiscardTile() {
            return this.publicDiscardTile;
        }

        UUID publicActionPlayerId() {
            return this.publicActionPlayerId;
        }

        String publicActionKey() {
            return this.publicActionKey;
        }

        ReactionResponse reactionSound() {
            return this.reactionSound;
        }
    }
}
