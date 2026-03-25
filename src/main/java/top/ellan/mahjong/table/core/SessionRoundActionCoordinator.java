package top.ellan.mahjong.table.core;

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
    private final MahjongTableSession session;

    SessionRoundActionCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    boolean discard(UUID playerId, int tileIndex) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        top.ellan.mahjong.model.MahjongTile discardedTile = this.session.handTileAtInternal(playerId, tileIndex);
        boolean result = controller.discard(playerId, tileIndex);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
            this.session.clearLastPublicActionInternal();
            this.session.rememberPublicDiscardInternal(playerId, discardedTile);
        }
        this.session.render();
        return result;
    }

    boolean declareRiichi(UUID playerId, int tileIndex) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        top.ellan.mahjong.model.MahjongTile discardedTile = this.session.handTileAtInternal(playerId, tileIndex);
        boolean result = controller.declareRiichi(playerId, tileIndex);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
            this.session.rememberPublicDiscardInternal(playerId, discardedTile);
            this.session.rememberPublicActionInternal(playerId, "table.action.riichi");
        }
        this.session.render();
        return result;
    }

    boolean declareTsumo(UUID playerId) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        boolean result = controller.declareTsumo(playerId);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
            this.session.rememberPublicActionInternal(playerId, "table.action.tsumo");
        }
        this.session.render();
        return result;
    }

    boolean declareKyuushuKyuuhai(UUID playerId) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        boolean result = controller.declareKyuushuKyuuhai(playerId);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
            this.session.rememberPublicActionInternal(playerId, "table.action.kyuushu");
        }
        this.session.render();
        return result;
    }

    boolean react(UUID playerId, ReactionResponse response) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        Map<UUID, List<MeldView>> meldsBefore = this.captureSeatMelds(controller);
        boolean result = controller.react(playerId, response);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
            this.session.playReactionSoundInternal(response);
            this.recordResolvedReactionAction(controller, playerId, response, meldsBefore);
        }
        this.session.render();
        return result;
    }

    boolean declareKan(UUID playerId, String tileName) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        List<MeldView> meldsBefore = playerId == null ? List.of() : List.copyOf(controller.fuuro(playerId));
        boolean result = controller.declareKan(playerId, tileName);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
            List<MeldView> meldsAfter = playerId == null ? List.of() : List.copyOf(controller.fuuro(playerId));
            String actionKey = this.resolveKanActionKey(meldsBefore, meldsAfter);
            if (!actionKey.isBlank()) {
                this.session.rememberPublicActionInternal(playerId, actionKey);
            }
        }
        this.session.render();
        return result;
    }

    private void recordResolvedReactionAction(
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
                this.session.rememberPublicActionInternal(claimPlayerId, actionKey);
            }
            return;
        }
        if (response != null && response.getType() == ReactionType.RON) {
            this.session.rememberPublicActionInternal(actorId, "table.action.ron");
        }
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
        top.ellan.mahjong.model.MahjongTile first = meld.tiles().get(0);
        for (top.ellan.mahjong.model.MahjongTile tile : meld.tiles()) {
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
}

