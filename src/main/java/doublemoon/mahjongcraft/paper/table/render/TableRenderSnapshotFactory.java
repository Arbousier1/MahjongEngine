package doublemoon.mahjongcraft.paper.table.render;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;

public final class TableRenderSnapshotFactory {
    public MahjongTableSession.RenderSnapshot create(MahjongTableSession session, long version, long cancellationNonce) {
        Location tableCenter = session.center();
        EnumMap<SeatWind, MahjongTableSession.SeatRenderSnapshot> seats = new EnumMap<>(SeatWind.class);
        for (SeatWind wind : SeatWind.values()) {
            seats.put(wind, this.captureSeatSnapshot(session, wind));
        }
        return new MahjongTableSession.RenderSnapshot(
            version,
            cancellationNonce,
            Objects.toString(tableCenter.getWorld() == null ? null : tableCenter.getWorld().getName(), ""),
            tableCenter.getX(),
            tableCenter.getY(),
            tableCenter.getZ(),
            session.isStarted(),
            session.isRoundFinished(),
            session.remainingWall().size(),
            session.kanCount(),
            session.dicePoints(),
            session.roundIndex(),
            session.honbaCount(),
            session.dealerSeat(),
            session.currentSeat(),
            session.openDoorSeat(),
            session.waitingDisplaySummary(),
            session.ruleDisplaySummary(),
            session.publicCenterText(),
            session.lastPublicDiscardPlayerIdValue(),
            session.lastPublicDiscardTile(),
            List.copyOf(session.doraIndicators()),
            seats
        );
    }

    private MahjongTableSession.SeatRenderSnapshot captureSeatSnapshot(MahjongTableSession session, SeatWind wind) {
        UUID playerId = session.playerAt(wind);
        boolean occupied = playerId != null;
        return new MahjongTableSession.SeatRenderSnapshot(
            wind,
            playerId,
            session.displayName(playerId),
            session.publicSeatStatus(wind),
            occupied ? session.points(playerId) : 0,
            occupied && session.isRiichi(playerId),
            occupied && session.isReady(playerId),
            occupied && session.isQueuedToLeave(playerId),
            occupied && session.onlinePlayer(playerId) != null,
            occupied ? session.viewerMembershipSignatureFor(playerId) : "",
            occupied ? session.selectedHandTileIndex(playerId) : -1,
            occupied ? session.riichiDiscardIndex(playerId) : -1,
            session.stickLayoutCount(wind),
            occupied ? session.viewerIdsExcluding(playerId) : List.of(),
            occupied ? session.hand(playerId) : List.of(),
            occupied ? session.discards(playerId) : List.of(),
            occupied ? session.fuuro(playerId) : List.of(),
            occupied ? session.scoringSticks(playerId) : List.of(),
            session.cornerSticks(wind)
        );
    }
}

