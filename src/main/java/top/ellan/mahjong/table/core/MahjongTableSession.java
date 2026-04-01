package top.ellan.mahjong.table.core;

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.gb.runtime.GbNativeRulesGateway;
import top.ellan.mahjong.i18n.LocalizedMessages;
import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.scene.MeldView;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.scene.TableRenderer;
import top.ellan.mahjong.riichi.ReactionResponse;
import top.ellan.mahjong.riichi.ReactionResponses;
import top.ellan.mahjong.riichi.RiichiPlayerState;
import top.ellan.mahjong.riichi.RiichiRoundEngine;
import top.ellan.mahjong.riichi.model.MahjongRule;
import top.ellan.mahjong.riichi.model.MahjongSoulScoring;
import top.ellan.mahjong.riichi.model.ScoringStick;
import top.ellan.mahjong.table.core.round.GbTableRoundController;
import top.ellan.mahjong.table.core.round.OpeningDiceRoll;
import top.ellan.mahjong.table.core.round.RiichiTableRoundController;
import top.ellan.mahjong.table.core.round.TableRoundController;
import top.ellan.mahjong.table.presentation.TableDiceAnimationCoordinator;
import top.ellan.mahjong.table.presentation.TablePlayerFeedbackCoordinator;
import top.ellan.mahjong.table.presentation.TablePublicTextFactory;
import top.ellan.mahjong.table.presentation.TableStateSoundCoordinator;
import top.ellan.mahjong.table.presentation.TableViewerPresentationCoordinator;
import top.ellan.mahjong.table.presentation.TableViewerSnapshotFactory;
import top.ellan.mahjong.table.render.TableRegionDisplayCoordinator;
import top.ellan.mahjong.table.render.TableRegionFingerprintService;
import top.ellan.mahjong.table.render.TableRenderCoordinator;
import top.ellan.mahjong.table.render.TableRenderInspectCoordinator;
import top.ellan.mahjong.table.render.TableRenderSnapshotFactory;
import top.ellan.mahjong.table.runtime.BotActionScheduler;
import top.ellan.mahjong.runtime.PluginTask;
import top.ellan.mahjong.table.runtime.TableLifecycleCoordinator;
import top.ellan.mahjong.ui.SettlementUi;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class MahjongTableSession {
    private static final long PUBLIC_ACTION_DISPLAY_TICKS = 40L;
    private final MahjongPaperPlugin plugin;
    private final String id;
    private final Location center;
    private final boolean persistentRoom;
    private final boolean botMatchRoom;
    private final TableParticipantRegistry participants = new TableParticipantRegistry();
    private final TableRenderer renderer = new TableRenderer();
    private final TableRenderSnapshotFactory renderSnapshotFactory = new TableRenderSnapshotFactory();
    private final TableRegionFingerprintService regionFingerprintService = new TableRegionFingerprintService();
    private MahjongRule configuredRule;
    private TableRoundController roundController;
    private boolean roundStartInProgress;
    private top.ellan.mahjong.model.MahjongTile lastPublicDiscardTile;
    private UUID lastPublicDiscardPlayerId;
    private PublicActionAnnouncement lastPublicActionAnnouncement;
    private PluginTask publicActionClearTask;
    private long publicActionAnnouncementSequence;
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
    private final SessionState coordinatorState;
    private final SessionRuleCoordinator ruleCoordinator;
    private final SessionRoundActionCoordinator roundActionCoordinator;
    private final SessionHandSelectionCoordinator handSelectionCoordinator;
    private final SessionRoundFlowCoordinator roundFlowCoordinator;
    private final Map<UUID, String> viewerActionMenuStates = new HashMap<>();
    private final Map<UUID, PluginTask> viewerActionMenuTransitions = new HashMap<>();
    private MahjongVariant configuredVariant;

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, boolean persistentRoom) {
        this(
            plugin,
            id,
            center,
            MahjongVariant.RIICHI,
            SessionRulePresetResolver.majsoulRule(MahjongRule.GameLength.TWO_WIND),
            persistentRoom,
            false
        );
    }

    public MahjongTableSession(
        MahjongPaperPlugin plugin,
        String id,
        Location center,
        MahjongVariant configuredVariant,
        MahjongRule configuredRule,
        boolean persistentRoom,
        boolean botMatchRoom
    ) {
        this.plugin = plugin;
        this.id = id;
        this.center = normalizedTableCenter(center);
        this.configuredVariant = configuredVariant == null ? MahjongVariant.RIICHI : configuredVariant;
        this.configuredRule = copyRule(configuredRule);
        this.persistentRoom = persistentRoom;
        this.botMatchRoom = botMatchRoom;
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
        this.coordinatorState = new SessionStateView();
        this.ruleCoordinator = new SessionRuleCoordinator(this.coordinatorState);
        this.roundActionCoordinator = new SessionRoundActionCoordinator(this.coordinatorState);
        this.handSelectionCoordinator = new SessionHandSelectionCoordinator(this.coordinatorState);
        this.roundFlowCoordinator = new SessionRoundFlowCoordinator(this.coordinatorState);
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

    public boolean isBotMatchRoom() {
        return this.botMatchRoom;
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
        this.cancelViewerActionMenuTransition(playerId);
        this.viewerActionMenuStates.remove(playerId);
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
        this.roundFlowCoordinator.maybeStartRoundIfReady();
        return true;
    }

    public boolean replaceBotWithPlayer(Player player, SeatWind wind) {
        if (player == null || wind == null || this.isStarted() || this.roundStartInProgress) {
            return false;
        }
        UUID botId = this.playerAt(wind);
        if (botId == null || !this.isBot(botId)) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!this.participants.replaceBotWithPlayer(playerId, wind)) {
            return false;
        }
        this.playerFeedbackCoordinator.clearPlayerState(botId);
        this.handSelectionCoordinator.clearPlayer(botId);
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
        this.handSelectionCoordinator.clearPlayer(playerId);
        this.render();
        return true;
    }

    public boolean removePlayer(UUID playerId) {
        if (this.roundStartInProgress || (this.roundController != null && this.roundController.started() && !this.roundController.gameFinished())) {
            return false;
        }
        this.viewerPresentation.hideHud(playerId);
        this.cancelViewerActionMenuTransition(playerId);
        this.viewerActionMenuStates.remove(playerId);
        this.playerFeedbackCoordinator.clearPlayerState(playerId);
        this.handSelectionCoordinator.clearPlayer(playerId);
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

        if (nowReady && this.roundFlowCoordinator.maybeStartRoundIfReady()) {
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
        this.roundFlowCoordinator.startRound();
    }

    public boolean discard(UUID playerId, int tileIndex) {
        return this.roundActionCoordinator.discard(playerId, tileIndex);
    }

    public boolean declareRiichi(UUID playerId, int tileIndex) {
        return this.roundActionCoordinator.declareRiichi(playerId, tileIndex);
    }

    public boolean declareTsumo(UUID playerId) {
        return this.roundActionCoordinator.declareTsumo(playerId);
    }

    public boolean declareKyuushuKyuuhai(UUID playerId) {
        return this.roundActionCoordinator.declareKyuushuKyuuhai(playerId);
    }

    public boolean react(UUID playerId, ReactionResponse response) {
        return this.roundActionCoordinator.react(playerId, response);
    }

    public boolean declareKan(UUID playerId, String tileName) {
        return this.roundActionCoordinator.declareKan(playerId, tileName);
    }

    public void render() {
        this.renderCoordinator.render();
    }

    public void clearDisplays() {
        this.renderCoordinator.clearDisplays();
        this.diceAnimationCoordinator.clear();
    }

    public boolean applyRenderPrecompute(TableRenderPrecomputeResult result) {
        boolean deferred = this.regionDisplayCoordinator.applyRenderPrecompute(result);
        this.viewerPresentation.flushIfNeeded();
        return deferred;
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

    public void cancelNextRoundCountdown() {
        this.roundLifecycle.cancelNextRoundCountdown();
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
        this.handSelectionCoordinator.clearAll();
    }

    public void clearRoundTrackingState() {
        this.roundStartInProgress = false;
        this.diceAnimationCoordinator.clear();
        this.clearLastPublicDiscardInternal();
        this.clearLastPublicActionInternal();
        this.playerFeedbackCoordinator.resetState();
        this.stateSoundCoordinator.reset();
        this.cancelAllViewerActionMenuTransitions();
        this.viewerActionMenuStates.clear();
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

    public void resetBotCounterForLifecycle() {
        this.participants.resetBotCounter();
    }

    public void resetReadyStateForNextRound() {
        this.participants.readyBotsOnly();
    }

    public void promptPlayersToReady() {
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

    public SeatWind currentSeat() {
        if (this.roundController == null || !this.roundController.started()) {
            return SeatWind.EAST;
        }
        return this.roundController.currentSeat();
    }

    public UUID playerAt(SeatWind wind) {
        if (this.roundController != null && this.roundController.started() && !this.roundController.gameFinished()) {
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
        return this.riichiEngine()
            .map(engine -> engine.seatPlayer(playerId.toString()))
            .map(RiichiPlayerState::getDisplayName)
            .orElse(this.plugin.messages().plain(locale, "common.offline"));
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
        return this.intFromRoundController(TableRoundController::dicePoints);
    }

    public int kanCount() {
        return this.intFromRoundController(TableRoundController::kanCount);
    }

    public int roundIndex() {
        return this.intFromRoundController(TableRoundController::roundIndex);
    }

    public SeatWind openDoorSeat() {
        return this.fromRoundController(
            controller -> SeatWind.fromIndex(Math.floorMod(controller.dicePoints() - 1 + controller.roundIndex(), SeatWind.values().length)),
            SeatWind.EAST
        );
    }

    public int honbaCount() {
        return this.intFromRoundController(TableRoundController::honbaCount);
    }

    public SeatWind roundWind() {
        return this.fromRoundController(TableRoundController::roundWind, SeatWind.EAST);
    }

    public SeatWind dealerSeat() {
        return this.fromRoundController(TableRoundController::dealerSeat, SeatWind.EAST);
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
        return this.ruleCoordinator.setRuleOption(key, rawValue);
    }

    public List<String> ruleKeys() {
        return this.ruleCoordinator.ruleKeys();
    }

    public List<String> ruleValues(String key) {
        return this.ruleCoordinator.ruleValues(key);
    }

    public List<top.ellan.mahjong.model.MahjongTile> hand(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::hand, List.of());
    }

    public List<top.ellan.mahjong.model.MahjongTile> discards(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::discards, List.of());
    }

    public int riichiDiscardIndex(UUID playerId) {
        if (playerId == null) {
            return -1;
        }
        return this.riichiEngine()
            .map(engine -> engine.seatPlayer(playerId.toString()))
            .filter(player -> player.getRiichiSengenTile() != null)
            .map(MahjongTableSession::resolveRiichiDiscardIndex)
            .orElse(-1);
    }

    private static int resolveRiichiDiscardIndex(RiichiPlayerState player) {
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

    public List<top.ellan.mahjong.model.MahjongTile> remainingWall() {
        return this.fromRoundController(TableRoundController::remainingWall, List.of());
    }

    public int remainingWallCount() {
        return this.intFromRoundController(TableRoundController::remainingWallCount);
    }

    public List<MeldView> fuuro(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::fuuro, List.of());
    }

    public List<ScoringStick> scoringSticks(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::scoringSticks, List.of());
    }

    public int riichiPoolCount() {
        return this.riichiEngine()
            .map(engine -> engine.getSeats().stream().mapToInt(RiichiPlayerState::getRiichiStickAmount).sum())
            .orElse(0);
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
        return this.cornerSticks(wind).size();
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

    public List<top.ellan.mahjong.model.MahjongTile> doraIndicators() {
        return this.fromRoundController(TableRoundController::doraIndicators, List.of());
    }

    public List<top.ellan.mahjong.model.MahjongTile> uraDoraIndicators() {
        return this.fromRoundController(TableRoundController::uraDoraIndicators, List.of());
    }

    public top.ellan.mahjong.riichi.RoundResolution lastResolution() {
        return this.fromRoundController(TableRoundController::lastResolution, null);
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

    public Optional<RiichiRoundEngine> riichiEngine() {
        if (this.roundController == null) {
            return Optional.empty();
        }
        Optional<RiichiRoundEngine> engine = this.roundController.accept(new TableRoundController.VariantVisitor<>() {
            @Override
            public Optional<RiichiRoundEngine> visitRiichi(RiichiTableRoundController controller) {
                return Optional.of(controller.roundEngine());
            }

            @Override
            public Optional<RiichiRoundEngine> visitGb(GbTableRoundController controller) {
                return Optional.empty();
            }
        });
        return engine == null ? Optional.empty() : engine;
    }

    public boolean hasRoundController() {
        return this.roundController != null;
    }

    public boolean isRoundFinished() {
        return this.roundController != null && this.roundController.gameFinished();
    }

    public String currentTurnDisplayName() {
        return this.fromRoundController(TableRoundController::currentPlayerDisplayName, "");
    }

    public top.ellan.mahjong.riichi.ReactionOptions availableReactions(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::availableReactions, null);
    }

    public boolean hasPendingReaction() {
        return this.roundController != null && this.roundController.hasPendingReaction();
    }

    public String pendingReactionFingerprint() {
        return this.fromRoundController(TableRoundController::pendingReactionFingerprint, "");
    }

    public String pendingReactionTileKey() {
        return this.fromRoundController(TableRoundController::pendingReactionTileKey, "");
    }

    public boolean canDeclareRiichi(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::canDeclareRiichi);
    }

    public boolean canDeclareKan(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::canDeclareKan);
    }

    public boolean canDeclareConcealedKan(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::canDeclareConcealedKan);
    }

    public boolean canDeclareAddedKan(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::canDeclareAddedKan);
    }

    public boolean canDeclareKyuushu(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::canDeclareKyuushu);
    }

    public boolean canDeclareTsumo(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::canDeclareTsumo);
    }

    public List<Integer> suggestedRiichiIndices(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::suggestedRiichiIndices, List.of());
    }

    public List<String> suggestedKanTiles(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::suggestedKanTiles, List.of());
    }

    public List<String> suggestedConcealedKanTiles(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::suggestedConcealedKanTiles, List.of());
    }

    public List<String> suggestedAddedKanTiles(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::suggestedAddedKanTiles, List.of());
    }

    public List<String> suggestedDiscardTiles(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::suggestedDiscardTiles, List.of());
    }

    public List<top.ellan.mahjong.riichi.RiichiDiscardSuggestion> suggestedDiscardSuggestions(UUID playerId) {
        return this.fromRoundController(playerId, TableRoundController::suggestedDiscardSuggestions, List.of());
    }

    public boolean canSelectHandTile(UUID playerId, int tileIndex) {
        return this.canSelectHandTileInternal(playerId, tileIndex);
    }

    public top.ellan.mahjong.gb.jni.GbTingResponse gbTingOptions(UUID playerId) {
        return this.fromGbController(
            playerId,
            GbTableRoundController::tingOptions,
            new top.ellan.mahjong.gb.jni.GbTingResponse(false, List.of(), "GB round is inactive.")
        );
    }

    public boolean gbCanWinByTsumo(UUID playerId) {
        return this.fromGbController(playerId, GbTableRoundController::canWinByTsumo, false);
    }

    public int gbSuggestedDiscardIndex(UUID playerId) {
        return this.fromGbController(playerId, GbTableRoundController::suggestedBotDiscardIndex, -1);
    }

    public ReactionResponse gbSuggestedReaction(UUID playerId) {
        return this.fromGbController(playerId, GbTableRoundController::suggestedBotReaction, ReactionResponses.SKIP);
    }

    public String gbSuggestedKanTile(UUID playerId) {
        return this.fromGbController(playerId, GbTableRoundController::suggestedBotKanTile, null);
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
        this.roundFlowCoordinator.tick();
    }

    private MahjongRule currentRule() {
        return this.roundController == null ? this.configuredRule : this.roundController.rule();
    }

    private int intFromRoundController(ToIntFunction<TableRoundController> extractor) {
        return this.roundController == null ? 0 : extractor.applyAsInt(this.roundController);
    }

    private <T> T fromRoundController(Function<TableRoundController, T> extractor, T fallback) {
        return this.roundController == null ? fallback : extractor.apply(this.roundController);
    }

    private <T> T fromRoundController(UUID playerId, BiFunction<TableRoundController, UUID, T> extractor, T fallback) {
        return playerId == null || this.roundController == null ? fallback : extractor.apply(this.roundController, playerId);
    }

    private boolean fromRoundController(UUID playerId, BiPredicate<TableRoundController, UUID> extractor) {
        return playerId != null && this.roundController != null && extractor.test(this.roundController, playerId);
    }

    private <T> T fromGbController(UUID playerId, BiFunction<GbTableRoundController, UUID, T> extractor, T fallback) {
        if (!(this.roundController instanceof GbTableRoundController gbController) || playerId == null) {
            return fallback;
        }
        return extractor.apply(gbController, playerId);
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
        return this.ruleCoordinator.applyRulePreset(rawValue);
    }

    public boolean clickHandTile(UUID playerId, int tileIndex, boolean cancelSelection) {
        return this.handSelectionCoordinator.clickHandTile(playerId, tileIndex, cancelSelection);
    }

    public int selectedHandTileIndex(UUID playerId) {
        return this.handSelectionCoordinator.selectedHandTileIndex(playerId);
    }

    void refreshSelectedHandTileViewInternal(UUID playerId) {
        SeatWind wind = this.seatOf(playerId);
        if (wind == null) {
            return;
        }
        TableRenderSnapshot snapshot = this.captureRenderSnapshot(0L, 0L);
        TableSeatRenderSnapshot seat = snapshot.seat(wind);
        if (seat == null || seat.playerId() == null) {
            return;
        }
        TableRenderLayout.SeatLayoutPlan seatPlan = TableRenderLayout.precomputeSeatOnly(snapshot, wind);
        if (seatPlan == null) {
            return;
        }
        this.regionDisplayCoordinator.refreshPrivateHandRegions(seat, seatPlan);
    }

    boolean canSelectHandTileInternal(UUID playerId, int tileIndex) {
        return this.contains(playerId)
            && tileIndex >= 0
            && this.roundController != null
            && this.roundController.canSelectHandTile(playerId, tileIndex);
    }

    top.ellan.mahjong.model.MahjongTile handTileAtInternal(UUID playerId, int tileIndex) {
        List<top.ellan.mahjong.model.MahjongTile> hand = this.hand(playerId);
        if (tileIndex < 0 || tileIndex >= hand.size()) {
            return null;
        }
        return hand.get(tileIndex);
    }

    void rememberPublicDiscardInternal(UUID playerId, top.ellan.mahjong.model.MahjongTile discardedTile) {
        if (discardedTile == null) {
            return;
        }
        this.lastPublicDiscardPlayerId = playerId;
        this.lastPublicDiscardTile = discardedTile;
    }

    void clearLastPublicDiscardInternal() {
        this.lastPublicDiscardPlayerId = null;
        this.lastPublicDiscardTile = null;
    }

    void rememberPublicActionInternal(UUID playerId, String actionKey) {
        this.rememberPublicActionInternal(playerId, actionKey, List.of());
    }

    void rememberPublicActionInternal(UUID playerId, String actionKey, List<top.ellan.mahjong.model.MahjongTile> tiles) {
        if (playerId == null || actionKey == null || actionKey.isBlank()) {
            return;
        }
        List<top.ellan.mahjong.model.MahjongTile> safeTiles = tiles == null ? List.of() : List.copyOf(tiles);
        this.lastPublicActionAnnouncement = new PublicActionAnnouncement(playerId, actionKey, safeTiles);
        long sequence = ++this.publicActionAnnouncementSequence;
        this.cancelPublicActionClearTask();
        this.publicActionClearTask = this.plugin.scheduler().runRegionDelayed(this.center(), () -> {
            this.publicActionClearTask = null;
            if (this.publicActionAnnouncementSequence != sequence) {
                return;
            }
            this.lastPublicActionAnnouncement = null;
            this.render();
        }, PUBLIC_ACTION_DISPLAY_TICKS);
    }

    void clearLastPublicActionInternal() {
        this.lastPublicActionAnnouncement = null;
        this.cancelPublicActionClearTask();
    }

    void clearSelectedHandTilesInternal() {
        this.handSelectionCoordinator.clearAll();
    }

    public String viewerActionMenuState(UUID viewerId) {
        if (viewerId == null) {
            return "";
        }
        return Objects.toString(this.viewerActionMenuStates.get(viewerId), "");
    }

    public void setViewerActionMenuState(UUID viewerId, String menuState) {
        if (viewerId == null) {
            return;
        }
        this.cancelViewerActionMenuTransition(viewerId);
        if (menuState == null || menuState.isBlank()) {
            this.clearViewerActionMenuState(viewerId);
            return;
        }
        if (Objects.equals(this.viewerActionMenuStates.get(viewerId), menuState)) {
            return;
        }
        this.viewerActionMenuStates.put(viewerId, menuState);
        this.viewerPresentation.markDirty();
    }

    public void clearViewerActionMenuState(UUID viewerId) {
        if (viewerId == null) {
            return;
        }
        this.cancelViewerActionMenuTransition(viewerId);
        if (this.viewerActionMenuStates.remove(viewerId) != null) {
            this.viewerPresentation.markDirty();
        }
    }

    public void transitionViewerActionMenuState(UUID viewerId, String targetMenuState) {
        if (viewerId == null) {
            return;
        }
        this.cancelViewerActionMenuTransition(viewerId);
        this.viewerActionMenuStates.remove(viewerId);
        this.viewerPresentation.markDirty();
        this.render();
        PluginTask transitionTask = this.plugin.scheduler().runRegionDelayed(this.center(), () -> {
            this.viewerActionMenuTransitions.remove(viewerId);
            if (!this.contains(viewerId)) {
                return;
            }
            if (targetMenuState == null || targetMenuState.isBlank()) {
                this.viewerActionMenuStates.remove(viewerId);
            } else {
                this.viewerActionMenuStates.put(viewerId, targetMenuState);
            }
            this.viewerPresentation.markDirty();
            this.render();
        }, 1L);
        this.viewerActionMenuTransitions.put(viewerId, transitionTask);
    }

    private void cancelViewerActionMenuTransition(UUID viewerId) {
        if (viewerId == null) {
            return;
        }
        PluginTask task = this.viewerActionMenuTransitions.remove(viewerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelAllViewerActionMenuTransitions() {
        for (PluginTask task : this.viewerActionMenuTransitions.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        this.viewerActionMenuTransitions.clear();
    }

    void playReactionSoundInternal(ReactionResponse response) {
        this.stateSoundCoordinator.playReactionSound(response);
    }

    void persistRoomMetadataIfNeededInternal() {
        if (this.persistentRoom && this.plugin.tableManager() != null) {
            this.plugin.tableManager().persistTables();
        }
    }

    MahjongRule configuredRuleInternal() {
        return this.configuredRule;
    }

    void setConfiguredRuleInternal(MahjongRule configuredRule) {
        this.configuredRule = copyRule(configuredRule);
    }

    void setConfiguredVariantInternal(MahjongVariant configuredVariant) {
        this.configuredVariant = configuredVariant == null ? MahjongVariant.RIICHI : configuredVariant;
    }

    TableRoundController roundControllerInternal() {
        return this.roundController;
    }

    boolean seatAssignmentsMatchControllerInternal(TableRoundController controller) {
        if (controller == null) {
            return false;
        }
        for (SeatWind wind : SeatWind.values()) {
            if (!Objects.equals(this.participants.playerAt(wind), controller.playerAt(wind))) {
                return false;
            }
        }
        return true;
    }

    void setRoundControllerInternal(TableRoundController roundController) {
        this.roundController = roundController;
    }

    TableRoundController createRoundControllerInternal() {
        return this.createRoundController();
    }

    void setRoundStartInProgressInternal(boolean roundStartInProgress) {
        this.roundStartInProgress = roundStartInProgress;
    }

    void resetRoundPresentationForStartInternal() {
        this.playerFeedbackCoordinator.resetForRoundStart();
        this.stateSoundCoordinator.resetForRoundStart();
    }

    boolean shouldAnimateOpeningDiceInternal() {
        return this.diceAnimationCoordinator.shouldAnimate();
    }

    void startOpeningDiceAnimationInternal(OpeningDiceRoll diceRoll, Runnable completion) {
        this.diceAnimationCoordinator.start(diceRoll, completion);
    }

    void completeRoundStartInternal() {
        if (this.roundController == null) {
            this.roundStartInProgress = false;
            return;
        }
        this.roundController.startRound();
        this.roundStartInProgress = false;
        this.render();
        if (this.plugin.tableManager() != null && !this.plugin.settings().tableFreeMoveDuringRound()) {
            this.plugin.tableManager().startSeatWatchdog(this, 60L);
        }
    }

    void playRoundStartSoundInternal() {
        this.stateSoundCoordinator.playRoundStartSound();
    }

    void restoreDisplaysIfNeededInternal() {
        this.renderCoordinator.restoreDisplaysIfNeeded();
    }

    void flushViewerPresentationIfNeededInternal() {
        this.viewerPresentation.flushIfNeeded();
    }

    boolean hasQueuedLeavesInternal() {
        return this.participants.hasQueuedLeaves();
    }

    Set<UUID> queuedLeavePlayersInternal() {
        return this.participants.queuedLeavePlayers();
    }

    void removeQueuedLeavesInternal(List<UUID> removedPlayerIds) {
        this.participants.removeQueuedLeaves(removedPlayerIds);
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
            rule.getLocalYaku(),
            rule.getRonMode(),
            rule.getRiichiProfile()
        );
    }

    public List<TableFinalStanding> finalStandings() {
        if (this.roundController == null || !this.roundController.gameFinished()) {
            return List.of();
        }
        Optional<RiichiRoundEngine> riichiEngine = this.riichiEngine();
        if (this.currentVariant() != MahjongVariant.RIICHI || riichiEngine.isEmpty()) {
            List<TableFinalStanding> standings = new ArrayList<>();
            for (UUID playerId : this.players()) {
                standings.add(new TableFinalStanding(
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
            List<TableFinalStanding> ranked = new ArrayList<>(standings.size());
            for (int i = 0; i < standings.size(); i++) {
                TableFinalStanding standing = standings.get(i);
                ranked.add(new TableFinalStanding(standing.playerId(), standing.displayName(), i + 1, standing.points(), standing.gameScore(), standing.bot()));
            }
            return List.copyOf(ranked);
        }
        List<RiichiPlayerState> ranked = new ArrayList<>(riichiEngine.get().placementOrder());

        List<TableFinalStanding> standings = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            RiichiPlayerState player = ranked.get(i);
            double gameScore = MahjongSoulScoring.gameScore(player.getPoints(), i + 1);
            UUID playerId = UUID.fromString(player.getUuid());
            standings.add(new TableFinalStanding(playerId, player.getDisplayName(), i + 1, player.getPoints(), gameScore, this.isBot(playerId)));
        }
        return List.copyOf(standings);
    }

    public TableViewerOverlaySnapshot captureViewerOverlaySnapshot(Player viewer) {
        return this.viewerSnapshotFactory.captureViewerOverlaySnapshot(viewer);
    }

    public TableViewerHudSnapshot captureViewerHudSnapshot(Locale locale, UUID viewerId) {
        return this.viewerSnapshotFactory.captureViewerHudSnapshot(locale, viewerId);
    }

    public void updateViewerOverlayRegion(TableViewerOverlaySnapshot snapshot) {
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
        DelimitedFingerprintBuilder builder = fingerprintBuilder(64);
        for (UUID playerId : this.participants.seatIds()) {
            if (playerId == null) {
                continue;
            }
            builder.field(playerId).field(this.isRiichi(playerId)).entrySeparator();
        }
        return builder.toString();
    }

    void scheduleNextRoundCountdownInternal() {
        this.roundLifecycle.scheduleNextRoundCountdown();
    }

    boolean hasNextRoundCountdownInternal() {
        return this.roundLifecycle.hasNextRoundCountdown();
    }

    void cancelNextRoundCountdownInternal() {
        this.cancelNextRoundCountdown();
    }

    long nextRoundSecondsRemainingInternal() {
        return this.roundLifecycle.nextRoundSecondsRemaining();
    }

    public long nextRoundSecondsRemainingValue() {
        return this.roundLifecycle.nextRoundSecondsRemaining();
    }

    public void prepareRenderRequest() {
        this.cancelBotTask();
        this.handSelectionCoordinator.pruneSelectedHandTiles();
        this.viewerPresentation.markDirty();
    }

    public void completeRenderFlush() {
        this.playerFeedbackCoordinator.sync();
        this.viewerPresentation.flushIfNeeded();
        this.stateSoundCoordinator.syncStateSounds();
        BotActionScheduler.schedule(this);
    }

    public TableRenderSnapshot captureRenderSnapshot(long version, long cancellationNonce) {
        return this.renderSnapshotFactory.create(this, version, cancellationNonce);
    }

    public TableRenderPrecomputeResult precomputeRender(TableRenderSnapshot snapshot) {
        return new TableRenderPrecomputeResult(
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

    private final class SessionStateView implements SessionState {
        @Override
        public MahjongPaperPlugin plugin() {
            return MahjongTableSession.this.plugin();
        }

        @Override
        public MahjongVariant currentVariant() {
            return MahjongTableSession.this.currentVariant();
        }

        @Override
        public boolean isStarted() {
            return MahjongTableSession.this.isStarted();
        }

        @Override
        public int size() {
            return MahjongTableSession.this.size();
        }

        @Override
        public boolean isReady(UUID playerId) {
            return MahjongTableSession.this.isReady(playerId);
        }

        @Override
        public UUID playerAt(SeatWind wind) {
            return MahjongTableSession.this.playerAt(wind);
        }

        @Override
        public SeatWind seatOf(UUID playerId) {
            return MahjongTableSession.this.seatOf(playerId);
        }

        @Override
        public boolean removePlayer(UUID playerId) {
            return MahjongTableSession.this.removePlayer(playerId);
        }

        @Override
        public int botCount() {
            return MahjongTableSession.this.botCount();
        }

        @Override
        public boolean isBotMatchRoom() {
            return MahjongTableSession.this.isBotMatchRoom();
        }

        @Override
        public void render() {
            MahjongTableSession.this.render();
        }

        @Override
        public boolean discard(UUID playerId, int tileIndex) {
            return MahjongTableSession.this.discard(playerId, tileIndex);
        }

        @Override
        public boolean canSelectHandTileInternal(UUID playerId, int tileIndex) {
            return MahjongTableSession.this.canSelectHandTileInternal(playerId, tileIndex);
        }

        @Override
        public void refreshSelectedHandTileViewInternal(UUID playerId) {
            MahjongTableSession.this.refreshSelectedHandTileViewInternal(playerId);
        }

        @Override
        public top.ellan.mahjong.model.MahjongTile handTileAtInternal(UUID playerId, int tileIndex) {
            return MahjongTableSession.this.handTileAtInternal(playerId, tileIndex);
        }

        @Override
        public void clearSelectedHandTilesInternal() {
            MahjongTableSession.this.clearSelectedHandTilesInternal();
        }

        @Override
        public void clearLastPublicDiscardInternal() {
            MahjongTableSession.this.clearLastPublicDiscardInternal();
        }

        @Override
        public void clearLastPublicActionInternal() {
            MahjongTableSession.this.clearLastPublicActionInternal();
        }

        @Override
        public void rememberPublicDiscardInternal(UUID playerId, top.ellan.mahjong.model.MahjongTile discardedTile) {
            MahjongTableSession.this.rememberPublicDiscardInternal(playerId, discardedTile);
        }

        @Override
        public void rememberPublicActionInternal(UUID playerId, String actionKey) {
            MahjongTableSession.this.rememberPublicActionInternal(playerId, actionKey);
        }

        @Override
        public void playReactionSoundInternal(ReactionResponse response) {
            MahjongTableSession.this.playReactionSoundInternal(response);
        }

        @Override
        public MahjongRule configuredRuleInternal() {
            return MahjongTableSession.this.configuredRuleInternal();
        }

        @Override
        public void setConfiguredRuleInternal(MahjongRule configuredRule) {
            MahjongTableSession.this.setConfiguredRuleInternal(configuredRule);
        }

        @Override
        public void setConfiguredVariantInternal(MahjongVariant configuredVariant) {
            MahjongTableSession.this.setConfiguredVariantInternal(configuredVariant);
        }

        @Override
        public void persistRoomMetadataIfNeededInternal() {
            MahjongTableSession.this.persistRoomMetadataIfNeededInternal();
        }

        @Override
        public TableRoundController roundControllerInternal() {
            return MahjongTableSession.this.roundControllerInternal();
        }

        @Override
        public boolean seatAssignmentsMatchControllerInternal(TableRoundController controller) {
            return MahjongTableSession.this.seatAssignmentsMatchControllerInternal(controller);
        }

        @Override
        public void setRoundControllerInternal(TableRoundController roundController) {
            MahjongTableSession.this.setRoundControllerInternal(roundController);
        }

        @Override
        public TableRoundController createRoundControllerInternal() {
            return MahjongTableSession.this.createRoundControllerInternal();
        }

        @Override
        public boolean isRoundStartInProgress() {
            return MahjongTableSession.this.isRoundStartInProgress();
        }

        @Override
        public void setRoundStartInProgressInternal(boolean roundStartInProgress) {
            MahjongTableSession.this.setRoundStartInProgressInternal(roundStartInProgress);
        }

        @Override
        public void resetRoundPresentationForStartInternal() {
            MahjongTableSession.this.resetRoundPresentationForStartInternal();
        }

        @Override
        public boolean shouldAnimateOpeningDiceInternal() {
            return MahjongTableSession.this.shouldAnimateOpeningDiceInternal();
        }

        @Override
        public void startOpeningDiceAnimationInternal(OpeningDiceRoll diceRoll, Runnable completion) {
            MahjongTableSession.this.startOpeningDiceAnimationInternal(diceRoll, completion);
        }

        @Override
        public void completeRoundStartInternal() {
            MahjongTableSession.this.completeRoundStartInternal();
        }

        @Override
        public void playRoundStartSoundInternal() {
            MahjongTableSession.this.playRoundStartSoundInternal();
        }

        @Override
        public void restoreDisplaysIfNeededInternal() {
            MahjongTableSession.this.restoreDisplaysIfNeededInternal();
        }

        @Override
        public void flushViewerPresentationIfNeededInternal() {
            MahjongTableSession.this.flushViewerPresentationIfNeededInternal();
        }

        @Override
        public boolean hasQueuedLeavesInternal() {
            return MahjongTableSession.this.hasQueuedLeavesInternal();
        }

        @Override
        public Set<UUID> queuedLeavePlayersInternal() {
            return MahjongTableSession.this.queuedLeavePlayersInternal();
        }

        @Override
        public void removeQueuedLeavesInternal(List<UUID> removedPlayerIds) {
            MahjongTableSession.this.removeQueuedLeavesInternal(removedPlayerIds);
        }

        @Override
        public void finalizeDeferredLeaves(Map<UUID, SeatWind> removed) {
            if (MahjongTableSession.this.plugin().tableManager() != null) {
                MahjongTableSession.this.plugin().tableManager().finalizeDeferredLeaves(MahjongTableSession.this, removed);
            }
        }

        @Override
        public void scheduleNextRoundCountdownInternal() {
            MahjongTableSession.this.scheduleNextRoundCountdownInternal();
        }

        @Override
        public boolean hasNextRoundCountdownInternal() {
            return MahjongTableSession.this.hasNextRoundCountdownInternal();
        }

        @Override
        public void cancelNextRoundCountdownInternal() {
            MahjongTableSession.this.cancelNextRoundCountdownInternal();
        }

        @Override
        public long nextRoundSecondsRemainingInternal() {
            return MahjongTableSession.this.nextRoundSecondsRemainingInternal();
        }
    }

    private static DelimitedFingerprintBuilder fingerprintBuilder(int capacity) {
        return DelimitedFingerprintBuilder.create(capacity);
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

    public String publicLastActionSummary(Locale locale) {
        PublicActionAnnouncement announcement = this.lastPublicActionAnnouncement;
        if (announcement == null) {
            return "";
        }
        String actor = this.displayName(announcement.playerId(), locale);
        if (actor == null || actor.isBlank()) {
            actor = this.plugin.messages().plain(locale, "common.unknown");
        }
        String action = this.actionLabelWithTiles(locale, announcement);
        if (action.isBlank()) {
            return "";
        }
        return this.plugin.messages().plain(
            locale,
            "table.last_action",
            this.plugin.messages().tag("player", actor),
            this.plugin.messages().tag("action", action)
        );
    }

    public top.ellan.mahjong.model.MahjongTile lastPublicDiscardTile() {
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

    private String actionLabelWithTiles(Locale locale, PublicActionAnnouncement announcement) {
        String action = this.plugin.messages().plain(locale, announcement.actionKey());
        if (announcement.tiles().isEmpty()) {
            return action;
        }
        String tileLabels = announcement.tiles().stream()
            .map(tile -> this.tileLabelForDisplay(locale, tile.name()))
            .collect(java.util.stream.Collectors.joining(" "));
        if (tileLabels.isBlank()) {
            return action;
        }
        return action + " " + tileLabels;
    }

    private void cancelPublicActionClearTask() {
        if (this.publicActionClearTask == null) {
            return;
        }
        this.publicActionClearTask.cancel();
        this.publicActionClearTask = null;
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

    public enum ReadyResult {
        READY,
        UNREADY,
        STARTED,
        BLOCKED
    }

    private record PublicActionAnnouncement(
        UUID playerId,
        String actionKey,
        List<top.ellan.mahjong.model.MahjongTile> tiles
    ) {
    }
}




