package top.ellan.mahjong.render.display;

import top.ellan.mahjong.model.SeatWind;
import java.util.UUID;

public record DisplayClickAction(ActionType actionType, String tableId, UUID ownerId, int tileIndex, SeatWind seatWind, String command) {
    public static DisplayClickAction handTile(String tableId, UUID ownerId, int tileIndex) {
        return new DisplayClickAction(ActionType.HAND_TILE, tableId, ownerId, tileIndex, null, null);
    }

    public static DisplayClickAction joinSeat(String tableId, SeatWind seatWind) {
        return new DisplayClickAction(ActionType.JOIN_SEAT, tableId, null, -1, seatWind, null);
    }

    public static DisplayClickAction toggleReady(String tableId, SeatWind seatWind) {
        return new DisplayClickAction(ActionType.TOGGLE_READY, tableId, null, -1, seatWind, null);
    }

    public static DisplayClickAction playerCommand(String tableId, UUID ownerId, String command) {
        return new DisplayClickAction(ActionType.PLAYER_COMMAND, tableId, ownerId, -1, null, command);
    }

    public enum ActionType {
        HAND_TILE,
        JOIN_SEAT,
        TOGGLE_READY,
        PLAYER_COMMAND
    }
}

