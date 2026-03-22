package doublemoon.mahjongcraft.paper.table.core;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public record TableRenderSnapshot(
    long version,
    long cancellationNonce,
    String worldName,
    double centerX,
    double centerY,
    double centerZ,
    boolean started,
    boolean gameFinished,
    int remainingWallCount,
    int kanCount,
    int dicePoints,
    int roundIndex,
    int honbaCount,
    SeatWind dealerSeat,
    SeatWind currentSeat,
    SeatWind openDoorSeat,
    String waitingDisplaySummary,
    String ruleDisplaySummary,
    String publicCenterText,
    UUID lastPublicDiscardPlayerId,
    doublemoon.mahjongcraft.paper.model.MahjongTile lastPublicDiscardTile,
    List<doublemoon.mahjongcraft.paper.model.MahjongTile> doraIndicators,
    EnumMap<SeatWind, TableSeatRenderSnapshot> seats
) {
    public TableSeatRenderSnapshot seat(SeatWind wind) {
        return this.seats.get(wind);
    }
}
