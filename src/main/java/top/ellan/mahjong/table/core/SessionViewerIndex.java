package top.ellan.mahjong.table.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.bukkit.entity.Player;

final class SessionViewerIndex {
    private final MahjongTableSession session;

    SessionViewerIndex(MahjongTableSession session) {
        this.session = session;
    }

    List<Player> viewers() {
        return this.collectOnlineViewers(null, (viewerId, player) -> player);
    }

    List<UUID> viewerIdsExcluding(UUID excludedPlayerId) {
        return List.copyOf(this.collectOnlineViewers(excludedPlayerId, (viewerId, player) -> viewerId));
    }

    String viewerMembershipSignatureFor(UUID excludedPlayerId) {
        StringBuilder builder = new StringBuilder(96);
        this.forEachOnlineViewer(excludedPlayerId, (viewerId, player) -> builder.append(viewerId).append(';'));
        return builder.toString();
    }

    private void forEachOnlineViewer(UUID excludedPlayerId, BiConsumer<UUID, Player> consumer) {
        for (UUID seatPlayerId : this.session.seatIds()) {
            this.acceptOnlineViewer(seatPlayerId, excludedPlayerId, consumer);
        }
        for (UUID spectatorId : this.session.spectators()) {
            this.acceptOnlineViewer(spectatorId, excludedPlayerId, consumer);
        }
    }

    private void acceptOnlineViewer(UUID viewerId, UUID excludedPlayerId, BiConsumer<UUID, Player> consumer) {
        if (viewerId == null || viewerId.equals(excludedPlayerId)) {
            return;
        }
        Player player = this.session.onlinePlayer(viewerId);
        if (player != null) {
            consumer.accept(viewerId, player);
        }
    }

    private <T> List<T> collectOnlineViewers(UUID excludedPlayerId, BiFunction<UUID, Player, T> mapper) {
        List<T> values = new ArrayList<>(this.session.size() + this.session.spectatorCount());
        this.forEachOnlineViewer(excludedPlayerId, (viewerId, player) -> values.add(mapper.apply(viewerId, player)));
        return values;
    }
}

