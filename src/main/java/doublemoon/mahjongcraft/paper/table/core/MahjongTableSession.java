package doublemoon.mahjongcraft.paper.table.core;

import doublemoon.mahjongcraft.paper.bootstrap.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.gb.runtime.GbNativeRulesGateway;
import doublemoon.mahjongcraft.paper.i18n.LocalizedMessages;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.scene.MeldView;
import doublemoon.mahjongcraft.paper.render.layout.TableRenderLayout;
import doublemoon.mahjongcraft.paper.render.scene.TableRenderer;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.RiichiPlayerState;
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongSoulScoring;
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick;
import doublemoon.mahjongcraft.paper.table.core.round.GbTableRoundController;
import doublemoon.mahjongcraft.paper.table.core.round.OpeningDiceRoll;
import doublemoon.mahjongcraft.paper.table.core.round.RiichiTableRoundController;
import doublemoon.mahjongcraft.paper.table.core.round.TableRoundController;
import doublemoon.mahjongcraft.paper.table.presentation.TableDiceAnimationCoordinator;
import doublemoon.mahjongcraft.paper.table.presentation.TablePlayerFeedbackCoordinator;
import doublemoon.mahjongcraft.paper.table.presentation.TablePublicTextFactory;
import doublemoon.mahjongcraft.paper.table.presentation.TableStateSoundCoordinator;
import doublemoon.mahjongcraft.paper.table.presentation.TableViewerPresentationCoordinator;
import doublemoon.mahjongcraft.paper.table.presentation.TableViewerSnapshotFactory;
import doublemoon.mahjongcraft.paper.table.render.TableRegionDisplayCoordinator;
import doublemoon.mahjongcraft.paper.table.render.TableRegionFingerprintService;
import doublemoon.mahjongcraft.paper.table.render.TableRenderCoordinator;
import doublemoon.mahjongcraft.paper.table.render.TableRenderInspectCoordinator;
import doublemoon.mahjongcraft.paper.table.render.TableRenderSnapshotFactory;
import doublemoon.mahjongcraft.paper.table.runtime.BotActionScheduler;
import doublemoon.mahjongcraft.paper.runtime.PluginTask;
import doublemoon.mahjongcraft.paper.table.runtime.TableLifecycleCoordinator;
import doublemoon.mahjongcraft.paper.ui.SettlementUi;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class MahjongTableSession {
    private final MahjongPaperPlugin plugin;
    private final String id;
    private final Location center;
    private final boolean persistentRoom;
    private final TableParticipantRegistry participants = new TableParticipantRegistry();
    private final TableRenderer renderer = new TableRenderer();
    private final TableRenderSnapshotFactory renderSnapshotFactory = new TableRenderSnapshotFactory();
    private final TableRegionFingerprintService regionFingerprintService = new TableRegionFingerprintService();
    private final Map<UUID, Integer> selectedHandTileIndices = new HashMap<>();
    private MahjongRule configuredRule;
    private TableRoundController roundController;
    private boolean roundStartInProgress;
    private doublemoon.mahjongcraft.paper.model.MahjongTile lastPublicDiscardTile;
    private UUID lastPublicDiscardPlayerId;
    private PluginTask botTask;
    private final TableRenderCoordinator renderCoordinator;
    private final TableViewerPresentationCoordinator viewerPresentation;
    private final TableRegionDisplayCoordinator regionDisplayCoordinator;
    private final TableRenderInspectCoordinator renderInspectCoordinator;
    private final TableLifecycleCoordinator lifecycleCoordinator;
    private final TableViewerSnapshotFactory viewerSnapshotFactory;
    private final TableDiceAnimationCoordinator diceAnimationCoordinator;
    private final TablePlayerFeedbackCoordinator playerFeedbackCoordinator;
    private final TableStateSoundCoordinator stateSoundCoordinator;
    private final TablePublicTextFactory publicTextFactory;
    private final SessionMessaging sessionMessaging;
    private final SessionViewerIndex viewerIndex;
    private final SessionRoundLifecycle roundLifecycle;
    private MahjongVariant configuredVariant;

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, Player owner) {
        this(plugin, id, center, MahjongVariant.RIICHI, majsoulRule(MahjongRule.GameLength.TWO_WIND), true);
        this.addPlayer(owner);
    }

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, boolean persistentRoom) {
        this(plugin, id, center, MahjongVariant.RIICHI, majsoulRule(MahjongRule.GameLength.TWO_WIND), persistentRoom);
    }

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, Player owner, boolean persistentRoom) {
        this(plugin, id, center, MahjongVariant.RIICHI, majsoulRule(MahjongRule.GameLength.TWO_WIND), persistentRoom);
        this.addPlayer(owner);
    }

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, MahjongRule configuredRule, boolean persistentRoom) {
        this(plugin, id, center, MahjongVariant.RIICHI, configuredRule, persistentRoom);
    }

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, MahjongVariant configuredVariant, MahjongRule configuredRule, boolean persistentRoom) {
        this.plugin = plugin;
        this.id = id;
        this.center = normalizedTableCenter(center);
        this.configuredVariant = configuredVariant == null ? MahjongVariant.RIICHI : configuredVariant;
        this.configuredRule = copyRule(configuredRule);
        this.persistentRoom = persistentRoom;
        this.renderCoordinator = new TableRenderCoordinator(this);
        this.viewerPresentation = new TableViewerPresentationCoordinator(this);
        this.regionDisplayCoordinator = new TableRegionDisplayCoordinator(this, this.regionFingerprintService);
        this.renderInspectCoordinator = new TableRenderInspectCoordinator(this);
        this.lifecycleCoordinator = new TableLifecycleCoordinator(this);
        this.viewerSnapshotFactory = new TableViewerSnapshotFactory(this);
        this.diceAnimationCoordinator = new TableDiceAnimationCoordinator(this);
        this.playerFeedbackCoordinator = new TablePlayerFeedbackCoordinator(this);
        this.stateSoundCoordinator = new TableStateSoundCoordinator(this);
        this.publicTextFactory = new TablePublicTextFactory(this);
        this.sessionMessaging = new SessionMessaging(this);
        this.viewerIndex = new SessionViewerIndex(this);
        this.roundLifecycle = new SessionRoundLifecycle();
    }

    public MahjongPaperPlugin plugin() {
        return this.plugin;
    }

    public String id() {
        return this.id;
    }

    public boolean isPersistentRoom() {
        return this.persistentRoom;
    }

    public Location center() {
        return normalizedTableCenter(this.center);
    }

    public Location seatAnchorLocation(SeatWind wind) {
        return wind == null ? this.center() : this.renderer.seatAnchorLocation(this, wind);
    }

    public float seatFacingYaw(SeatWind wind) {
        return wind == null ? 0.0F : this.renderer.seatFacingYaw(wind);
    }

    public MahjongRule configuredRuleSnapshot() {
        return copyRule(this.configuredRule);
    }

    public MahjongVariant configuredVariant() {
        return this.configuredVariant;
    }

    public boolean addPlayer(Player player) {
        for (SeatWind wind : SeatWind.values()) {
            if (this.playerAt(wind) == null) {
                return this.addPlayer(player, wind);
            }
        }
        return false;
    }

    public boolean addPlayer(Player player, SeatWind wind) {
        return this.participants.addPlayer(player.getUniqueId(), wind);
    }

    public boolean addSpectator(Player player) {
        return this.participants.addSpectator(player.getUniqueId(), this.currentRule().getSpectate());
    }

    public boolean removeSpectator(UUID playerId) {
        this.viewerPresentation.hideHud(playerId);
        return this.participants.removeSpectator(playerId);
    }

    public boolean addBot() {
        if (this.isStarted() || this.roundStartInProgress || this.size() >= 4) {
            return false;
        }
        UUID botId = this.participants.createNextBotId(this.id);
        if (!this.participants.addBot(botId)) {
            return false;
        }
        this.render();
        this.maybeStartRoundIfReady();
        return true;
    }

    public boolean removeBot() {
        if (this.isStarted() || this.roundStartInProgress) {
            return false;
        }
        UUID playerId = this.participants.removeLastBot();
        if (playerId == null) {
            return false;
        }
        this.playerFeedbackCoordinator.clearPlayerState(playerId);
        this.selectedHandTileIndices.remove(playerId);
        this.render();
        return true;
    }

    public boolean removePlayer(UUID playerId) {
        if (this.roundStartInProgress || (this.roundController != null && this.roundController.started())) {
            return false;
        }
        this.viewerPresentation.hideHud(playerId);
        this.playerFeedbackCoordinator.clearPlayerState(playerId);
        this.selectedHandTileIndices.remove(playerId);
        return this.participants.removePlayer(playerId);
    }

    public boolean contains(UUID playerId) {
        return this.participants.contains(playerId);
    }

    public boolean isEmpty() {
        return this.participants.isEmpty();
    }

    public int size() {
        return this.participants.size();
    }

    public int botCount() {
        return this.participants.botCount();
    }

    public int spectatorCount() {
        return this.participants.spectatorCount();
    }

    public List<UUID> players() {
        return this.participants.players();
    }

    public Set<UUID> spectators() {
        return this.participants.spectators();
    }

    public UUID owner() {
        return this.participants.owner();
    }

    public SeatWind seatOf(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (SeatWind wind : SeatWind.values()) {
            if (Objects.equals(this.playerAt(wind), playerId)) {
                return wind;
            }
        }
        return null;
    }

    public boolean isReady(UUID playerId) {
        return this.participants.isReady(playerId);
    }

    public int readyCount() {
        return this.participants.readyCount();
    }

    public boolean isQueuedToLeave(UUID playerId) {
        return this.participants.isQueuedToLeave(playerId);
    }

    public ReadyResult toggleReady(UUID playerId) {
        if (playerId == null || !this.contains(playerId) || this.isBot(playerId) || this.isStarted() || this.roundStartInProgress) {
            return ReadyResult.BLOCKED;
        }

        boolean nowReady = this.participants.toggleReady(playerId);

        if (nowReady && this.maybeStartRoundIfReady()) {
            return ReadyResult.STARTED;
        }

        this.render();
        return nowReady ? ReadyResult.READY : ReadyResult.UNREADY;
    }

    public boolean queueLeaveAfterRound(UUID playerId) {
        if (playerId == null || !this.contains(playerId) || !this.isStarted()) {
            return false;
        }
        this.participants.queueLeave(playerId);
        return true;
    }

    public void startRound() {
        if (this.roundStartInProgress) {
            return;
        }
        if (this.size() != 4) {
            throw new IllegalStateException("A table needs exactly 4 players");
        }

        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null || !this.isReady(playerId)) {
                throw new IllegalStateException("All seated players must be ready");
            }
        }

        this.cancelNextRoundCountdown();
        if (this.roundController == null || this.roundController.gameFinished() || this.roundController.variant() != this.currentVariant()) {
            this.roundController = this.createRoundController();
        }

        this.selectedHandTileIndices.clear();
        this.clearLastPublicDiscard();
        this.playerFeedbackCoordinator.resetForRoundStart();
        this.regionDisplayCoordinator.invalidateFingerprints();
        this.stateSoundCoordinator.resetForRoundStart();
        this.roundStartInProgress = true;
        OpeningDiceRoll diceRoll = OpeningDiceRoll.random();
        this.roundController.setPendingDiceRoll(diceRoll);
        if (!this.diceAnimationCoordinator.shouldAnimate()) {
            this.completeRoundStart();
            return;
        }
        this.diceAnimationCoordinator.start(diceRoll, this::completeRoundStart);
    }

    public boolean discard(UUID playerId, int tileIndex) {
        if (this.roundController == null) {
            return false;
        }
        doublemoon.mahjongcraft.paper.model.MahjongTile discardedTile = this.handTileAt(playerId, tileIndex);
        boolean result = this.roundController.discard(playerId, tileIndex);
        if (result) {
            this.selectedHandTileIndices.clear();
            this.rememberPublicDiscard(playerId, discardedTile);
        }
        this.render();
        return result;
    }

    public boolean declareRiichi(UUID playerId, int tileIndex) {
        if (this.roundController == null) {
            return false;
        }
        doublemoon.mahjongcraft.paper.model.MahjongTile discardedTile = this.handTileAt(playerId, tileIndex);
        boolean result = this.roundController.declareRiichi(playerId, tileIndex);
        if (result) {
            this.selectedHandTileIndices.clear();
            this.rememberPublicDiscard(playerId, discardedTile);
        }
        this.render();
        return result;
    }

    public boolean declareTsumo(UUID playerId) {
        if (this.roundController == null) {
            return false;
        }
        boolean result = this.roundController.declareTsumo(playerId);
        if (result) {
            this.selectedHandTileIndices.clear();
        }
        this.render();
        return result;
    }

    public boolean declareKyuushuKyuuhai(UUID playerId) {
        if (this.roundController == null) {
            return false;
        }
        boolean result = this.roundController.declareKyuushuKyuuhai(playerId);
        if (result) {
            this.selectedHandTileIndices.clear();
        }
        this.render();
        return result;
    }

    public boolean react(UUID playerId, ReactionResponse response) {
        if (this.roundController == null) {
            return false;
        }
        boolean result = this.roundController.react(playerId, response);
        if (result) {
            this.selectedHandTileIndices.clear();
            this.stateSoundCoordinator.playReactionSound(response);
        }
        this.render();
        return result;
    }

    public boolean declareKan(UUID playerId, String tileName) {
        if (this.roundController == null) {
            return false;
        }
        boolean result = this.roundController.declareKan(playerId, tileName);
        if (result) {
            this.selectedHandTileIndices.clear();
        }
        this.render();
        return result;
    }

    public void render() {
        this.renderCoordinator.render();
    }

    public void clearDisplays() {
        this.renderCoordinator.clearDisplays();
        this.diceAnimationCoordinator.clear();
    }

    public void applyRenderPrecompute(RenderPrecomputeResult result) {
        this.regionDisplayCoordinator.applyRenderPrecompute(result);
        this.viewerPresentation.flushIfNeeded();
    }

    public void inspectRender(Player viewer) {
        this.renderInspectCoordinator.inspectRender(viewer);
    }

    public void shutdown() {
        this.lifecycleCoordinator.shutdown();
    }

    public void forceEndMatch() {
        this.lifecycleCoordinator.forceEndMatch();
    }

    public void resetForServerStartup() {
        this.lifecycleCoordinator.resetForServerStartup();
    }

    public void cancelNextRoundCountdownForLifecycle() {
        this.cancelNextRoundCountdown();
    }

    public void shutdownRenderFlow() {
        this.renderCoordinator.shutdown();
    }

    public void shutdownViewerPresentation() {
        this.viewerPresentation.shutdown();
    }

    public void resetViewerPresentationForLifecycleChange() {
        this.viewerPresentation.resetForLifecycleChange();
    }

    public void clearFeedbackTracking() {
        this.selectedHandTileIndices.clear();
    }

    public void clearRoundTrackingState() {
        this.roundStartInProgress = false;
        this.diceAnimationCoordinator.clear();
        this.clearLastPublicDiscard();
        this.playerFeedbackCoordinator.resetState();
        this.stateSoundCoordinator.reset();
    }

    public void invalidateRenderFingerprints() {
        this.regionDisplayCoordinator.invalidateFingerprints();
    }

    public void clearReadyPlayersForLifecycle() {
        this.participants.clearReadyPlayers();
    }

    public void clearLeaveQueueForLifecycle() {
        this.participants.clearLeaveQueue();
    }

    public void clearSpectatorsForLifecycle() {
        this.participants.clearSpectators();
    }

    public void clearBotNamesForLifecycle() {
        this.participants.clearBotNames();
    }

    public void clearSeatAssignmentsForLifecycle() {
        this.participants.clearSeats();
    }

    public void clearEngineForLifecycle() {
        this.roundController = null;
    }

    public void cancelNextRoundCountdownForFeedback() {
        this.cancelNextRoundCountdown();
    }

    public void resetBotCounterForLifecycle() {
        this.participants.resetBotCounter();
    }

    public void resetReadyStateForNextRoundForLifecycle() {
        this.resetReadyStateForNextRound();
    }

    public void resetReadyStateForNextRoundForFeedback() {
        this.resetReadyStateForNextRound();
    }

    public void promptPlayersToReadyForFeedback() {
        this.promptPlayersToReady();
    }

    public SeatWind currentSeat() {
        if (this.roundController == null || !this.roundController.started()) {
            return SeatWind.EAST;
        }
        return this.roundController.currentSeat();
    }

    public UUID playerAt(SeatWind wind) {
        if (this.roundController != null && this.roundController.started()) {
            UUID startedSeat = this.roundController.playerAt(wind);
            if (startedSeat != null) {
                return startedSeat;
            }
        }
        return this.participants.playerAt(wind);
    }

    public String playerName(SeatWind wind) {
        return this.displayName(this.playerAt(wind));
    }

    public String displayName(UUID playerId) {
        return this.displayName(playerId, this.publicLocale());
    }

    public String displayName(UUID playerId, Locale locale) {
        if (playerId == null) {
            return this.plugin.messages().plain(locale, "common.empty");
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        if (this.participants.isBot(playerId)) {
            return this.botDisplayName(playerId, locale);
        }
        if (this.engine() != null) {
            RiichiPlayerState seatPlayer = this.engine().seatPlayer(playerId.toString());
            if (seatPlayer != null) {
                return seatPlayer.getDisplayName();
            }
        }
        return this.plugin.messages().plain(locale, "common.offline");
    }

    public boolean isBot(UUID playerId) {
        return this.participants.isBot(playerId);
    }

    public int points(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        if (this.roundController != null) {
            int roundPoints = this.roundController.points(playerId);
            if (roundPoints > 0) {
                return roundPoints;
            }
        }
        return this.contains(playerId) ? this.configuredRule.getStartingPoints() : 0;
    }

    public boolean isRiichi(UUID playerId) {
        return playerId != null && this.roundController != null && this.roundController.isRiichi(playerId);
    }

    public int dicePoints() {
        return this.roundController == null ? 0 : this.roundController.dicePoints();
    }

    public int kanCount() {
        return this.roundController == null ? 0 : this.roundController.kanCount();
    }

    public int roundIndex() {
        return this.roundController == null ? 0 : this.roundController.roundIndex();
    }

    public SeatWind openDoorSeat() {
        if (this.roundController == null) {
            return SeatWind.EAST;
        }
        return SeatWind.fromIndex(Math.floorMod(this.dicePoints() - 1 + this.roundIndex(), SeatWind.values().length));
    }

    public int honbaCount() {
        return this.roundController == null ? 0 : this.roundController.honbaCount();
    }

    public SeatWind roundWind() {
        return this.roundController == null ? SeatWind.EAST : this.roundController.roundWind();
    }

    public SeatWind dealerSeat() {
        return this.roundController == null ? SeatWind.EAST : this.roundController.dealerSeat();
    }

    public String waitingSummary() {
        return this.waitingSummary(this.publicLocale());
    }

    public String waitingSummary(Locale locale) {
        return this.publicTextFactory.waitingSummary(locale);
    }

    public String waitingDisplaySummary() {
        return this.waitingDisplaySummary(this.publicLocale());
    }

    public String waitingDisplaySummary(Locale locale) {
        return this.publicTextFactory.waitingDisplaySummary(locale);
    }

    public String ruleDisplaySummary() {
        return this.ruleDisplaySummary(this.publicLocale());
    }

    public String ruleDisplaySummary(Locale locale) {
        return this.publicTextFactory.ruleDisplaySummary(locale);
    }

    public String ruleSummary() {
        return this.ruleSummary(this.publicLocale());
    }

    public String ruleSummary(Locale locale) {
        return this.publicTextFactory.ruleSummary(locale);
    }

    public boolean setRuleOption(String key, String rawValue) {
        if (this.isStarted()) {
            return false;
        }
        try {
            switch (key.toLowerCase(Locale.ROOT)) {
                case "preset", "mode" -> {
                    if (!this.applyRulePreset(rawValue)) {
                        return false;
                    }
                }
                case "variant", "ruleset" -> {
                    MahjongVariant variant = MahjongVariant.valueOf(rawValue.toUpperCase(Locale.ROOT));
                    this.configuredVariant = variant;
                    this.configuredRule = variant == MahjongVariant.GB
                        ? gbRule()
                        : majsoulRule(MahjongRule.GameLength.TWO_WIND);
                }
                case "length" -> this.configuredRule.setLength(MahjongRule.GameLength.valueOf(rawValue.toUpperCase(Locale.ROOT)));
                case "thinkingtime", "thinking" -> this.configuredRule.setThinkingTime(MahjongRule.ThinkingTime.valueOf(rawValue.toUpperCase(Locale.ROOT)));
                case "minimumhan", "minhan" -> this.configuredRule.setMinimumHan(MahjongRule.MinimumHan.valueOf(rawValue.toUpperCase(Locale.ROOT)));
                case "spectate" -> this.configuredRule.setSpectate(Boolean.parseBoolean(rawValue));
                case "redfive" -> this.configuredRule.setRedFive(MahjongRule.RedFive.valueOf(rawValue.toUpperCase(Locale.ROOT)));
                case "opentanyao" -> this.configuredRule.setOpenTanyao(Boolean.parseBoolean(rawValue));
                case "localyaku" -> this.configuredRule.setLocalYaku(Boolean.parseBoolean(rawValue));
                case "startingpoints", "startpoints" -> this.configuredRule.setStartingPoints(Integer.parseInt(rawValue));
                case "minpointstowin", "goal" -> this.configuredRule.setMinPointsToWin(Integer.parseInt(rawValue));
                default -> {
                    return false;
                }
            }
            this.render();
            this.persistRoomMetadataIfNeeded();
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public List<String> ruleKeys() {
        return List.of("preset", "mode", "variant", "ruleset", "length", "thinkingTime", "minimumHan", "spectate", "redFive", "openTanyao", "localYaku", "startingPoints", "minPointsToWin");
    }

    public List<String> ruleValues(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "preset", "mode" -> List.of("MAJSOUL_TONPUU", "MAJSOUL_HANCHAN", "GB");
            case "variant", "ruleset" -> List.of("RIICHI", "GB");
            case "length" -> List.of("ONE_GAME", "EAST", "SOUTH", "TWO_WIND");
            case "thinkingtime", "thinking" -> List.of("VERY_SHORT", "SHORT", "NORMAL", "LONG", "VERY_LONG");
            case "minimumhan", "minhan" -> List.of("ONE", "TWO", "FOUR", "YAKUMAN");
            case "redfive" -> List.of("NONE", "THREE", "FOUR");
            case "spectate", "opentanyao", "localyaku" -> List.of("true", "false");
            case "startingpoints", "startpoints" -> List.of("25000", "30000", "35000");
            case "minpointstowin", "goal" -> List.of("30000", "35000", "40000");
            default -> List.of();
        };
    }

    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand(UUID playerId) {
        return playerId == null || this.roundController == null ? List.of() : this.roundController.hand(playerId);
    }

    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> discards(UUID playerId) {
        return playerId == null || this.roundController == null ? List.of() : this.roundController.discards(playerId);
    }

    public int riichiDiscardIndex(UUID playerId) {
        if (this.engine() == null) {
            return -1;
        }
        if (playerId == null) {
            return -1;
        }
        RiichiPlayerState player = this.engine().seatPlayer(playerId.toString());
        if (player == null || player.getRiichiSengenTile() == null) {
            return -1;
        }
        int displayIndex = player.getDiscardedTilesForDisplay().indexOf(player.getRiichiSengenTile());
        if (displayIndex >= 0) {
            return displayIndex;
        }

        int declaredIndex = player.getDiscardedTiles().indexOf(player.getRiichiSengenTile());
        if (declaredIndex < 0) {
            return -1;
        }

        for (int i = declaredIndex + 1; i < player.getDiscardedTiles().size(); i++) {
            int shiftedDisplayIndex = player.getDiscardedTilesForDisplay().indexOf(player.getDiscardedTiles().get(i));
            if (shiftedDisplayIndex >= 0) {
                return shiftedDisplayIndex;
            }
        }
        return -1;
    }

    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> remainingWall() {
        return this.roundController == null ? List.of() : this.roundController.remainingWall();
    }

    public int remainingWallCount() {
        return this.roundController == null ? 0 : this.roundController.remainingWallCount();
    }

    public List<MeldView> fuuro(UUID playerId) {
        return playerId == null || this.roundController == null ? List.of() : this.roundController.fuuro(playerId);
    }

    public List<ScoringStick> scoringSticks(UUID playerId) {
        return playerId == null || this.roundController == null ? List.of() : this.roundController.scoringSticks(playerId);
    }

    public int riichiPoolCount() {
        if (this.engine() == null) {
            return 0;
        }
        return this.engine().getSeats().stream().mapToInt(RiichiPlayerState::getRiichiStickAmount).sum();
    }

    public List<ScoringStick> cornerSticks(SeatWind wind) {
        List<ScoringStick> sticks = new ArrayList<>();
        if (this.dealerSeat() == wind) {
            for (int i = 0; i < this.honbaCount(); i++) {
                sticks.add(ScoringStick.P100);
            }
        }
        return List.copyOf(sticks);
    }

    public int stickLayoutCount(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        int total = playerId == null ? 0 : this.scoringSticks(playerId).size();
        if (this.dealerSeat() == wind) {
            total += this.honbaCount();
        }
        return total;
    }

    public Player onlinePlayer(UUID playerId) {
        return Bukkit.getPlayer(playerId);
    }

    public boolean isSpectator(UUID playerId) {
        return this.participants.spectatorIds().contains(playerId);
    }

    public List<Player> viewers() {
        return this.viewerIndex.viewers();
    }

    public List<UUID> viewerIdsExcluding(UUID excludedPlayerId) {
        return this.viewerIndex.viewerIdsExcluding(excludedPlayerId);
    }

    public String roundDisplay() {
        return this.roundDisplay(this.publicLocale());
    }

    public String roundDisplay(Locale locale) {
        return this.publicTextFactory.roundDisplay(locale);
    }

    public String dealerName() {
        return this.dealerName(this.publicLocale());
    }

    public String dealerName(Locale locale) {
        return this.publicTextFactory.dealerName(locale);
    }

    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> doraIndicators() {
        return this.roundController == null ? List.of() : this.roundController.doraIndicators();
    }

    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> uraDoraIndicators() {
        return this.roundController == null ? List.of() : this.roundController.uraDoraIndicators();
    }

    public doublemoon.mahjongcraft.paper.riichi.RoundResolution lastResolution() {
        return this.roundController == null ? null : this.roundController.lastResolution();
    }

    public boolean openSettlementUi(Player player) {
        if (this.lastResolution() == null) {
            return false;
        }
        SettlementUi.open(player, this);
        return true;
    }

    public Component stateSummary(Player player) {
        return this.viewerSnapshotFactory.createStateSummary(player);
    }

    public Component viewerOverlay(Player viewer) {
        return this.viewerSnapshotFactory.createViewerOverlay(viewer);
    }

    public Component spectatorSeatOverlay(Player viewer, SeatWind wind) {
        return this.spectatorSeatOverlay(this.plugin.messages().resolveLocale(viewer), wind);
    }

    private Component spectatorSeatOverlay(Locale locale, SeatWind wind) {
        return this.sessionMessaging.spectatorSeatOverlay(locale, wind);
    }

    public boolean isStarted() {
        return this.roundController != null && this.roundController.started();
    }

    public boolean isRoundStartInProgress() {
        return this.roundStartInProgress;
    }

    public RiichiRoundEngine engine() {
        return this.roundController == null ? null : this.roundController.asRiichiEngine();
    }

    public boolean hasRoundController() {
        return this.roundController != null;
    }

    public boolean isRoundFinished() {
        return this.roundController != null && this.roundController.gameFinished();
    }

    public String currentTurnDisplayName() {
        return this.roundController == null ? "" : this.roundController.currentPlayerDisplayName();
    }

    public doublemoon.mahjongcraft.paper.riichi.ReactionOptions availableReactions(UUID playerId) {
        return playerId == null || this.roundController == null ? null : this.roundController.availableReactions(playerId);
    }

    public boolean hasPendingReaction() {
        return this.roundController != null && this.roundController.hasPendingReaction();
    }

    public String pendingReactionFingerprint() {
        return this.roundController == null ? "" : this.roundController.pendingReactionFingerprint();
    }

    public String pendingReactionTileKey() {
        return this.roundController == null ? "" : this.roundController.pendingReactionTileKey();
    }

    public boolean canDeclareRiichi(UUID playerId) {
        return playerId != null && this.roundController != null && this.roundController.canDeclareRiichi(playerId);
    }

    public boolean canDeclareKan(UUID playerId) {
        return playerId != null && this.roundController != null && this.roundController.canDeclareKan(playerId);
    }

    public boolean canDeclareKyuushu(UUID playerId) {
        return playerId != null && this.roundController != null && this.roundController.canDeclareKyuushu(playerId);
    }

    public List<Integer> suggestedRiichiIndices(UUID playerId) {
        return playerId == null || this.roundController == null ? List.of() : this.roundController.suggestedRiichiIndices(playerId);
    }

    public List<String> suggestedKanTiles(UUID playerId) {
        return playerId == null || this.roundController == null ? List.of() : this.roundController.suggestedKanTiles(playerId);
    }

    public List<String> suggestedDiscardTiles(UUID playerId) {
        return playerId == null || this.roundController == null ? List.of() : this.roundController.suggestedDiscardTiles(playerId);
    }

    public List<doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion> suggestedDiscardSuggestions(UUID playerId) {
        return playerId == null || this.roundController == null ? List.of() : this.roundController.suggestedDiscardSuggestions(playerId);
    }

    public doublemoon.mahjongcraft.paper.gb.jni.GbTingResponse gbTingOptions(UUID playerId) {
        if (!(this.roundController instanceof GbTableRoundController gbController) || playerId == null) {
            return new doublemoon.mahjongcraft.paper.gb.jni.GbTingResponse(false, List.of(), "GB round is inactive.");
        }
        return gbController.tingOptions(playerId);
    }

    public boolean gbCanWinByTsumo(UUID playerId) {
        return this.roundController instanceof GbTableRoundController gbController && playerId != null && gbController.canWinByTsumo(playerId);
    }

    public int gbSuggestedDiscardIndex(UUID playerId) {
        return this.roundController instanceof GbTableRoundController gbController && playerId != null
            ? gbController.suggestedBotDiscardIndex(playerId)
            : -1;
    }

    public ReactionResponse gbSuggestedReaction(UUID playerId) {
        return this.roundController instanceof GbTableRoundController gbController && playerId != null
            ? gbController.suggestedBotReaction(playerId)
            : new ReactionResponse(doublemoon.mahjongcraft.paper.riichi.ReactionType.SKIP, null);
    }

    public String gbSuggestedKanTile(UUID playerId) {
        return this.roundController instanceof GbTableRoundController gbController && playerId != null
            ? gbController.suggestedBotKanTile(playerId)
            : null;
    }

    public void setBotTask(PluginTask botTask) {
        this.botTask = botTask;
    }

    public void cancelBotTask() {
        if (this.botTask != null) {
            this.botTask.cancel();
            this.botTask = null;
        }
    }

    public void tick() {
        this.renderCoordinator.restoreDisplaysIfNeeded();
        this.viewerPresentation.flushIfNeeded();
        if (this.roundStartInProgress || this.roundController == null || this.roundController.started() || this.roundController.lastResolution() == null) {
            return;
        }
        this.processDeferredLeaves();
    }

    private MahjongRule currentRule() {
        return this.roundController == null ? this.configuredRule : this.roundController.rule();
    }

    private TableRoundController createRoundController() {
        EnumMap<SeatWind, UUID> seats = new EnumMap<>(SeatWind.class);
        Map<UUID, String> displayNames = new HashMap<>();
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.participants.playerAt(wind);
            if (playerId == null) {
                throw new IllegalStateException("A table needs exactly 4 occupied seats");
            }
            seats.put(wind, playerId);
            displayNames.put(playerId, this.displayName(playerId));
        }
        if (this.currentVariant() == MahjongVariant.GB) {
            return new GbTableRoundController(this.copyRule(), seats, displayNames, new GbNativeRulesGateway());
        }
        List<RiichiPlayerState> players = new ArrayList<>(SeatWind.values().length);
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = seats.get(wind);
            players.add(new RiichiPlayerState(displayNames.get(playerId), playerId.toString(), !this.isBot(playerId)));
        }
        return new RiichiTableRoundController(new RiichiRoundEngine(players, this.copyRule()));
    }

    public MahjongVariant currentVariant() {
        return this.configuredVariant;
    }

    public boolean applyRulePreset(String rawValue) {
        switch (rawValue.toUpperCase(Locale.ROOT)) {
            case "MAJSOUL_TONPUU", "TONPUU", "TONPUSEN", "EAST" -> {
                this.configuredVariant = MahjongVariant.RIICHI;
                this.configuredRule = majsoulRule(MahjongRule.GameLength.EAST);
            }
            case "MAJSOUL_HANCHAN", "HANCHAN", "TWO_WIND", "SOUTH" -> {
                this.configuredVariant = MahjongVariant.RIICHI;
                this.configuredRule = majsoulRule(MahjongRule.GameLength.TWO_WIND);
            }
            case "GB", "GUOBIAO", "ZHONGGUO", "CHINESE_OFFICIAL" -> {
                this.configuredVariant = MahjongVariant.GB;
                this.configuredRule = gbRule();
            }
            default -> {
                return false;
            }
        }
        this.persistRoomMetadataIfNeeded();
        return true;
    }

    public boolean clickHandTile(UUID playerId, int tileIndex, boolean cancelSelection) {
        if (!this.canSelectHandTile(playerId, tileIndex)) {
            return false;
        }
        Integer selectedIndex = this.selectedHandTileIndices.get(playerId);
        if (selectedIndex != null && selectedIndex == tileIndex) {
            if (cancelSelection) {
                this.selectedHandTileIndices.remove(playerId);
                this.refreshSelectedHandTileView(playerId);
                return true;
            }
            return this.discard(playerId, tileIndex);
        }
        this.selectedHandTileIndices.put(playerId, tileIndex);
        this.refreshSelectedHandTileView(playerId);
        return true;
    }

    public int selectedHandTileIndex(UUID playerId) {
        return this.selectedHandTileIndices.getOrDefault(playerId, -1);
    }

    private void pruneSelectedHandTiles() {
        this.selectedHandTileIndices.entrySet().removeIf(entry -> !this.isValidSelectedHandTile(entry.getKey(), entry.getValue()));
    }

    private boolean isValidSelectedHandTile(UUID playerId, int tileIndex) {
        return this.canSelectHandTile(playerId, tileIndex);
    }

    private boolean canSelectHandTile(UUID playerId, int tileIndex) {
        return this.contains(playerId)
            && tileIndex >= 0
            && this.roundController != null
            && this.roundController.canSelectHandTile(playerId, tileIndex);
    }

    private void refreshSelectedHandTileView(UUID playerId) {
        SeatWind wind = this.seatOf(playerId);
        if (wind == null) {
            return;
        }
        RenderSnapshot snapshot = this.captureRenderSnapshot(0L, 0L);
        SeatRenderSnapshot seat = snapshot.seat(wind);
        if (seat == null || seat.playerId() == null) {
            return;
        }
        TableRenderLayout.LayoutPlan plan = TableRenderLayout.precompute(snapshot);
        TableRenderLayout.SeatLayoutPlan seatPlan = plan.seat(wind);
        if (seatPlan == null) {
            return;
        }
        this.regionDisplayCoordinator.refreshPrivateHandRegions(seat, seatPlan);
    }

    private doublemoon.mahjongcraft.paper.model.MahjongTile handTileAt(UUID playerId, int tileIndex) {
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand = this.hand(playerId);
        if (tileIndex < 0 || tileIndex >= hand.size()) {
            return null;
        }
        return hand.get(tileIndex);
    }

    private void rememberPublicDiscard(UUID playerId, doublemoon.mahjongcraft.paper.model.MahjongTile discardedTile) {
        if (discardedTile == null) {
            return;
        }
        this.lastPublicDiscardPlayerId = playerId;
        this.lastPublicDiscardTile = discardedTile;
    }

    private void clearLastPublicDiscard() {
        this.lastPublicDiscardPlayerId = null;
        this.lastPublicDiscardTile = null;
    }

    private void persistRoomMetadataIfNeeded() {
        if (this.persistentRoom && this.plugin.tableManager() != null) {
            this.plugin.tableManager().persistTables();
        }
    }

    private MahjongRule copyRule() {
        return copyRule(this.configuredRule);
    }

    private static MahjongRule copyRule(MahjongRule rule) {
        return new MahjongRule(
            rule.getLength(),
            rule.getThinkingTime(),
            rule.getStartingPoints(),
            rule.getMinPointsToWin(),
            rule.getMinimumHan(),
            rule.getSpectate(),
            rule.getRedFive(),
            rule.getOpenTanyao(),
            rule.getLocalYaku()
        );
    }

    public List<FinalStanding> finalStandings() {
        if (this.roundController == null || !this.roundController.gameFinished()) {
            return List.of();
        }
        if (this.currentVariant() != MahjongVariant.RIICHI || this.engine() == null) {
            List<FinalStanding> standings = new ArrayList<>();
            for (UUID playerId : this.players()) {
                standings.add(new FinalStanding(
                    playerId,
                    this.displayName(playerId),
                    0,
                    this.points(playerId),
                    (this.points(playerId) - this.currentRule().getStartingPoints()) / 1000.0D,
                    this.isBot(playerId)
                ));
            }
            standings.sort((left, right) -> {
                int scoreCompare = Integer.compare(right.points(), left.points());
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return Integer.compare(this.players().indexOf(left.playerId()), this.players().indexOf(right.playerId()));
            });
            List<FinalStanding> ranked = new ArrayList<>(standings.size());
            for (int i = 0; i < standings.size(); i++) {
                FinalStanding standing = standings.get(i);
                ranked.add(new FinalStanding(standing.playerId(), standing.displayName(), i + 1, standing.points(), standing.gameScore(), standing.bot()));
            }
            return List.copyOf(ranked);
        }
        RiichiRoundEngine engine = this.engine();
        List<RiichiPlayerState> ranked = new ArrayList<>(engine.placementOrder());

        List<FinalStanding> standings = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            RiichiPlayerState player = ranked.get(i);
            double gameScore = MahjongSoulScoring.gameScore(player.getPoints(), i + 1);
            UUID playerId = UUID.fromString(player.getUuid());
            standings.add(new FinalStanding(playerId, player.getDisplayName(), i + 1, player.getPoints(), gameScore, this.isBot(playerId)));
        }
        return List.copyOf(standings);
    }

    public ViewerOverlaySnapshot captureViewerOverlaySnapshot(Player viewer) {
        return this.viewerSnapshotFactory.captureViewerOverlaySnapshot(viewer);
    }

    public ViewerHudSnapshot captureViewerHudSnapshot(Locale locale, UUID viewerId) {
        return this.viewerSnapshotFactory.captureViewerHudSnapshot(locale, viewerId);
    }

    public void updateViewerOverlayRegion(ViewerOverlaySnapshot snapshot) {
        this.regionDisplayCoordinator.updateViewerOverlayRegion(snapshot);
    }

    public List<String> viewerOverlayRegionKeys() {
        return this.regionDisplayCoordinator.regionKeys();
    }

    public void removeManagedRegionDisplays(String regionKey) {
        this.regionDisplayCoordinator.removeManagedRegionDisplays(regionKey);
    }

    public List<UUID> seatIds() {
        return this.participants.seatIds();
    }

    public String viewerMembershipSignatureFor(UUID excludedPlayerId) {
        return this.viewerIndex.viewerMembershipSignatureFor(excludedPlayerId);
    }

    public String riichiFingerprintValue() {
        FingerprintBuilder builder = fingerprintBuilder(64);
        for (UUID playerId : this.participants.seatIds()) {
            if (playerId == null) {
                continue;
            }
            builder.field(playerId).field(this.isRiichi(playerId)).entrySeparator();
        }
        return builder.toString();
    }

    private void scheduleNextRoundCountdown() {
        this.roundLifecycle.scheduleNextRoundCountdown();
    }

    private void cancelNextRoundCountdown() {
        this.roundLifecycle.cancelNextRoundCountdown();
    }

    private long nextRoundSecondsRemaining() {
        return this.roundLifecycle.nextRoundSecondsRemaining();
    }

    public long nextRoundSecondsRemainingValue() {
        return this.nextRoundSecondsRemaining();
    }

    public void prepareRenderRequest() {
        this.cancelBotTask();
        this.pruneSelectedHandTiles();
        this.viewerPresentation.markDirty();
    }

    private void completeRoundStart() {
        if (this.roundController == null) {
            this.roundStartInProgress = false;
            return;
        }
        this.roundController.startRound();
        this.roundStartInProgress = false;
        this.render();
        if (this.plugin.tableManager() != null) {
            this.plugin.tableManager().startSeatWatchdog(this, 60L);
        }
    }

    public void completeRenderFlush() {
        this.playerFeedbackCoordinator.sync();
        this.viewerPresentation.flushIfNeeded();
        this.stateSoundCoordinator.syncStateSounds();
        BotActionScheduler.schedule(this);
    }

    public RenderSnapshot captureRenderSnapshot(long version, long cancellationNonce) {
        return this.renderSnapshotFactory.create(this, version, cancellationNonce);
    }

    public RenderPrecomputeResult precomputeRender(RenderSnapshot snapshot) {
        return new RenderPrecomputeResult(
            snapshot,
            this.regionFingerprintService.precomputeRegionFingerprints(this, snapshot),
            TableRenderLayout.precompute(snapshot)
        );
    }

    public boolean isCenteredInChunk(Chunk chunk) {
        if (chunk == null) {
            return false;
        }
        World world = this.center.getWorld();
        return world != null
            && world.equals(chunk.getWorld())
            && Math.floorDiv(this.center.getBlockX(), 16) == chunk.getX()
            && Math.floorDiv(this.center.getBlockZ(), 16) == chunk.getZ();
    }

    public void clearRenderDisplays() {
        this.regionDisplayCoordinator.clearRenderDisplays();
    }

    public boolean hasRegionDisplays() {
        return this.regionDisplayCoordinator.hasRegionDisplays();
    }

    public boolean isCenterChunkLoaded() {
        World world = this.center.getWorld();
        return world != null
            && world.isChunkLoaded(Math.floorDiv(this.center.getBlockX(), 16), Math.floorDiv(this.center.getBlockZ(), 16));
    }

    public boolean hasStaleDisplayRegions() {
        return this.regionDisplayCoordinator.hasStaleDisplayRegions();
    }

    private static FingerprintBuilder fingerprintBuilder(int capacity) {
        return new FingerprintBuilder(capacity);
    }

    public record RenderSnapshot(
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
        EnumMap<SeatWind, SeatRenderSnapshot> seats
    ) {
        public SeatRenderSnapshot seat(SeatWind wind) {
            return this.seats.get(wind);
        }
    }

    public record SeatRenderSnapshot(
        SeatWind wind,
        UUID playerId,
        String displayName,
        String publicSeatStatus,
        int points,
        boolean riichi,
        boolean ready,
        boolean queuedToLeave,
        boolean online,
        String viewerMembershipSignature,
        int selectedHandTileIndex,
        int riichiDiscardIndex,
        int stickLayoutCount,
        List<UUID> viewerIdsExcluding,
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand,
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> discards,
        List<MeldView> melds,
        List<ScoringStick> scoringSticks,
        List<ScoringStick> cornerSticks
    ) {
    }

    public record ViewerOverlaySnapshot(
        UUID viewerId,
        String regionKey,
        boolean spectator,
        Component overlay,
        List<SpectatorSeatOverlaySnapshot> spectatorSeatOverlays,
        String fingerprint
    ) {
    }

    public record SpectatorSeatOverlaySnapshot(
        SeatWind wind,
        Component overlay,
        String signature
    ) {
    }

    public record ViewerHudSnapshot(
        Component title,
        float progress,
        BossBar.Color color,
        String stateSignature
    ) {
    }

    public record RenderPrecomputeResult(
        RenderSnapshot snapshot,
        Map<String, String> regionFingerprints,
        TableRenderLayout.LayoutPlan layout
    ) {
    }

    private static final class FingerprintBuilder {
        private final StringBuilder delegate;
        private boolean needsSeparator;

        private FingerprintBuilder(int capacity) {
            this.delegate = new StringBuilder(capacity);
        }

        private FingerprintBuilder field(Object value) {
            if (this.needsSeparator) {
                this.delegate.append(':');
            }
            this.delegate.append(Objects.toString(value, ""));
            this.needsSeparator = true;
            return this;
        }

        private FingerprintBuilder raw(Object value) {
            this.delegate.append(value);
            return this;
        }

        private FingerprintBuilder entrySeparator() {
            this.delegate.append(';');
            this.needsSeparator = false;
            return this;
        }

        @Override
        public String toString() {
            return this.delegate.toString();
        }
    }

    public Locale publicLocale() {
        return LocalizedMessages.DEFAULT_LOCALE;
    }

    public TableRenderer renderer() {
        return this.renderer;
    }

    public String seatDisplayName(SeatWind wind, Locale locale) {
        return this.publicTextFactory.seatDisplayName(wind, locale);
    }

    public String publicSeatStatus(SeatWind wind) {
        return this.publicTextFactory.publicSeatStatus(wind);
    }

    public String publicCenterText() {
        return this.publicTextFactory.publicCenterText();
    }

    public doublemoon.mahjongcraft.paper.model.MahjongTile lastPublicDiscardTile() {
        return this.lastPublicDiscardTile;
    }

    public UUID lastPublicDiscardPlayerId() {
        return this.lastPublicDiscardPlayerId;
    }

    public UUID lastPublicDiscardPlayerIdValue() {
        return this.lastPublicDiscardPlayerId;
    }

    private String botDisplayName(UUID playerId, Locale locale) {
        String raw = this.participants.botDisplayNameSource(playerId);
        if (raw == null) {
            return this.plugin.messages().plain(locale, "common.unknown");
        }
        int suffix = this.participants.seatIndexOf(playerId) + 1;
        return this.plugin.messages().plain(locale, "table.bot_name", this.plugin.messages().number(locale, "index", Math.max(1, suffix)));
    }

    public String tileLabelForDisplay(Locale locale, String tileName) {
        String key = "tile." + tileName.toLowerCase(Locale.ROOT);
        return this.plugin.messages().contains(locale, key) ? this.plugin.messages().plain(locale, key) : tileName.toLowerCase(Locale.ROOT);
    }

    private void resetReadyStateForNextRound() {
        this.participants.readyBotsOnly();
    }

    private void promptPlayersToReady() {
        for (UUID playerId : this.participants.seatIds()) {
            if (playerId == null || this.isBot(playerId) || this.isQueuedToLeave(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            this.plugin.messages().send(player, "command.ready_prompt");
        }
    }

    private boolean maybeStartRoundIfReady() {
        if (this.isStarted() || this.roundStartInProgress || this.size() != 4) {
            return false;
        }
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null || !this.isReady(playerId)) {
                return false;
            }
        }
        this.startRound();
        this.stateSoundCoordinator.playRoundStartSound();
        return true;
    }

    private void processDeferredLeaves() {
        if (!this.participants.hasQueuedLeaves() || this.isStarted()) {
            return;
        }
        Map<UUID, SeatWind> removed = new LinkedHashMap<>();
        for (UUID playerId : this.participants.queuedLeavePlayers()) {
            SeatWind wind = this.seatOf(playerId);
            if (this.removePlayer(playerId)) {
                removed.put(playerId, wind);
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        this.participants.removeQueuedLeaves(new ArrayList<>(removed.keySet()));
        if (this.plugin.tableManager() != null) {
            this.plugin.tableManager().finalizeDeferredLeaves(this, removed);
        }
    }

    private static MahjongRule majsoulRule(MahjongRule.GameLength length) {
        return new MahjongRule(
            length,
            MahjongRule.ThinkingTime.NORMAL,
            25000,
            30000,
            MahjongRule.MinimumHan.ONE,
            true,
            MahjongRule.RedFive.THREE,
            true,
            false
        );
    }

    private static MahjongRule gbRule() {
        return new MahjongRule(
            MahjongRule.GameLength.TWO_WIND,
            MahjongRule.ThinkingTime.NORMAL,
            25000,
            30000,
            MahjongRule.MinimumHan.ONE,
            true,
            MahjongRule.RedFive.NONE,
            false,
            false
        );
    }

    private static Location normalizedTableCenter(Location source) {
        Location normalized = source.clone();
        normalized.setYaw(0.0F);
        normalized.setPitch(0.0F);
        return normalized;
    }

    private SeatWind firstEmptySeat() {
        return this.participants.firstEmptySeat();
    }

    public static final class FinalStanding {
        private final UUID playerId;
        private final String displayName;
        private final int place;
        private final int points;
        private final double gameScore;
        private final boolean bot;

        public FinalStanding(UUID playerId, String displayName, int place, int points, double gameScore, boolean bot) {
            this.playerId = playerId;
            this.displayName = displayName;
            this.place = place;
            this.points = points;
            this.gameScore = gameScore;
            this.bot = bot;
        }

        public UUID playerId() {
            return this.playerId;
        }

        public String displayName() {
            return this.displayName;
        }

        public int place() {
            return this.place;
        }

        public int points() {
            return this.points;
        }

        public double gameScore() {
            return this.gameScore;
        }

        public boolean bot() {
            return this.bot;
        }
    }

    public enum ReadyResult {
        READY,
        UNREADY,
        STARTED,
        BLOCKED
    }
}



