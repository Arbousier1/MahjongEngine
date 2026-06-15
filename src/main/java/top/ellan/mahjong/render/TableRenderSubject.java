package top.ellan.mahjong.render;

import top.ellan.mahjong.compat.CraftEngineService;
import top.ellan.mahjong.config.PluginSettings;
import top.ellan.mahjong.i18n.MessageService;
import top.ellan.mahjong.model.MahjongTile;
import top.ellan.mahjong.model.MahjongVariant;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.scene.MeldView;
import top.ellan.mahjong.riichi.model.ScoringStick;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface TableRenderSubject {
    Plugin plugin();

    CraftEngineService craftEngine();

    PluginSettings settings();

    MessageService messages();

    String id();

    Location center();

    MahjongVariant currentVariant();

    boolean isStarted();

    boolean isRoundFinished();

    boolean isRoundStartInProgress();

    int remainingWallCount();

    int kanCount();

    int dicePoints();

    int roundIndex();

    int honbaCount();

    SeatWind dealerSeat();

    SeatWind currentSeat();

    SeatWind openDoorSeat();

    UUID playerAt(SeatWind wind);

    SeatWind seatOf(UUID playerId);

    List<Player> viewers();

    Locale publicLocale();

    String waitingDisplaySummary();

    String ruleDisplaySummary();

    String publicCenterText();

    UUID lastPublicDiscardPlayerIdValue();

    MahjongTile lastPublicDiscardTile();

    String publicSeatStatus(SeatWind wind);

    String displayName(UUID playerId);

    String displayName(UUID playerId, Locale locale);

    int points(UUID playerId);

    boolean isRiichi(UUID playerId);

    boolean isReady(UUID playerId);

    boolean isQueuedToLeave(UUID playerId);

    boolean isSpectator(UUID playerId);

    int selectedHandTileIndex(UUID playerId);

    int riichiDiscardIndex(UUID playerId);

    int stickLayoutCount(SeatWind wind);

    List<MahjongTile> hand(UUID playerId);

    List<MahjongTile> discards(UUID playerId);

    List<MeldView> fuuro(UUID playerId);

    List<ScoringStick> scoringSticks(UUID playerId);

    List<ScoringStick> cornerSticks(SeatWind wind);

    List<MahjongTile> doraIndicators();

    Component viewerOverlay(Player viewer);

    Component spectatorSeatOverlay(Player viewer, SeatWind wind);
}
