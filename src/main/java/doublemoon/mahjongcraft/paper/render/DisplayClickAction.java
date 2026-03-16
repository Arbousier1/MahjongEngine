package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import java.util.UUID;

public record DisplayClickAction(ActionType actionType, String tableId, UUID ownerId, int tileIndex, SeatWind seatWind) {
    public static DisplayClickAction handTile(String tableId, UUID ownerId, int tileIndex) {
        return new DisplayClickAction(ActionType.HAND_TILE, tableId, ownerId, tileIndex, null);
    }

    public static DisplayClickAction joinSeat(String tableId, SeatWind seatWind) {
        return new DisplayClickAction(ActionType.JOIN_SEAT, tableId, null, -1, seatWind);
    }

    public enum ActionType {
        HAND_TILE,
        JOIN_SEAT
    }
}
