package top.ellan.mahjong.table.core;

import top.ellan.mahjong.model.SeatWind;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Membership port for a mahjong table session, exposing player/spectator
 * join/leave operations, seat queries, and readiness state. Extends
 * {@link TableIdentityPort} so that identity queries are also available.
 */
public interface TableMembershipPort extends TableIdentityPort {
    boolean addPlayer(Player player);

    boolean addPlayer(Player player, SeatWind wind);

    boolean addSpectator(Player player);

    boolean removeSpectator(UUID playerId);

    boolean addBot();

    boolean replaceBotWithPlayer(Player player, SeatWind wind);

    boolean removeBot();

    boolean removePlayer(UUID playerId);

    boolean contains(UUID playerId);

    boolean isEmpty();

    int size();

    int botCount();

    int spectatorCount();

    List<UUID> players();

    Set<UUID> spectators();

    List<UUID> seatIds();

    boolean isOwner(UUID playerId);

    void setOwner(UUID ownerId);

    SeatWind seatOf(UUID playerId);

    boolean isBot(UUID playerId);

    boolean isSpectator(UUID playerId);

    boolean isReady(UUID playerId);

    int readyCount();

    boolean queueLeaveAfterRound(UUID playerId);

    boolean isQueuedToLeave(UUID playerId);

    List<Player> viewers();

    List<UUID> viewerIdsExcluding(UUID excludedPlayerId);

    String viewerMembershipSignatureFor(UUID excludedPlayerId);

    UUID playerAt(SeatWind wind);

    Location seatAnchorLocation(SeatWind wind);

    float seatFacingYaw(SeatWind wind);

    Player onlinePlayer(UUID playerId);

    void promptPlayersToReady();

    String playerName(SeatWind wind);

    String displayName(UUID playerId);

    String displayName(UUID playerId, Locale locale);

    String seatDisplayName(SeatWind wind, Locale locale);

    boolean isStarted();
}
