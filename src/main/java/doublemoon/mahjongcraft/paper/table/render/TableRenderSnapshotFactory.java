package doublemoon.mahjongcraft.paper.table.render;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TableRenderSnapshotFactory {
    public MahjongTableSession.RenderSnapshot create(MahjongTableSession session, long version, long cancellationNonce) {
        Location tableCenter = session.center();
        boolean started = session.isStarted();
        List<UUID> onlineViewerIds = session.viewers().stream()
            .map(Player::getUniqueId)
            .distinct()
            .toList();
        Set<UUID> onlineViewerIdSet = new HashSet<>(onlineViewerIds);
        Map<UUID, String> viewerMembershipSignatures = new HashMap<>();
        Map<UUID, List<UUID>> viewerIdsExcluding = new HashMap<>();
        for (UUID viewerId : onlineViewerIds) {
            viewerMembershipSignatures.put(viewerId, this.viewerMembershipSignature(onlineViewerIds, viewerId));
            viewerIdsExcluding.put(viewerId, this.viewerIdsExcluding(onlineViewerIds, viewerId));
        }
        EnumMap<SeatWind, MahjongTableSession.SeatRenderSnapshot> seats = new EnumMap<>(SeatWind.class);
        for (SeatWind wind : SeatWind.values()) {
            seats.put(wind, this.captureSeatSnapshot(session, wind, onlineViewerIdSet, viewerMembershipSignatures, viewerIdsExcluding));
        }
        return new MahjongTableSession.RenderSnapshot(
            version,
            cancellationNonce,
            Objects.toString(tableCenter.getWorld() == null ? null : tableCenter.getWorld().getName(), ""),
            tableCenter.getX(),
            tableCenter.getY(),
            tableCenter.getZ(),
            started,
            session.isRoundFinished(),
            session.remainingWallCount(),
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
            started ? List.copyOf(session.doraIndicators()) : List.of(),
            seats
        );
    }

    private MahjongTableSession.SeatRenderSnapshot captureSeatSnapshot(
        MahjongTableSession session,
        SeatWind wind,
        Set<UUID> onlineViewerIdSet,
        Map<UUID, String> viewerMembershipSignatures,
        Map<UUID, List<UUID>> viewerIdsExcluding
    ) {
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
            occupied && onlineViewerIdSet.contains(playerId),
            occupied ? viewerMembershipSignatures.getOrDefault(playerId, "") : "",
            occupied ? session.selectedHandTileIndex(playerId) : -1,
            occupied ? session.riichiDiscardIndex(playerId) : -1,
            session.stickLayoutCount(wind),
            occupied ? viewerIdsExcluding.getOrDefault(playerId, List.of()) : List.of(),
            occupied ? session.hand(playerId) : List.of(),
            occupied ? session.discards(playerId) : List.of(),
            occupied ? session.fuuro(playerId) : List.of(),
            occupied ? session.scoringSticks(playerId) : List.of(),
            session.cornerSticks(wind)
        );
    }

    private String viewerMembershipSignature(List<UUID> onlineViewerIds, UUID excludedPlayerId) {
        StringBuilder builder = new StringBuilder(onlineViewerIds.size() * 36);
        for (UUID viewerId : onlineViewerIds) {
            if (!viewerId.equals(excludedPlayerId)) {
                builder.append(viewerId);
            }
        }
        return builder.toString();
    }

    private List<UUID> viewerIdsExcluding(List<UUID> onlineViewerIds, UUID excludedPlayerId) {
        return onlineViewerIds.stream()
            .filter(viewerId -> !viewerId.equals(excludedPlayerId))
            .toList();
    }
}

