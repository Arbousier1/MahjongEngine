package doublemoon.mahjongcraft.paper.table.core;

import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.table.core.round.TableRoundController;
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
        doublemoon.mahjongcraft.paper.model.MahjongTile discardedTile = this.session.handTileAtInternal(playerId, tileIndex);
        boolean result = controller.discard(playerId, tileIndex);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
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
        doublemoon.mahjongcraft.paper.model.MahjongTile discardedTile = this.session.handTileAtInternal(playerId, tileIndex);
        boolean result = controller.declareRiichi(playerId, tileIndex);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
            this.session.rememberPublicDiscardInternal(playerId, discardedTile);
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
        }
        this.session.render();
        return result;
    }

    boolean react(UUID playerId, ReactionResponse response) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        boolean result = controller.react(playerId, response);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
            this.session.playReactionSoundInternal(response);
        }
        this.session.render();
        return result;
    }

    boolean declareKan(UUID playerId, String tileName) {
        TableRoundController controller = this.session.roundControllerInternal();
        if (controller == null) {
            return false;
        }
        boolean result = controller.declareKan(playerId, tileName);
        if (result) {
            this.session.clearSelectedHandTilesInternal();
        }
        this.session.render();
        return result;
    }
}
