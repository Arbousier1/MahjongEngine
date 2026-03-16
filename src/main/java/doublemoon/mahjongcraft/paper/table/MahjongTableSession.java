package doublemoon.mahjongcraft.paper.table;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.i18n.LocalizedMessages;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.DisplayVisibilityRegistry;
import doublemoon.mahjongcraft.paper.render.MeldView;
import doublemoon.mahjongcraft.paper.render.TableDisplayRegistry;
import doublemoon.mahjongcraft.paper.render.TableRenderLayout;
import doublemoon.mahjongcraft.paper.render.TableRenderer;
import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.ReactionType;
import doublemoon.mahjongcraft.paper.riichi.RiichiPlayerState;
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongSoulScoring;
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick;
import doublemoon.mahjongcraft.paper.ui.SettlementUi;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import kotlin.Pair;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class MahjongTableSession {
    private static final String REGION_TABLE = "table";
    private static final String REGION_WALL = "wall";
    private static final String REGION_DORA = "dora";
    private static final String REGION_CENTER = "center";
    private static final long NEXT_ROUND_DELAY_MILLIS = 8000L;
    private static final int MAX_HAND_TILE_REGIONS = 14;

    private final MahjongPaperPlugin plugin;
    private final String id;
    private final Location center;
    private final boolean persistentRoom;
    private final List<UUID> seats = new ArrayList<>(Collections.nCopies(4, null));
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<String, List<Entity>> regionDisplays = new LinkedHashMap<>();
    private final Map<String, String> regionFingerprints = new HashMap<>();
    private final TableRenderer renderer = new TableRenderer();
    private final Map<UUID, String> botNames = new LinkedHashMap<>();
    private final Map<UUID, String> feedbackState = new HashMap<>();
    private final Map<UUID, BossBar> viewerHudBars = new HashMap<>();
    private final Map<UUID, String> viewerHudState = new HashMap<>();
    private final Map<UUID, Integer> selectedHandTileIndices = new HashMap<>();
    private final Set<UUID> readyPlayers = new LinkedHashSet<>();
    private final Set<UUID> leaveAfterRoundPlayers = new LinkedHashSet<>();
    private MahjongRule configuredRule;
    private RiichiRoundEngine engine;
    private doublemoon.mahjongcraft.paper.model.MahjongTile lastPublicDiscardTile;
    private UUID lastPublicDiscardPlayerId;
    private String lastSettlementFingerprint = "";
    private String lastPersistedSettlementFingerprint = "";
    private String lastTurnSoundFingerprint = "";
    private String lastRiichiSoundFingerprint = "";
    private String lastResolutionSoundFingerprint = "";
    private long nextRoundDeadlineMillis;
    private int nextBotNumber = 1;
    private BukkitTask botTask;
    private long renderRequestVersion;
    private long renderCancellationNonce;
    private boolean renderPrecomputeRunning;
    private RenderSnapshot pendingRenderSnapshot;

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, Player owner) {
        this(plugin, id, center, majsoulRule(MahjongRule.GameLength.TWO_WIND), true);
        this.addPlayer(owner);
    }

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, Player owner, boolean persistentRoom) {
        this(plugin, id, center, majsoulRule(MahjongRule.GameLength.TWO_WIND), persistentRoom);
        this.addPlayer(owner);
    }

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, MahjongRule configuredRule, boolean persistentRoom) {
        this.plugin = plugin;
        this.id = id;
        this.center = normalizedTableCenter(center);
        this.configuredRule = copyRule(configuredRule);
        this.persistentRoom = persistentRoom;
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

    public MahjongRule configuredRuleSnapshot() {
        return copyRule(this.configuredRule);
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
        UUID playerId = player.getUniqueId();
        if (wind == null || this.seats.contains(playerId) || this.playerAt(wind) != null) {
            return false;
        }
        if (this.size() >= 4) {
            return false;
        }
        this.spectators.remove(player.getUniqueId());
        this.seats.set(wind.index(), playerId);
        this.leaveAfterRoundPlayers.remove(playerId);
        this.readyPlayers.remove(playerId);
        return true;
    }

    public boolean addSpectator(Player player) {
        UUID playerId = player.getUniqueId();
        if (!this.currentRule().getSpectate() || this.seats.contains(playerId) || this.spectators.contains(playerId)) {
            return false;
        }
        this.spectators.add(playerId);
        return true;
    }

    public boolean removeSpectator(UUID playerId) {
        this.hideHud(playerId);
        return this.spectators.remove(playerId);
    }

    public boolean addBot() {
        if (this.isStarted() || this.size() >= 4) {
            return false;
        }
        UUID botId;
        do {
            botId = UUID.nameUUIDFromBytes((this.id + "-bot-" + this.nextBotNumber).getBytes(StandardCharsets.UTF_8));
            this.nextBotNumber++;
        } while (this.seats.contains(botId));
        SeatWind emptySeat = this.firstEmptySeat();
        if (emptySeat == null) {
            return false;
        }
        this.seats.set(emptySeat.index(), botId);
        this.botNames.put(botId, "Bot-" + this.botNames.size());
        this.readyPlayers.add(botId);
        this.render();
        this.maybeStartRoundIfReady();
        return true;
    }

    public boolean removeBot() {
        if (this.isStarted()) {
            return false;
        }
        for (int i = this.seats.size() - 1; i >= 0; i--) {
            UUID playerId = this.seats.get(i);
            if (playerId != null && this.botNames.containsKey(playerId)) {
                this.seats.set(i, null);
                this.botNames.remove(playerId);
                this.feedbackState.remove(playerId);
                this.selectedHandTileIndices.remove(playerId);
                this.readyPlayers.remove(playerId);
                this.leaveAfterRoundPlayers.remove(playerId);
                this.render();
                return true;
            }
        }
        return false;
    }

    public boolean removePlayer(UUID playerId) {
        if (this.engine != null && this.engine.getStarted()) {
            return false;
        }
        this.hideHud(playerId);
        this.feedbackState.remove(playerId);
        this.botNames.remove(playerId);
        this.selectedHandTileIndices.remove(playerId);
        this.readyPlayers.remove(playerId);
        this.leaveAfterRoundPlayers.remove(playerId);
        int seatIndex = this.seats.indexOf(playerId);
        if (seatIndex < 0) {
            return false;
        }
        this.seats.set(seatIndex, null);
        return true;
    }

    public boolean contains(UUID playerId) {
        return this.seats.contains(playerId);
    }

    public boolean isEmpty() {
        return this.size() == 0;
    }

    public int size() {
        int occupied = 0;
        for (UUID playerId : this.seats) {
            if (playerId != null) {
                occupied++;
            }
        }
        return occupied;
    }

    public int botCount() {
        return this.botNames.size();
    }

    public int spectatorCount() {
        return this.spectators.size();
    }

    public List<UUID> players() {
        return this.seats.stream().filter(Objects::nonNull).toList();
    }

    public Set<UUID> spectators() {
        return Set.copyOf(this.spectators);
    }

    public UUID owner() {
        for (UUID playerId : this.seats) {
            if (playerId != null) {
                return playerId;
            }
        }
        return null;
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
        return playerId != null && (this.isBot(playerId) || this.readyPlayers.contains(playerId));
    }

    public int readyCount() {
        int ready = 0;
        for (UUID playerId : this.seats) {
            if (playerId != null && this.isReady(playerId)) {
                ready++;
            }
        }
        return ready;
    }

    public boolean isQueuedToLeave(UUID playerId) {
        return playerId != null && this.leaveAfterRoundPlayers.contains(playerId);
    }

    public ReadyResult toggleReady(UUID playerId) {
        if (playerId == null || !this.contains(playerId) || this.isBot(playerId) || this.isStarted()) {
            return ReadyResult.BLOCKED;
        }

        boolean nowReady;
        if (this.readyPlayers.contains(playerId)) {
            this.readyPlayers.remove(playerId);
            nowReady = false;
        } else {
            this.readyPlayers.add(playerId);
            nowReady = true;
        }

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
        this.leaveAfterRoundPlayers.add(playerId);
        return true;
    }

    public void startRound() {
        if (this.size() != 4) {
            throw new IllegalStateException("A riichi table needs exactly 4 players");
        }

        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null || !this.isReady(playerId)) {
                throw new IllegalStateException("All seated players must be ready");
            }
        }

        this.cancelNextRoundCountdown();
        if (this.engine == null || this.engine.getGameFinished()) {
            List<RiichiPlayerState> players = new ArrayList<>(SeatWind.values().length);
            for (SeatWind wind : SeatWind.values()) {
                UUID playerId = this.playerAt(wind);
                if (playerId == null) {
                    throw new IllegalStateException("A riichi table needs exactly 4 occupied seats");
                }
                players.add(new RiichiPlayerState(this.displayName(playerId), playerId.toString(), !this.isBot(playerId)));
            }
            this.engine = new RiichiRoundEngine(players, this.copyRule());
        }

        this.engine.startRound();
        this.selectedHandTileIndices.clear();
        this.clearLastPublicDiscard();
        this.lastSettlementFingerprint = "";
        this.lastPersistedSettlementFingerprint = "";
        this.regionFingerprints.clear();
        this.lastResolutionSoundFingerprint = "";
        this.render();
    }

    public boolean discard(UUID playerId, int tileIndex) {
        if (this.engine == null) {
            return false;
        }
        doublemoon.mahjongcraft.paper.model.MahjongTile discardedTile = this.handTileAt(playerId, tileIndex);
        boolean result = this.engine.discard(playerId.toString(), tileIndex);
        if (result) {
            this.selectedHandTileIndices.clear();
            this.rememberPublicDiscard(playerId, discardedTile);
        }
        this.render();
        return result;
    }

    public boolean declareRiichi(UUID playerId, int tileIndex) {
        if (this.engine == null) {
            return false;
        }
        doublemoon.mahjongcraft.paper.model.MahjongTile discardedTile = this.handTileAt(playerId, tileIndex);
        boolean result = this.engine.declareRiichi(playerId.toString(), tileIndex);
        if (result) {
            this.selectedHandTileIndices.clear();
            this.rememberPublicDiscard(playerId, discardedTile);
        }
        this.render();
        return result;
    }

    public boolean declareTsumo(UUID playerId) {
        if (this.engine == null) {
            return false;
        }
        boolean result = this.engine.tryTsumo(playerId.toString());
        if (result) {
            this.selectedHandTileIndices.clear();
        }
        this.render();
        return result;
    }

    public boolean declareKyuushuKyuuhai(UUID playerId) {
        if (this.engine == null) {
            return false;
        }
        boolean result = this.engine.declareKyuushuKyuuhai(playerId.toString());
        if (result) {
            this.selectedHandTileIndices.clear();
        }
        this.render();
        return result;
    }

    public boolean react(UUID playerId, ReactionResponse response) {
        if (this.engine == null) {
            return false;
        }
        boolean result = this.engine.react(playerId.toString(), response);
        if (result) {
            this.selectedHandTileIndices.clear();
            this.playReactionSound(response);
        }
        this.render();
        return result;
    }

    public boolean declareKan(UUID playerId, String tileName) {
        if (this.engine == null) {
            return false;
        }
        try {
            doublemoon.mahjongcraft.paper.riichi.model.MahjongTile tile =
                doublemoon.mahjongcraft.paper.riichi.model.MahjongTile.valueOf(tileName.toUpperCase(Locale.ROOT));
            boolean result = this.engine.tryAnkanOrKakan(playerId.toString(), tile);
            if (result) {
                this.selectedHandTileIndices.clear();
            }
            this.render();
            return result;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public void render() {
        this.cancelBotTask();
        this.pruneSelectedHandTiles();
        this.scheduleAsyncRenderPrecompute();
        this.syncPlayerFeedback();
        this.syncHud();
        this.playStateSounds();
        BotActionScheduler.schedule(this);
    }

    public void clearDisplays() {
        this.invalidatePendingRenderPrecompute();
        this.regionFingerprints.clear();
        this.removeAllDisplays();
    }

    private void scheduleAsyncRenderPrecompute() {
        RenderSnapshot snapshot = this.captureRenderSnapshot(++this.renderRequestVersion);
        this.pendingRenderSnapshot = snapshot;
        if (this.renderPrecomputeRunning) {
            return;
        }
        this.startAsyncRenderPrecompute(snapshot);
    }

    private void startAsyncRenderPrecompute(RenderSnapshot snapshot) {
        this.renderPrecomputeRunning = true;
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            RenderPrecomputeResult result = new RenderPrecomputeResult(
                snapshot,
                this.precomputeRegionFingerprints(snapshot),
                TableRenderLayout.precompute(snapshot)
            );
            Bukkit.getScheduler().runTask(this.plugin, () -> this.finishAsyncRenderPrecompute(result));
        });
    }

    private void finishAsyncRenderPrecompute(RenderPrecomputeResult result) {
        this.renderPrecomputeRunning = false;
        if (result.snapshot().cancellationNonce() != this.renderCancellationNonce) {
            this.startNextPendingRenderIfNeeded(result.snapshot().version());
            return;
        }

        RenderSnapshot latestSnapshot = this.pendingRenderSnapshot;
        if (latestSnapshot != null && latestSnapshot.version() > result.snapshot().version()) {
            this.startNextPendingRenderIfNeeded(result.snapshot().version());
            return;
        }

        this.applyRenderPrecompute(result);
        this.startNextPendingRenderIfNeeded(result.snapshot().version());
    }

    private void startNextPendingRenderIfNeeded(long completedVersion) {
        RenderSnapshot latestSnapshot = this.pendingRenderSnapshot;
        if (latestSnapshot == null || latestSnapshot.version() <= completedVersion) {
            return;
        }
        this.startAsyncRenderPrecompute(latestSnapshot);
    }

    private void applyRenderPrecompute(RenderPrecomputeResult result) {
        RenderSnapshot snapshot = result.snapshot();
        TableRenderLayout.LayoutPlan plan = result.layout();
        Map<String, String> fingerprints = result.regionFingerprints();

        this.updateRegion(REGION_TABLE, fingerprints.get(REGION_TABLE), () -> this.renderer.renderTableStructure(this, plan));
        this.updateRegion(REGION_WALL, fingerprints.get(REGION_WALL), () -> this.renderer.renderWall(this, plan));
        this.updateRegion(REGION_DORA, fingerprints.get(REGION_DORA), () -> this.renderer.renderDora(this, plan));
        this.updateRegion(REGION_CENTER, fingerprints.get(REGION_CENTER), () -> this.renderer.renderCenterLabel(this, snapshot, plan));
        for (SeatWind wind : SeatWind.values()) {
            SeatRenderSnapshot seat = snapshot.seat(wind);
            TableRenderLayout.SeatLayoutPlan seatPlan = plan.seat(wind);
            this.updateRegion(this.seatRegionKey("labels", wind), fingerprints.get(this.seatRegionKey("labels", wind)), () -> this.renderer.renderSeatLabels(this, seat, seatPlan));
            this.updateRegion(this.seatRegionKey("sticks", wind), fingerprints.get(this.seatRegionKey("sticks", wind)), () -> this.renderer.renderSticks(this, seat, seatPlan));
            this.updateRegion(this.seatRegionKey("hand-public", wind), fingerprints.get(this.seatRegionKey("hand-public", wind)), () -> this.renderer.renderHandPublic(this, snapshot, seat, seatPlan));
            this.clearLegacyHandPrivateTileRegions(wind);
            this.updateRegion(this.seatRegionKey("hand-private", wind), fingerprints.get(this.seatRegionKey("hand-private", wind)), () -> this.renderer.renderHandPrivate(this, seat, seatPlan));
            this.updateRegion(this.seatRegionKey("discards", wind), fingerprints.get(this.seatRegionKey("discards", wind)), () -> this.renderer.renderDiscards(this, seat, seatPlan));
            this.updateRegion(this.seatRegionKey("melds", wind), fingerprints.get(this.seatRegionKey("melds", wind)), () -> this.renderer.renderMelds(this, seat, seatPlan));
        }
        this.updateViewerOverlayRegions();
    }

    private void invalidatePendingRenderPrecompute() {
        this.renderCancellationNonce++;
        this.pendingRenderSnapshot = null;
        this.renderPrecomputeRunning = false;
    }

    public void inspectRender(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        Locale locale = this.plugin.messages().resolveLocale(viewer);
        TableRenderer.TableDiagnostics tableDiagnostics = this.renderer.inspectTable(this);
        this.highlightTableDiagnostics(viewer, tableDiagnostics);
        for (TableRenderer.StickDiagnostics stickDiagnostics : this.renderer.inspectSticks(this)) {
            this.highlightStickDiagnostics(viewer, stickDiagnostics);
            viewer.sendMessage(this.plugin.messages().render(
                locale,
                "command.inspect_stick",
                this.plugin.messages().tag("kind", this.plugin.messages().plain(locale, stickDiagnostics.riichi() ? "command.inspect.kind.riichi" : "command.inspect.kind.stick")),
                this.plugin.messages().tag("wind", this.seatDisplayName(stickDiagnostics.wind(), locale)),
                this.plugin.messages().tag("index", stickDiagnostics.index() >= 0 ? "#" + stickDiagnostics.index() : ""),
                this.plugin.messages().tag("furniture_id", stickDiagnostics.furnitureId()),
                this.plugin.messages().tag("location", this.formatLocation(stickDiagnostics.center())),
                this.plugin.messages().tag("axis", this.plugin.messages().plain(locale, stickDiagnostics.longOnX() ? "command.inspect.axis.x" : "command.inspect.axis.z"))
            ));
        }
        viewer.sendMessage(this.plugin.messages().render(
            locale,
            "command.inspect_summary",
            this.plugin.messages().tag("table_id", this.id()),
            this.plugin.messages().tag("center", this.formatLocation(tableDiagnostics.tableCenter())),
            this.plugin.messages().tag("anchor", this.formatLocation(tableDiagnostics.visualAnchor())),
            this.plugin.messages().tag("span_x", formatDecimal(tableDiagnostics.borderSpanX())),
            this.plugin.messages().tag("span_z", formatDecimal(tableDiagnostics.borderSpanZ()))
        ));
        this.plugin.debug().log(
            "render",
            "Inspect table=" + this.id
                + " center=" + this.formatLocation(tableDiagnostics.tableCenter())
                + " anchor=" + this.formatLocation(tableDiagnostics.visualAnchor())
                + " span=" + formatDecimal(tableDiagnostics.borderSpanX()) + "x" + formatDecimal(tableDiagnostics.borderSpanZ())
        );
    }

    private void removeAllDisplays() {
        for (String regionKey : List.copyOf(this.regionDisplays.keySet())) {
            this.removeRegionDisplays(regionKey);
        }
        this.regionDisplays.clear();
    }

    private void removeRegionDisplays(String regionKey) {
        List<Entity> entities = this.regionDisplays.remove(regionKey);
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            TableDisplayRegistry.unregister(entity.getEntityId());
            DisplayVisibilityRegistry.unregister(entity.getEntityId());
            if (this.plugin.craftEngine() != null) {
                this.plugin.craftEngine().unregisterCullableEntity(entity);
            }
            boolean removedByCraftEngine = this.plugin.craftEngine() != null && this.plugin.craftEngine().removeFurniture(entity);
            if (!removedByCraftEngine && !entity.isDead()) {
                entity.remove();
            }
        }
        this.regionFingerprints.remove(regionKey);
    }

    private void highlightTableDiagnostics(Player viewer, TableRenderer.TableDiagnostics diagnostics) {
        Location centerMarker = diagnostics.tableCenter().clone().add(0.0D, 1.02D, 0.0D);
        Location anchorMarker = diagnostics.visualAnchor().clone().add(0.0D, 1.02D, 0.0D);
        this.spawnMarker(viewer, centerMarker, Color.fromRGB(0, 255, 80), 18);
        this.spawnMarker(viewer, anchorMarker, Color.fromRGB(255, 70, 70), 18);

        double minX = diagnostics.visualAnchor().getX() - diagnostics.borderSpanX() / 2.0D;
        double maxX = diagnostics.visualAnchor().getX() + diagnostics.borderSpanX() / 2.0D;
        double minZ = diagnostics.visualAnchor().getZ() - diagnostics.borderSpanZ() / 2.0D;
        double maxZ = diagnostics.visualAnchor().getZ() + diagnostics.borderSpanZ() / 2.0D;
        World world = diagnostics.visualAnchor().getWorld();
        if (world == null) {
            return;
        }
        this.spawnMarker(viewer, new Location(world, minX, centerMarker.getY(), minZ), Color.fromRGB(255, 180, 60), 10);
        this.spawnMarker(viewer, new Location(world, maxX, centerMarker.getY(), minZ), Color.fromRGB(255, 180, 60), 10);
        this.spawnMarker(viewer, new Location(world, minX, centerMarker.getY(), maxZ), Color.fromRGB(255, 180, 60), 10);
        this.spawnMarker(viewer, new Location(world, maxX, centerMarker.getY(), maxZ), Color.fromRGB(255, 180, 60), 10);
    }

    private void highlightStickDiagnostics(Player viewer, TableRenderer.StickDiagnostics diagnostics) {
        Color color = diagnostics.riichi() ? Color.fromRGB(255, 255, 255) : switch (diagnostics.stick()) {
            case P100 -> Color.fromRGB(255, 70, 70);
            case P5000 -> Color.fromRGB(255, 210, 60);
            case P10000 -> Color.fromRGB(60, 220, 120);
            default -> Color.fromRGB(240, 240, 240);
        };
        this.spawnMarker(viewer, diagnostics.center().clone().add(0.0D, 0.04D, 0.0D), color, 12);
        for (double offset = -1.0D; offset <= 1.0D; offset += 0.25D) {
            Location point = diagnostics.center().clone().add(
                diagnostics.longOnX() ? 0.20D * offset : 0.0D,
                0.04D,
                diagnostics.longOnX() ? 0.0D : 0.20D * offset
            );
            this.spawnMarker(viewer, point, color, 4);
        }
        this.plugin.debug().log(
            "render",
            "Inspect table=" + this.id
                + " stick=" + diagnostics.furnitureId()
                + " wind=" + diagnostics.wind().name()
                + " riichi=" + diagnostics.riichi()
                + " center=" + this.formatLocation(diagnostics.center())
                + " axis=" + (diagnostics.longOnX() ? "X" : "Z")
        );
    }

    private void spawnMarker(Player viewer, Location location, Color color, int count) {
        viewer.spawnParticle(
            Particle.DUST,
            location,
            count,
            0.015D,
            0.015D,
            0.015D,
            0.0D,
            new Particle.DustOptions(color, 1.1F)
        );
    }

    private String formatLocation(Location location) {
        return formatDecimal(location.getX()) + "," + formatDecimal(location.getY()) + "," + formatDecimal(location.getZ());
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public void shutdown() {
        this.cancelBotTask();
        this.cancelNextRoundCountdown();
        this.invalidatePendingRenderPrecompute();
        this.removeAllDisplays();
        this.feedbackState.clear();
        this.selectedHandTileIndices.clear();
        this.clearLastPublicDiscard();
        this.lastSettlementFingerprint = "";
        this.lastPersistedSettlementFingerprint = "";
        this.regionFingerprints.clear();
        this.lastTurnSoundFingerprint = "";
        this.lastRiichiSoundFingerprint = "";
        this.lastResolutionSoundFingerprint = "";
        this.readyPlayers.clear();
        this.leaveAfterRoundPlayers.clear();
        this.spectators.clear();
        this.clearHud();
        this.engine = null;
    }

    public void forceEndMatch() {
        this.cancelBotTask();
        this.cancelNextRoundCountdown();
        this.invalidatePendingRenderPrecompute();
        this.feedbackState.clear();
        this.selectedHandTileIndices.clear();
        this.clearLastPublicDiscard();
        this.lastSettlementFingerprint = "";
        this.lastPersistedSettlementFingerprint = "";
        this.regionFingerprints.clear();
        this.lastTurnSoundFingerprint = "";
        this.lastRiichiSoundFingerprint = "";
        this.lastResolutionSoundFingerprint = "";
        this.leaveAfterRoundPlayers.clear();
        this.engine = null;
        this.resetReadyStateForNextRound();
        this.render();
    }

    public SeatWind currentSeat() {
        if (this.engine == null || !this.engine.getStarted()) {
            return SeatWind.EAST;
        }
        return SeatWind.fromIndex(this.engine.getCurrentPlayerIndex());
    }

    public UUID playerAt(SeatWind wind) {
        if (this.engine != null && !this.engine.getSeats().isEmpty()) {
            return UUID.fromString(this.engine.getSeats().get(wind.index()).getUuid());
        }
        return this.seats.get(wind.index());
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
        if (this.botNames.containsKey(playerId)) {
            return this.botDisplayName(playerId, locale);
        }
        if (this.engine != null) {
            RiichiPlayerState seatPlayer = this.engine.seatPlayer(playerId.toString());
            if (seatPlayer != null) {
                return seatPlayer.getDisplayName();
            }
        }
        return this.plugin.messages().plain(locale, "common.offline");
    }

    public boolean isBot(UUID playerId) {
        return this.botNames.containsKey(playerId);
    }

    public int points(UUID playerId) {
        RiichiPlayerState player = this.engine == null ? null : this.engine.seatPlayer(playerId.toString());
        if (player != null) {
            return player.getPoints();
        }
        return this.contains(playerId) ? this.configuredRule.getStartingPoints() : 0;
    }

    public boolean isRiichi(UUID playerId) {
        RiichiPlayerState player = this.engine == null ? null : this.engine.seatPlayer(playerId.toString());
        return player != null && (player.getRiichi() || player.getDoubleRiichi());
    }

    public int dicePoints() {
        return this.engine == null ? 0 : this.engine.getDicePoints();
    }

    public int kanCount() {
        return this.engine == null ? 0 : this.engine.getKanCount();
    }

    public int roundIndex() {
        return this.engine == null ? 0 : this.engine.getRound().getRound();
    }

    public SeatWind openDoorSeat() {
        if (this.engine == null) {
            return SeatWind.EAST;
        }
        return SeatWind.fromIndex(Math.floorMod(this.dicePoints() - 1 + this.roundIndex(), SeatWind.values().length));
    }

    public int honbaCount() {
        return this.engine == null ? 0 : this.engine.getRound().getHonba();
    }

    public SeatWind dealerSeat() {
        return this.engine == null ? SeatWind.EAST : SeatWind.fromIndex(this.engine.getRound().getRound());
    }

    public String waitingSummary() {
        return this.waitingSummary(this.publicLocale());
    }

    public String waitingSummary(Locale locale) {
        return this.plugin.messages().plain(
            locale,
            "table.waiting_summary",
            this.plugin.messages().number(locale, "seats", this.size()),
            this.plugin.messages().number(locale, "bots", this.botCount()),
            this.plugin.messages().number(locale, "spectators", this.spectatorCount()),
            this.plugin.messages().tag("rules", this.ruleSummary(locale))
        ) + " | " + this.readySummary(locale);
    }

    public String waitingDisplaySummary() {
        return this.waitingDisplaySummary(this.publicLocale());
    }

    public String waitingDisplaySummary(Locale locale) {
        return this.plugin.messages().plain(
            locale,
            "table.waiting_display_summary",
            this.plugin.messages().number(locale, "seats", this.size()),
            this.plugin.messages().number(locale, "bots", this.botCount()),
            this.plugin.messages().number(locale, "spectators", this.spectatorCount())
        ) + " | " + this.readySummary(locale);
    }

    public String ruleDisplaySummary() {
        return this.ruleDisplaySummary(this.publicLocale());
    }

    public String ruleDisplaySummary(Locale locale) {
        MahjongRule rule = this.currentRule();
        return this.plugin.messages().plain(
            locale,
            "table.rule_display_summary",
            this.plugin.messages().tag("length", this.ruleLengthLabel(locale, rule.getLength())),
            this.plugin.messages().tag("thinking", this.ruleThinkingLabel(locale, rule.getThinkingTime())),
            this.plugin.messages().tag("red", this.ruleRedFiveLabel(locale, rule.getRedFive())),
            this.plugin.messages().tag("min_han", this.ruleMinimumHanLabel(locale, rule.getMinimumHan())),
            this.plugin.messages().number(locale, "start", rule.getStartingPoints()),
            this.plugin.messages().number(locale, "goal", rule.getMinPointsToWin())
        );
    }

    public String ruleSummary() {
        return this.ruleSummary(this.publicLocale());
    }

    public String ruleSummary(Locale locale) {
        MahjongRule rule = this.currentRule();
        return this.plugin.messages().plain(
            locale,
            "table.rule_summary",
            this.plugin.messages().tag("length", this.ruleLengthLabel(locale, rule.getLength())),
            this.plugin.messages().tag("thinking", this.ruleThinkingLabel(locale, rule.getThinkingTime())),
            this.plugin.messages().tag("min_han", this.ruleMinimumHanLabel(locale, rule.getMinimumHan())),
            this.plugin.messages().tag("spectate", this.booleanLabel(locale, rule.getSpectate())),
            this.plugin.messages().tag("red_five", this.ruleRedFiveLabel(locale, rule.getRedFive())),
            this.plugin.messages().tag("open_tanyao", this.booleanLabel(locale, rule.getOpenTanyao())),
            this.plugin.messages().tag("local_yaku", this.booleanLabel(locale, rule.getLocalYaku())),
            this.plugin.messages().number(locale, "start_points", rule.getStartingPoints()),
            this.plugin.messages().number(locale, "goal", rule.getMinPointsToWin())
        );
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
        return List.of("preset", "mode", "length", "thinkingTime", "minimumHan", "spectate", "redFive", "openTanyao", "localYaku", "startingPoints", "minPointsToWin");
    }

    public List<String> ruleValues(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "preset", "mode" -> List.of("MAJSOUL_TONPUU", "MAJSOUL_HANCHAN");
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
        RiichiPlayerState player = this.engine == null ? null : this.engine.seatPlayer(playerId.toString());
        if (player == null) {
            return List.of();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(player.getHands().size());
        player.getHands().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile.getMahjongTile().name())));
        return tiles;
    }

    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> discards(UUID playerId) {
        RiichiPlayerState player = this.engine == null ? null : this.engine.seatPlayer(playerId.toString());
        if (player == null) {
            return List.of();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(player.getDiscardedTilesForDisplay().size());
        player.getDiscardedTilesForDisplay().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile.getMahjongTile().name())));
        return tiles;
    }

    public int riichiDiscardIndex(UUID playerId) {
        RiichiPlayerState player = this.engine == null ? null : this.engine.seatPlayer(playerId.toString());
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
        if (this.engine == null) {
            return List.of();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(this.engine.getWall().size());
        this.engine.getWall().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.UNKNOWN));
        return tiles;
    }

    public List<MeldView> fuuro(UUID playerId) {
        RiichiPlayerState player = this.engine == null ? null : this.engine.seatPlayer(playerId.toString());
        if (player == null) {
            return List.of();
        }
        List<MeldView> melds = new ArrayList<>(player.getFuuroList().size());
        player.getFuuroList().forEach(fuuro -> {
            List<doublemoon.mahjongcraft.paper.riichi.model.TileInstance> orderedInstances = new ArrayList<>(fuuro.getTileInstances());
            doublemoon.mahjongcraft.paper.riichi.model.TileInstance addedKanTile = null;
            if ("KAKAN".equals(fuuro.getType().name()) && !orderedInstances.isEmpty()) {
                addedKanTile = orderedInstances.remove(orderedInstances.size() - 1);
            } else if (!"KAKAN".equals(fuuro.getType().name())) {
                orderedInstances.sort((left, right) -> Integer.compare(right.getMahjongTile().getSortOrder(), left.getMahjongTile().getSortOrder()));
            }

            doublemoon.mahjongcraft.paper.riichi.model.TileInstance claimTile = fuuro.getClaimTile();
            orderedInstances.remove(claimTile);
            int claimTileIndex = switch (fuuro.getClaimTarget().name()) {
                case "RIGHT" -> 0;
                case "ACROSS" -> 1;
                case "LEFT" -> orderedInstances.size();
                default -> -1;
            };
            if (claimTileIndex >= 0) {
                orderedInstances.add(Math.min(claimTileIndex, orderedInstances.size()), claimTile);
            }

            List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(orderedInstances.size());
            List<Boolean> faceDownFlags = new ArrayList<>(orderedInstances.size());
            boolean concealedKan = fuuro.isKan() && !fuuro.isOpen();
            for (int i = 0; i < orderedInstances.size(); i++) {
                tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(orderedInstances.get(i).getMahjongTile().name()));
                faceDownFlags.add(concealedKan && (i == 0 || i == orderedInstances.size() - 1));
            }
            int claimYawOffset = switch (fuuro.getClaimTarget().name()) {
                case "RIGHT", "ACROSS" -> 90;
                case "LEFT" -> -90;
                default -> 0;
            };
            doublemoon.mahjongcraft.paper.model.MahjongTile addedKanView = addedKanTile == null
                ? null
                : doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(addedKanTile.getMahjongTile().name());
            melds.add(new MeldView(List.copyOf(tiles), List.copyOf(faceDownFlags), claimTileIndex, claimYawOffset, addedKanView));
        });
        return List.copyOf(melds);
    }

    public List<ScoringStick> scoringSticks(UUID playerId) {
        RiichiPlayerState player = this.engine == null ? null : this.engine.seatPlayer(playerId.toString());
        return player == null ? List.of() : List.copyOf(player.getSticks());
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
        return this.spectators.contains(playerId);
    }

    public List<Player> viewers() {
        List<Player> viewers = new ArrayList<>(this.seats.size() + this.spectators.size());
        this.appendOnlineViewers(viewers);
        return viewers;
    }

    public List<UUID> viewerIdsExcluding(UUID excludedPlayerId) {
        List<UUID> viewerIds = new ArrayList<>(this.seats.size() + this.spectators.size());
        this.appendOnlineViewerIds(viewerIds, excludedPlayerId);
        return List.copyOf(viewerIds);
    }

    public String roundDisplay() {
        return this.roundDisplay(this.publicLocale());
    }

    public String roundDisplay(Locale locale) {
        if (this.engine == null) {
            return this.plugin.messages().plain(locale, "table.round_not_started");
        }
        return this.plugin.messages().plain(
            locale,
            "table.round_display",
            this.plugin.messages().tag("wind", this.roundWindText(locale)),
            this.plugin.messages().number(locale, "round", this.engine.getRound().getRound() + 1),
            this.plugin.messages().number(locale, "honba", this.engine.getRound().getHonba())
        );
    }

    public String dealerName() {
        return this.dealerName(this.publicLocale());
    }

    public String dealerName(Locale locale) {
        if (this.engine == null) {
            return this.plugin.messages().plain(locale, "common.unknown");
        }
        return this.displayName(UUID.fromString(this.engine.getDealer().getUuid()), locale);
    }

    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> doraIndicators() {
        if (this.engine == null) {
            return List.of();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(this.engine.getDoraIndicators().size());
        this.engine.getDoraIndicators().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile.getMahjongTile().name())));
        return tiles;
    }

    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> uraDoraIndicators() {
        if (this.engine == null) {
            return List.of();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(this.engine.getUraDoraIndicators().size());
        this.engine.getUraDoraIndicators().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile.getMahjongTile().name())));
        return tiles;
    }

    public doublemoon.mahjongcraft.paper.riichi.RoundResolution lastResolution() {
        return this.engine == null ? null : this.engine.getLastResolution();
    }

    public boolean openSettlementUi(Player player) {
        if (this.lastResolution() == null) {
            return false;
        }
        SettlementUi.open(player, this);
        return true;
    }

    public Component stateSummary(Player player) {
        if (this.engine == null) {
            return this.plugin.messages().render(player, "command.table_not_started");
        }

        Locale locale = this.plugin.messages().resolveLocale(player);
        ViewerSummarySnapshot summary = this.captureViewerSummarySnapshot(locale, player.getUniqueId());
        return this.plugin.messages().render(player, "command.rule_summary", this.plugin.messages().tag("summary", summary.commandStateSummary()));
    }

    public Component viewerOverlay(Player viewer) {
        Locale locale = this.plugin.messages().resolveLocale(viewer);
        return this.viewerOverlay(locale, this.captureViewerSummarySnapshot(locale, viewer.getUniqueId()));
    }

    private Component viewerOverlay(Locale locale, ViewerSummarySnapshot summary) {
        if (this.engine == null) {
            return this.waitingViewerOverlay(locale, summary);
        }
        if (!this.engine.getStarted()) {
            if (this.engine.getGameFinished() && this.engine.getLastResolution() != null) {
                return this.finishedViewerOverlay(locale, summary);
            }
            return this.nextRoundViewerOverlay(locale, summary);
        }
        return this.activeViewerOverlay(locale, summary);
    }

    public Component spectatorSeatOverlay(Player viewer, SeatWind wind) {
        return this.spectatorSeatOverlay(this.plugin.messages().resolveLocale(viewer), wind);
    }

    private Component spectatorSeatOverlay(Locale locale, SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        if (playerId == null) {
            return this.plugin.messages().render(
                locale,
                "overlay.seat_empty",
                this.plugin.messages().tag("seat", this.seatDisplayName(wind, locale))
            );
        }

        return this.plugin.messages().render(
            locale,
            "overlay.seat",
            this.plugin.messages().tag("seat", this.seatDisplayName(wind, locale)),
            this.plugin.messages().tag("name", this.displayName(playerId, locale)),
            this.plugin.messages().number(locale, "points", this.points(playerId)),
            this.plugin.messages().number(locale, "hand", this.hand(playerId).size()),
            this.plugin.messages().number(locale, "river", this.discards(playerId).size()),
            this.plugin.messages().number(locale, "melds", this.fuuro(playerId).size()),
            this.plugin.messages().tag("status", this.spectatorSeatStatus(locale, wind, playerId))
        );
    }

    public boolean isStarted() {
        return this.engine != null && this.engine.getStarted();
    }

    public RiichiRoundEngine engine() {
        return this.engine;
    }

    void setBotTask(BukkitTask botTask) {
        this.botTask = botTask;
    }

    void cancelBotTask() {
        if (this.botTask != null) {
            this.botTask.cancel();
            this.botTask = null;
        }
    }

    public void tick() {
        this.restoreDisplaysIfNeeded();
        this.updateViewerOverlayRegions();
        this.syncHud();
        if (this.engine == null || this.engine.getStarted() || this.engine.getLastResolution() == null) {
            return;
        }
        this.processDeferredLeaves();
    }

    private MahjongRule currentRule() {
        return this.engine == null ? this.configuredRule : this.engine.getRule();
    }

    public boolean applyRulePreset(String rawValue) {
        switch (rawValue.toUpperCase(Locale.ROOT)) {
            case "MAJSOUL_TONPUU", "TONPUU", "TONPUSEN", "EAST" -> this.configuredRule = majsoulRule(MahjongRule.GameLength.EAST);
            case "MAJSOUL_HANCHAN", "HANCHAN", "TWO_WIND", "SOUTH" -> this.configuredRule = majsoulRule(MahjongRule.GameLength.TWO_WIND);
            default -> {
                return false;
            }
        }
        this.persistRoomMetadataIfNeeded();
        return true;
    }

    public boolean clickHandTile(UUID playerId, int tileIndex) {
        if (!this.contains(playerId) || tileIndex < 0 || tileIndex >= this.hand(playerId).size()) {
            return false;
        }
        Integer selectedIndex = this.selectedHandTileIndices.get(playerId);
        if (selectedIndex != null && selectedIndex == tileIndex) {
            return this.discard(playerId, tileIndex);
        }
        this.selectedHandTileIndices.put(playerId, tileIndex);
        this.render();
        return true;
    }

    public int selectedHandTileIndex(UUID playerId) {
        return this.selectedHandTileIndices.getOrDefault(playerId, -1);
    }

    private void pruneSelectedHandTiles() {
        this.selectedHandTileIndices.entrySet().removeIf(entry -> !this.isValidSelectedHandTile(entry.getKey(), entry.getValue()));
    }

    private boolean isValidSelectedHandTile(UUID playerId, int tileIndex) {
        return this.contains(playerId) && tileIndex >= 0 && tileIndex < this.hand(playerId).size();
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
        if (this.engine == null || !this.engine.getGameFinished()) {
            return List.of();
        }
        List<RiichiPlayerState> seatOrder = this.engine.getSeats();
        List<RiichiPlayerState> ranked = new ArrayList<>(seatOrder);
        ranked.sort((left, right) -> {
            int scoreCompare = Integer.compare(right.getPoints(), left.getPoints());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Integer.compare(seatOrder.indexOf(left), seatOrder.indexOf(right));
        });

        List<FinalStanding> standings = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            RiichiPlayerState player = ranked.get(i);
            double gameScore = MahjongSoulScoring.gameScore(player.getPoints(), i + 1);
            standings.add(new FinalStanding(UUID.fromString(player.getUuid()), player.getDisplayName(), i + 1, player.getPoints(), gameScore));
        }
        return List.copyOf(standings);
    }

    private void syncPlayerFeedback() {
        if (this.engine == null) {
            this.resetFeedbackState();
            return;
        }

        String settlementFingerprint = Objects.toString(this.engine.getLastResolution(), "");
        this.persistSettlementIfNeeded(settlementFingerprint);
        this.syncSettlementFeedback(settlementFingerprint);
        this.lastSettlementFingerprint = settlementFingerprint;
        this.syncSeatFeedbackStates();
    }

    private void sendFeedback(Player player, PlayerFeedbackSnapshot snapshot) {
        if (snapshot.actionBar() != null) {
            player.sendActionBar(snapshot.actionBar());
        }
        if (snapshot.reactionPrompt() != null) {
            player.sendMessage(snapshot.reactionPrompt());
        }
    }

    private void resetFeedbackState() {
        this.feedbackState.clear();
        this.lastSettlementFingerprint = "";
        this.lastPersistedSettlementFingerprint = "";
        this.cancelNextRoundCountdown();
    }

    private void persistSettlementIfNeeded(String settlementFingerprint) {
        if (settlementFingerprint.isBlank() || settlementFingerprint.equals(this.lastPersistedSettlementFingerprint) || this.plugin.database() == null) {
            return;
        }
        this.plugin.database().persistRoundResultAsync(this, this.engine.getLastResolution());
        this.lastPersistedSettlementFingerprint = settlementFingerprint;
    }

    private void syncSettlementFeedback(String settlementFingerprint) {
        if (settlementFingerprint.isBlank() || settlementFingerprint.equals(this.lastSettlementFingerprint)) {
            return;
        }
        this.resetReadyStateForNextRound();
        this.openSettlementForViewers();
        this.cancelNextRoundCountdown();
        this.promptPlayersToReady();
    }

    private void openSettlementForViewers() {
        for (Player player : this.viewers()) {
            SettlementUi.open(player, this);
            if (!this.engine.getGameFinished()) {
                this.plugin.messages().send(player, "table.round_finished_ready");
            } else {
                this.plugin.messages().send(player, "table.match_finished_ready");
            }
        }
    }

    private void syncSeatFeedbackStates() {
        for (UUID playerId : this.seats) {
            if (playerId == null) {
                continue;
            }
            if (this.isBot(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            this.syncSeatFeedbackState(player, this.capturePlayerFeedbackSnapshot(player, playerId));
        }
    }

    private void syncSeatFeedbackState(Player player, PlayerFeedbackSnapshot snapshot) {
        String previous = this.feedbackState.put(snapshot.playerId(), snapshot.signature());
        if (Objects.equals(snapshot.signature(), previous)) {
            return;
        }
        if (snapshot.signature().isBlank()) {
            player.sendActionBar(Component.empty());
            return;
        }
        this.sendFeedback(player, snapshot);
    }

    private PlayerFeedbackSnapshot capturePlayerFeedbackSnapshot(Player player, UUID playerId) {
        Locale locale = this.plugin.messages().resolveLocale(player);
        ReactionOptions options = this.engine.availableReactions(playerId.toString());
        if (options != null && this.engine.getPendingReaction() != null) {
            Component actionBar = this.plugin.messages().render(locale, "table.reaction_available");
            Component reactionPrompt = this.reactionPrompt(locale, options);
            String signature = fingerprintBuilder(192)
                .field("reaction")
                .field(playerId)
                .field(this.engine.getPendingReaction().getTile().getId())
                .field(options)
                .toString();
            return new PlayerFeedbackSnapshot(playerId, signature, actionBar, reactionPrompt);
        }
        if (this.engine.getStarted() && this.engine.getCurrentPlayer().getUuid().equals(playerId.toString())) {
            List<String> actions = new ArrayList<>(4);
            if (this.engine.getCurrentPlayer().isRiichiable()) {
                actions.add("/mahjong riichi <index>");
            }
            if (this.engine.getCurrentPlayer().getCanAnkan() || this.engine.getCurrentPlayer().getCanKakan()) {
                actions.add("/mahjong kan <tile>");
            }
            if (this.engine.canKyuushuKyuuhai(playerId.toString())) {
                actions.add("/mahjong kyuushu");
            }
            actions.add("/mahjong tsumo");
            String suffix = actions.isEmpty() ? "" : " | " + String.join(" ", actions);
            Component actionBar = this.plugin.messages().render(locale, "table.turn_prompt", this.plugin.messages().tag("suffix", suffix));
            String signature = fingerprintBuilder(192)
                .field("turn")
                .field(playerId)
                .field(this.engine.getWall().size())
                .field(this.engine.getCurrentPlayer().isRiichiable())
                .field(this.engine.getCurrentPlayer().getCanAnkan())
                .field(this.engine.getCurrentPlayer().getCanKakan())
                .field(this.engine.canKyuushuKyuuhai(playerId.toString()))
                .field(suffix)
                .toString();
            return new PlayerFeedbackSnapshot(playerId, signature, actionBar, null);
        }
        return new PlayerFeedbackSnapshot(playerId, "", null, null);
    }

    private Component reactionPrompt(Player player, ReactionOptions options) {
        return this.reactionPrompt(this.plugin.messages().resolveLocale(player), options);
    }

    private Component reactionPrompt(Locale locale, ReactionOptions options) {
        Component message = this.plugin.messages().render(locale, "table.reactions_prefix");
        if (options.getCanRon()) {
            message = message.append(this.actionButton(this.plugin.messages().plain(locale, "table.action.ron"), "/mahjong ron", NamedTextColor.RED)).append(Component.space());
        }
        if (options.getCanPon()) {
            message = message.append(this.actionButton(this.plugin.messages().plain(locale, "table.action.pon"), "/mahjong pon", NamedTextColor.YELLOW)).append(Component.space());
        }
        if (options.getCanMinkan()) {
            message = message.append(this.actionButton(this.plugin.messages().plain(locale, "table.action.minkan"), "/mahjong minkan", NamedTextColor.DARK_AQUA)).append(Component.space());
        }
        for (Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> pair : options.getChiiPairs()) {
            String command = "/mahjong chii " + pair.getFirst().name().toLowerCase(Locale.ROOT) + " " + pair.getSecond().name().toLowerCase(Locale.ROOT);
            String label = this.plugin.messages().plain(locale, "table.action.chii") + " "
                + this.tileLabel(locale, pair.getFirst().name()) + " " + this.tileLabel(locale, pair.getSecond().name());
            message = message.append(this.actionButton(label, command, NamedTextColor.GREEN)).append(Component.space());
        }
        return message.append(this.actionButton(this.plugin.messages().plain(locale, "table.action.skip"), "/mahjong skip", NamedTextColor.GRAY));
    }

    private Component actionButton(String label, String command, NamedTextColor color) {
        return Component.text("[" + label + "]", color)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)));
    }

    private void appendReactionActionLabels(StringBuilder builder, Locale locale, ReactionOptions options) {
        if (options.getCanRon()) {
            builder.append(' ').append(this.plugin.messages().plain(locale, "table.action.ron"));
        }
        if (options.getCanPon()) {
            builder.append(' ').append(this.plugin.messages().plain(locale, "table.action.pon"));
        }
        if (options.getCanMinkan()) {
            builder.append(' ').append(this.plugin.messages().plain(locale, "table.action.minkan"));
        }
        if (!options.getChiiPairs().isEmpty()) {
            builder.append(' ').append(this.plugin.messages().plain(locale, "table.action.chii"));
        }
    }

    private Component waitingViewerOverlay(Locale locale, ViewerSummarySnapshot summary) {
        return this.plugin.messages().render(
            locale,
            "overlay.waiting",
            this.plugin.messages().tag("table_id", this.id),
            this.plugin.messages().tag("summary", summary.waitingSummary())
        );
    }

    private Component finishedViewerOverlay(Locale locale, ViewerSummarySnapshot summary) {
        return this.plugin.messages().render(
            locale,
            "overlay.finished",
            this.plugin.messages().tag("round", summary.round()),
            this.plugin.messages().tag("title", summary.resolutionTitle())
        );
    }

    private Component nextRoundViewerOverlay(Locale locale, ViewerSummarySnapshot summary) {
        return this.plugin.messages().render(
            locale,
            "overlay.waiting",
            this.plugin.messages().tag("table_id", this.id),
            this.plugin.messages().tag("summary", summary.waitingSummary())
        );
    }

    private Component activeViewerOverlay(Locale locale, ViewerSummarySnapshot summary) {
        return this.plugin.messages().render(
            locale,
            "overlay.active",
            this.plugin.messages().tag("role", summary.roleLabel()),
            this.plugin.messages().tag("round", summary.round()),
            this.plugin.messages().tag("turn", summary.turn()),
            this.plugin.messages().number(locale, "wall", summary.wall()),
            this.plugin.messages().tag("last_discard", summary.lastDiscardSummary()),
            this.plugin.messages().tag("prompt", summary.viewerPrompt())
        );
    }

    private String viewerRoleLabel(Locale locale, UUID viewerId) {
        return this.plugin.messages().plain(locale, this.isSpectator(viewerId) ? "hud.role_spectator" : "hud.role_player");
    }

    private ViewerSummarySnapshot captureViewerSummarySnapshot(Locale locale, UUID viewerId) {
        boolean spectator = this.isSpectator(viewerId);
        String waitingSummary = this.waitingDisplaySummary(locale);
        String ruleSummary = this.ruleDisplaySummary(locale);
        if (this.engine == null) {
            return new ViewerSummarySnapshot(
                locale,
                viewerId,
                spectator,
                waitingSummary,
                ruleSummary,
                "",
                "",
                0,
                this.viewerRoleLabel(locale, viewerId),
                "",
                "",
                "",
                "",
                ""
            );
        }

        String round = this.roundDisplay(locale);
        String turn = this.engine.getCurrentPlayer().getDisplayName();
        int wall = this.engine.getWall().size();
        String roleLabel = this.viewerRoleLabel(locale, viewerId);
        String lastDiscardSummary = this.lastDiscardSummary(locale);
        ReactionOptions options = this.engine.availableReactions(viewerId.toString());
        String reactionOptionsFingerprint = Objects.toString(options, "");
        String viewerPrompt = this.viewerPrompt(locale, viewerId, options, turn, spectator);
        String resolutionTitle = this.engine.getLastResolution() == null
            ? ""
            : this.resolutionLabel(locale, this.engine.getLastResolution().getTitle());
        String commandStateSummary = this.buildCommandStateSummary(locale, viewerId, round, turn, wall, options, resolutionTitle);
        return new ViewerSummarySnapshot(
            locale,
            viewerId,
            spectator,
            waitingSummary,
            ruleSummary,
            round,
            turn,
            wall,
            roleLabel,
            lastDiscardSummary,
            viewerPrompt,
            resolutionTitle,
            reactionOptionsFingerprint,
            commandStateSummary
        );
    }

    private String buildCommandStateSummary(
        Locale locale,
        UUID viewerId,
        String round,
        String turn,
        int wall,
        ReactionOptions options,
        String resolutionTitle
    ) {
        StringBuilder builder = new StringBuilder(128);
        builder.append(this.plugin.messages().plain(locale, "state.label.round")).append(' ').append(round);
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.turn")).append(' ').append(turn);
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.wall")).append(' ').append(wall);
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.spectators")).append(' ').append(this.spectatorCount());
        if (this.engine.getPendingReaction() != null && options != null) {
            builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.reactions"));
            this.appendReactionActionLabels(builder, locale, options);
        }
        if (this.engine.getLastResolution() != null) {
            builder.append(" | ")
                .append(this.plugin.messages().plain(locale, "state.label.resolution"))
                .append(' ')
                .append(resolutionTitle);
        }
        if (!this.engine.getStarted() && !this.engine.getGameFinished()) {
            builder.append(" | ")
                .append(this.plugin.messages().plain(locale, "state.label.next_round"))
                .append(' ')
                .append(this.plugin.messages().plain(
                    locale,
                    "state.ready_summary",
                    this.plugin.messages().number(locale, "ready", this.readyCount()),
                    this.plugin.messages().number(locale, "total", this.size())
                ));
        }
        if (this.engine.getGameFinished()) {
            builder.append(" | ").append(this.plugin.messages().plain(locale, "state.match_finished"));
        }
        return builder.toString();
    }

    private String viewerPrompt(Locale locale, UUID viewerId, ReactionOptions options, String currentTurnName, boolean spectator) {
        if (options != null) {
            return this.reactionSummary(locale, options);
        }
        if (this.engine.getCurrentPlayer().getUuid().equals(viewerId.toString())) {
            return this.plugin.messages().plain(locale, "overlay.your_turn");
        }
        if (spectator) {
            return this.plugin.messages().plain(locale, "overlay.spectating_turn") + " " + currentTurnName;
        }
        return "";
    }

    private String spectatorSeatStatus(Locale locale, SeatWind wind, UUID playerId) {
        String status = "";
        if (this.currentSeat() == wind && this.isStarted()) {
            status = this.plugin.messages().plain(locale, "overlay.status_turn");
        }
        if (!this.isRiichi(playerId)) {
            return status;
        }
        String riichi = this.plugin.messages().plain(locale, "overlay.status_riichi");
        return status.isBlank() ? riichi : status + " / " + riichi;
    }

    private ViewerOverlaySnapshot captureViewerOverlaySnapshot(Player viewer) {
        Locale locale = this.plugin.messages().resolveLocale(viewer);
        UUID viewerId = viewer.getUniqueId();
        String regionKey = "viewer-overlay:" + viewerId;
        boolean spectator = this.isSpectator(viewerId);
        ViewerSummarySnapshot summary = this.captureViewerSummarySnapshot(locale, viewerId);
        Component overlay = this.viewerOverlay(locale, summary);
        List<SpectatorSeatOverlaySnapshot> seatOverlays = spectator
            ? this.captureSpectatorSeatOverlays(locale)
            : List.of();
        String fingerprint = this.viewerOverlayFingerprint(locale, viewerId, spectator, summary, seatOverlays);
        return new ViewerOverlaySnapshot(viewerId, regionKey, spectator, overlay, seatOverlays, fingerprint);
    }

    private List<SpectatorSeatOverlaySnapshot> captureSpectatorSeatOverlays(Locale locale) {
        List<SpectatorSeatOverlaySnapshot> overlays = new ArrayList<>(SeatWind.values().length);
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            String signature;
            Component overlay;
            if (playerId == null) {
                signature = fingerprintBuilder(64)
                    .field(wind.name())
                    .field("empty")
                    .toString();
                overlay = this.plugin.messages().render(
                    locale,
                    "overlay.seat_empty",
                    this.plugin.messages().tag("seat", this.seatDisplayName(wind, locale))
                );
            } else {
                String status = this.spectatorSeatStatus(locale, wind, playerId);
                int points = this.points(playerId);
                int handCount = this.hand(playerId).size();
                int riverCount = this.discards(playerId).size();
                int meldCount = this.fuuro(playerId).size();
                String displayName = this.displayName(playerId, locale);
                signature = fingerprintBuilder(160)
                    .field(wind.name())
                    .field(playerId)
                    .field(displayName)
                    .field(points)
                    .field(handCount)
                    .field(riverCount)
                    .field(meldCount)
                    .field(status)
                    .toString();
                overlay = this.plugin.messages().render(
                    locale,
                    "overlay.seat",
                    this.plugin.messages().tag("seat", this.seatDisplayName(wind, locale)),
                    this.plugin.messages().tag("name", displayName),
                    this.plugin.messages().number(locale, "points", points),
                    this.plugin.messages().number(locale, "hand", handCount),
                    this.plugin.messages().number(locale, "river", riverCount),
                    this.plugin.messages().number(locale, "melds", meldCount),
                    this.plugin.messages().tag("status", status)
                );
            }
            overlays.add(new SpectatorSeatOverlaySnapshot(wind, overlay, signature));
        }
        return List.copyOf(overlays);
    }

    private String viewerOverlayFingerprint(
        Locale locale,
        UUID viewerId,
        boolean spectator,
        ViewerSummarySnapshot summary,
        List<SpectatorSeatOverlaySnapshot> seatOverlays
    ) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field(locale.toLanguageTag())
            .field(viewerId)
            .field(spectator)
            .field(this.nextRoundSecondsRemaining())
            .field(this.engine == null ? null : this.engine.getLastResolution())
            .field(summary.waitingSummary())
            .field(summary.ruleSummary())
            .field(summary.round())
            .field(summary.turn())
            .field(summary.wall())
            .field(summary.roleLabel())
            .field(summary.lastDiscardSummary())
            .field(summary.viewerPrompt())
            .field(summary.resolutionTitle())
            .field(summary.commandStateSummary());
        if (this.engine == null) {
            return builder.field("no-engine").toString();
        }
        builder = this.appendActiveRoundFingerprint(builder)
            .field(summary.reactionOptionsFingerprint());
        for (SpectatorSeatOverlaySnapshot seatOverlay : seatOverlays) {
            builder.field(seatOverlay.signature());
        }
        return builder.toString();
    }

    private void updateViewerOverlayRegions() {
        List<Player> viewers = this.viewers();
        Set<String> activeKeys = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            ViewerOverlaySnapshot snapshot = this.captureViewerOverlaySnapshot(viewer);
            activeKeys.add(snapshot.regionKey());
            this.updateRegion(snapshot.regionKey(), snapshot.fingerprint(), () -> this.renderer.renderViewerOverlay(this, snapshot));
        }
        for (String regionKey : List.copyOf(this.regionDisplays.keySet())) {
            if (regionKey.startsWith("viewer-overlay:") && !activeKeys.contains(regionKey)) {
                this.removeRegionDisplays(regionKey);
            }
        }
    }

    private void syncHud() {
        List<Player> viewers = this.viewers();
        Set<UUID> onlineViewerIds = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            this.syncViewerHud(viewer, onlineViewerIds);
        }
        this.hideOfflineHud(onlineViewerIds);
    }

    private float hudProgress() {
        if (this.engine == null) {
            return Math.min(1.0F, this.size() / 4.0F);
        }
        if (!this.engine.getStarted()) {
            return this.size() == 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, this.readyCount() / (float) this.size()));
        }
        if (this.engine.getWall().isEmpty()) {
            return 0.0F;
        }
        return Math.max(0.03F, Math.min(1.0F, this.engine.getWall().size() / 70.0F));
    }

    private BossBar.Color hudColor(UUID viewerId) {
        if (this.engine == null) {
            return BossBar.Color.WHITE;
        }
        if (this.engine.getGameFinished()) {
            return BossBar.Color.PURPLE;
        }
        if (!this.engine.getStarted()) {
            return BossBar.Color.YELLOW;
        }
        if (this.engine.availableReactions(viewerId.toString()) != null) {
            return BossBar.Color.RED;
        }
        if (this.engine.getCurrentPlayer().getUuid().equals(viewerId.toString())) {
            return BossBar.Color.GREEN;
        }
        return this.isSpectator(viewerId) ? BossBar.Color.BLUE : BossBar.Color.WHITE;
    }

    private void syncViewerHud(Player viewer, Set<UUID> onlineViewerIds) {
        UUID viewerId = viewer.getUniqueId();
        onlineViewerIds.add(viewerId);
        Locale locale = this.plugin.messages().resolveLocale(viewer);
        ViewerHudSnapshot snapshot = this.captureViewerHudSnapshot(locale, viewerId);
        BossBar bar = this.viewerHudBars.get(viewerId);
        if (bar == null) {
            bar = this.createHudBar(viewerId, viewer);
        }
        if (Objects.equals(this.viewerHudState.get(viewerId), snapshot.stateSignature())) {
            return;
        }
        bar.name(snapshot.title());
        bar.progress(snapshot.progress());
        bar.color(snapshot.color());
        this.viewerHudState.put(viewerId, snapshot.stateSignature());
    }

    private BossBar createHudBar(UUID viewerId, Player viewer) {
        BossBar bar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        this.viewerHudBars.put(viewerId, bar);
        viewer.showBossBar(bar);
        return bar;
    }

    private void hideOfflineHud(Set<UUID> onlineViewerIds) {
        for (UUID viewerId : List.copyOf(this.viewerHudBars.keySet())) {
            if (!onlineViewerIds.contains(viewerId)) {
                this.hideHud(viewerId);
            }
        }
    }

    private ViewerHudSnapshot captureViewerHudSnapshot(Locale locale, UUID viewerId) {
        float progress = this.hudProgress();
        BossBar.Color color = this.hudColor(viewerId);
        ViewerSummarySnapshot summary = this.captureViewerSummarySnapshot(locale, viewerId);
        boolean spectator = summary.spectator();
        long nextRoundSeconds = this.nextRoundSecondsRemaining();
        Object lastResolution = this.engine == null ? null : this.engine.getLastResolution();
        Component title;
        String stateSignature;

        if (this.engine == null) {
            title = this.plugin.messages().render(
                locale,
                "hud.waiting",
                this.plugin.messages().tag("table_id", this.id),
                this.plugin.messages().tag("summary", summary.waitingSummary())
            );
            stateSignature = fingerprintBuilder(192)
                .field(locale.toLanguageTag())
                .field(progress)
                .field(color)
                .field(nextRoundSeconds)
                .field(spectator)
                .field(false)
                .field(lastResolution)
                .field(summary.waitingSummary())
                .field(summary.ruleSummary())
                .toString();
            return new ViewerHudSnapshot(title, progress, color, stateSignature);
        }

        if (this.engine.getGameFinished() && this.engine.getLastResolution() != null) {
            title = this.plugin.messages().render(
                locale,
                "hud.finished",
                this.plugin.messages().tag("round", summary.round()),
                this.plugin.messages().tag("title", summary.resolutionTitle())
            );
            stateSignature = fingerprintBuilder(192)
                .field(locale.toLanguageTag())
                .field(progress)
                .field(color)
                .field(nextRoundSeconds)
                .field(spectator)
                .field(this.isStarted())
                .field(lastResolution)
                .field(summary.round())
                .field(summary.resolutionTitle())
                .toString();
            return new ViewerHudSnapshot(title, progress, color, stateSignature);
        }

        if (!this.engine.getStarted()) {
            title = this.plugin.messages().render(
                locale,
                "hud.waiting",
                this.plugin.messages().tag("table_id", this.id),
                this.plugin.messages().tag("summary", summary.waitingSummary())
            );
            stateSignature = fingerprintBuilder(192)
                .field(locale.toLanguageTag())
                .field(progress)
                .field(color)
                .field(nextRoundSeconds)
                .field(spectator)
                .field(false)
                .field(lastResolution)
                .field(summary.waitingSummary())
                .field(summary.ruleSummary())
                .toString();
            return new ViewerHudSnapshot(title, progress, color, stateSignature);
        }

        title = this.plugin.messages().render(
            locale,
            "hud.round",
            this.plugin.messages().tag("round", summary.round()),
            this.plugin.messages().tag("turn", summary.turn()),
            this.plugin.messages().number(locale, "wall", summary.wall()),
            this.plugin.messages().tag("role", summary.roleLabel())
        );
        stateSignature = fingerprintBuilder(192)
            .field(locale.toLanguageTag())
            .field(progress)
            .field(color)
            .field(nextRoundSeconds)
            .field(spectator)
            .field(true)
            .field(lastResolution)
            .field(summary.round())
            .field(summary.turn())
            .field(summary.wall())
            .field(summary.roleLabel())
            .field(this.lastPublicDiscardPlayerId)
            .field(this.lastPublicDiscardTile)
            .field(summary.reactionOptionsFingerprint())
            .toString();
        return new ViewerHudSnapshot(title, progress, color, stateSignature);
    }

    private void hideHud(UUID viewerId) {
        BossBar bar = this.viewerHudBars.remove(viewerId);
        this.viewerHudState.remove(viewerId);
        Player player = Bukkit.getPlayer(viewerId);
        if (bar != null && player != null) {
            player.hideBossBar(bar);
        }
    }

    private void clearHud() {
        for (UUID viewerId : List.copyOf(this.viewerHudBars.keySet())) {
            this.hideHud(viewerId);
        }
    }

    private void playStateSounds() {
        if (this.engine == null) {
            this.resetSoundFingerprints();
            return;
        }
        this.syncTurnSound();
        this.syncRiichiSound();
        this.syncResolutionSound();
    }

    private void broadcastSound(Sound sound, float volume, float pitch) {
        for (Player viewer : this.viewers()) {
            viewer.playSound(viewer.getLocation(), sound, volume, pitch);
        }
    }

    private void playReactionSound(ReactionResponse response) {
        if (response.getType() == ReactionType.CHII) {
            this.broadcastSound(Sound.ENTITY_PLAYER_BURP, 0.85F, 1.15F);
        }
    }

    private void resetSoundFingerprints() {
        this.lastTurnSoundFingerprint = "";
        this.lastRiichiSoundFingerprint = "";
        this.lastResolutionSoundFingerprint = "";
    }

    private void syncTurnSound() {
        String turnFingerprint = this.engine.getStarted()
            ? this.engine.getCurrentPlayerIndex() + ":" + this.engine.getWall().size() + ":" + Objects.toString(this.engine.getPendingReaction(), "")
            : "";
        if (!turnFingerprint.isBlank() && !turnFingerprint.equals(this.lastTurnSoundFingerprint)) {
            this.broadcastSound(Sound.UI_BUTTON_CLICK, 0.5F, 1.6F);
        }
        this.lastTurnSoundFingerprint = turnFingerprint;
    }

    private void syncRiichiSound() {
        String riichiFingerprint = this.riichiFingerprint();
        if (!riichiFingerprint.equals(this.lastRiichiSoundFingerprint) && !this.lastRiichiSoundFingerprint.isBlank()) {
            this.broadcastSound(Sound.BLOCK_BELL_USE, 0.8F, 1.25F);
        }
        this.lastRiichiSoundFingerprint = riichiFingerprint;
    }

    private void syncResolutionSound() {
        String resolutionFingerprint = Objects.toString(this.engine.getLastResolution(), "");
        if (!resolutionFingerprint.isBlank() && !resolutionFingerprint.equals(this.lastResolutionSoundFingerprint)) {
            Sound sound = this.engine.getLastResolution().getDraw() == null ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_BELL;
            this.broadcastSound(sound, 0.9F, 1.0F);
        }
        this.lastResolutionSoundFingerprint = resolutionFingerprint;
    }

    private String reactionSummary(Locale locale, ReactionOptions options) {
        List<String> actions = new ArrayList<>(4);
        if (options.getCanRon()) {
            actions.add(this.plugin.messages().plain(locale, "table.action.ron"));
        }
        if (options.getCanPon()) {
            actions.add(this.plugin.messages().plain(locale, "table.action.pon"));
        }
        if (options.getCanMinkan()) {
            actions.add(this.plugin.messages().plain(locale, "table.action.minkan"));
        }
        if (!options.getChiiPairs().isEmpty()) {
            actions.add(this.plugin.messages().plain(locale, "table.action.chii"));
        }
        return this.plugin.messages().plain(locale, "overlay.reactions") + " " + String.join("/", actions);
    }

    private String viewerMembershipSignature(UUID excludedPlayerId) {
        StringBuilder builder = new StringBuilder(96);
        this.appendOnlineViewerIds(builder, excludedPlayerId);
        return builder.toString();
    }

    private void appendOnlineViewers(List<Player> target) {
        this.appendOnlineSeatViewers(target);
        this.appendOnlineSpectators(target);
    }

    private void appendOnlineViewerIds(List<UUID> target, UUID excludedPlayerId) {
        this.appendOnlineSeatViewerIds(target, excludedPlayerId);
        this.appendOnlineSpectatorIds(target, excludedPlayerId);
    }

    private void appendOnlineViewerIds(StringBuilder target, UUID excludedPlayerId) {
        this.appendOnlineSeatViewerIds(target, excludedPlayerId);
        this.appendOnlineSpectatorIds(target, excludedPlayerId);
    }

    private void appendOnlineSeatViewers(List<Player> target) {
        for (UUID playerId : this.seats) {
            if (playerId == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                target.add(player);
            }
        }
    }

    private void appendOnlineSpectators(List<Player> target) {
        for (UUID spectatorId : this.spectators) {
            Player player = Bukkit.getPlayer(spectatorId);
            if (player != null) {
                target.add(player);
            }
        }
    }

    private void appendOnlineSeatViewerIds(List<UUID> target, UUID excludedPlayerId) {
        for (UUID playerId : this.seats) {
            if (playerId == null) {
                continue;
            }
            if (!playerId.equals(excludedPlayerId) && Bukkit.getPlayer(playerId) != null) {
                target.add(playerId);
            }
        }
    }

    private void appendOnlineSpectatorIds(List<UUID> target, UUID excludedPlayerId) {
        for (UUID spectatorId : this.spectators) {
            if (!spectatorId.equals(excludedPlayerId) && Bukkit.getPlayer(spectatorId) != null) {
                target.add(spectatorId);
            }
        }
    }

    private void appendOnlineSeatViewerIds(StringBuilder target, UUID excludedPlayerId) {
        for (UUID playerId : this.seats) {
            if (playerId == null) {
                continue;
            }
            if (!playerId.equals(excludedPlayerId) && Bukkit.getPlayer(playerId) != null) {
                target.append(playerId).append(';');
            }
        }
    }

    private void appendOnlineSpectatorIds(StringBuilder target, UUID excludedPlayerId) {
        for (UUID spectatorId : this.spectators) {
            if (!spectatorId.equals(excludedPlayerId) && Bukkit.getPlayer(spectatorId) != null) {
                target.append(spectatorId).append(';');
            }
        }
    }

    private String riichiFingerprint() {
        FingerprintBuilder builder = fingerprintBuilder(64);
        for (UUID playerId : this.seats) {
            if (playerId == null) {
                continue;
            }
            builder.field(playerId).field(this.isRiichi(playerId)).entrySeparator();
        }
        return builder.toString();
    }

    private void scheduleNextRoundCountdown() {
        this.nextRoundDeadlineMillis = System.currentTimeMillis() + NEXT_ROUND_DELAY_MILLIS;
    }

    private void cancelNextRoundCountdown() {
        this.nextRoundDeadlineMillis = 0L;
    }

    private long nextRoundSecondsRemaining() {
        if (this.nextRoundDeadlineMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (long) Math.ceil((this.nextRoundDeadlineMillis - System.currentTimeMillis()) / 1000.0D));
    }

    private RenderSnapshot captureRenderSnapshot(long version) {
        Location tableCenter = this.center();
        EnumMap<SeatWind, SeatRenderSnapshot> seats = new EnumMap<>(SeatWind.class);
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            seats.put(wind, new SeatRenderSnapshot(
                wind,
                playerId,
                this.displayName(playerId),
                this.publicSeatStatus(wind),
                this.points(playerId),
                this.isRiichi(playerId),
                this.isReady(playerId),
                this.isQueuedToLeave(playerId),
                playerId != null && this.onlinePlayer(playerId) != null,
                playerId == null ? "" : this.viewerMembershipSignature(playerId),
                playerId == null ? -1 : this.selectedHandTileIndex(playerId),
                playerId == null ? -1 : this.riichiDiscardIndex(playerId),
                this.stickLayoutCount(wind),
                playerId == null ? List.of() : this.viewerIdsExcluding(playerId),
                playerId == null ? List.of() : this.hand(playerId),
                playerId == null ? List.of() : this.discards(playerId),
                playerId == null ? List.of() : this.fuuro(playerId),
                playerId == null ? List.of() : this.scoringSticks(playerId),
                this.cornerSticks(wind)
            ));
        }
        return new RenderSnapshot(
            version,
            this.renderCancellationNonce,
            Objects.toString(tableCenter.getWorld() == null ? null : tableCenter.getWorld().getName(), ""),
            tableCenter.getX(),
            tableCenter.getY(),
            tableCenter.getZ(),
            this.isStarted(),
            this.engine != null && this.engine.getGameFinished(),
            this.remainingWall().size(),
            this.kanCount(),
            this.dicePoints(),
            this.roundIndex(),
            this.honbaCount(),
            this.dealerSeat(),
            this.currentSeat(),
            this.openDoorSeat(),
            this.waitingDisplaySummary(),
            this.ruleDisplaySummary(),
            this.publicCenterText(),
            this.lastPublicDiscardPlayerId,
            this.lastPublicDiscardTile,
            List.copyOf(this.doraIndicators()),
            seats
        );
    }

    private Map<String, String> precomputeRegionFingerprints(RenderSnapshot snapshot) {
        Map<String, String> fingerprints = new HashMap<>();
        fingerprints.put(REGION_TABLE, this.tableFingerprint(snapshot));
        fingerprints.put(REGION_WALL, this.wallFingerprint(snapshot));
        fingerprints.put(REGION_DORA, this.doraFingerprint(snapshot));
        fingerprints.put(REGION_CENTER, this.centerFingerprint(snapshot));
        for (SeatWind wind : SeatWind.values()) {
            SeatRenderSnapshot seat = snapshot.seat(wind);
            fingerprints.put(this.seatRegionKey("labels", wind), this.seatLabelFingerprint(snapshot, seat));
            fingerprints.put(this.seatRegionKey("sticks", wind), this.stickFingerprint(snapshot, seat));
            fingerprints.put(this.seatRegionKey("hand-public", wind), this.handPublicFingerprint(snapshot, seat));
            fingerprints.put(this.seatRegionKey("hand-private", wind), this.handPrivateFingerprint(seat));
            fingerprints.put(this.seatRegionKey("discards", wind), this.discardFingerprint(seat));
            fingerprints.put(this.seatRegionKey("melds", wind), this.meldFingerprint(seat));
        }
        return Map.copyOf(fingerprints);
    }

    private String wallFingerprint(RenderSnapshot snapshot) {
        if (!snapshot.started()) {
            return "waiting";
        }
        return fingerprintBuilder(32)
            .field("wall")
            .field(snapshot.started())
            .field(snapshot.gameFinished())
            .field(snapshot.remainingWallCount())
            .toString();
    }

    private String tableFingerprint(RenderSnapshot snapshot) {
        return fingerprintBuilder(48)
            .field("table")
            .field(snapshot.worldName())
            .field((int) Math.floor(snapshot.centerX()))
            .field((int) Math.floor(snapshot.centerY()))
            .field((int) Math.floor(snapshot.centerZ()))
            .toString();
    }

    private String doraFingerprint(RenderSnapshot snapshot) {
        if (!snapshot.started()) {
            return "dora:waiting";
        }
        FingerprintBuilder builder = fingerprintBuilder(64)
            .field("dora")
            .field(snapshot.started())
            .field(snapshot.doraIndicators().size());
        snapshot.doraIndicators().forEach(tile -> builder.raw(tile.name()).raw(','));
        return builder.toString();
    }

    private String centerFingerprint(RenderSnapshot snapshot) {
        return fingerprintBuilder(192)
            .field("center")
            .field(snapshot.started() ? "started" : "waiting")
            .field(snapshot.publicCenterText())
            .toString();
    }

    private String seatLabelFingerprint(RenderSnapshot snapshot, SeatRenderSnapshot seat) {
        return fingerprintBuilder(128)
            .field("labels")
            .field(seat.wind().name())
            .field(snapshot.currentSeat().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(seat.displayName())
            .field(seat.publicSeatStatus())
            .field(seat.riichi())
            .field(seat.ready())
            .field(seat.queuedToLeave())
            .toString();
    }

    private String handPublicFingerprint(RenderSnapshot snapshot, SeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field("hand-public")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"));
        if (seat.playerId() == null) {
            return builder.toString();
        }
        builder.field(snapshot.started())
            .field(seat.online())
            .field(seat.viewerMembershipSignature())
            .field(seat.stickLayoutCount());
        seat.hand().forEach(tile -> builder.field(tile.name()));
        return builder.toString();
    }

    private String handPrivateFingerprint(SeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field("hand-private")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"));
        if (seat.playerId() == null) {
            return builder.toString();
        }
        builder.field(seat.online())
            .field(seat.stickLayoutCount())
            .field(seat.selectedHandTileIndex());
        seat.hand().forEach(tile -> builder.field(tile.name()));
        return builder.toString();
    }

    private String discardFingerprint(SeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field("discards")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"));
        if (seat.playerId() == null) {
            return builder.toString();
        }
        builder.field(seat.riichiDiscardIndex());
        seat.discards().forEach(tile -> builder.field(tile.name()));
        return builder.toString();
    }

    private String meldFingerprint(SeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(256)
            .field("melds")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"));
        if (seat.playerId() == null) {
            return builder.toString();
        }
        builder.field(seat.stickLayoutCount());
        seat.melds().forEach(meld -> this.appendMeldFingerprint(builder, meld));
        return builder.toString();
    }

    private String stickFingerprint(RenderSnapshot snapshot, SeatRenderSnapshot seat) {
        FingerprintBuilder builder = fingerprintBuilder(128)
            .field("sticks")
            .field(seat.wind().name())
            .field(Objects.toString(seat.playerId(), "empty"))
            .field(snapshot.honbaCount())
            .field(snapshot.dealerSeat().name());
        if (seat.playerId() == null) {
            return builder.toString();
        }
        builder.field(seat.riichi());
        seat.scoringSticks().forEach(stick -> builder.field(stick.name()));
        seat.cornerSticks().forEach(stick -> builder.field(stick.name()));
        return builder.toString();
    }

    private void updateStaticRegions() {
        this.updateRegion(REGION_TABLE, this.tableFingerprint(), () -> this.renderer.renderTableStructure(this));
        this.updateRegion(REGION_WALL, this.wallFingerprint(), () -> this.renderer.renderWall(this));
        this.updateRegion(REGION_DORA, this.doraFingerprint(), () -> this.renderer.renderDora(this));
        this.updateRegion(REGION_CENTER, this.centerFingerprint(), () -> this.renderer.renderCenterLabel(this));
    }

    private void updateSeatRegion(SeatWind wind) {
        this.updateRegion(this.seatRegionKey("labels", wind), this.seatLabelFingerprint(wind), () -> this.renderer.renderSeatLabels(this, wind));
        this.updateRegion(this.seatRegionKey("sticks", wind), this.stickFingerprint(wind), () -> this.renderer.renderSticks(this, wind));
        this.updateRegion(this.seatRegionKey("hand-public", wind), this.handPublicFingerprint(wind), () -> this.renderer.renderHandPublic(this, wind));
        this.clearLegacyHandPrivateTileRegions(wind);
        this.updateRegion(this.seatRegionKey("hand-private", wind), this.handPrivateFingerprint(wind), () -> this.renderer.renderHandPrivate(this, wind));
        this.updateRegion(this.seatRegionKey("discards", wind), this.discardFingerprint(wind), () -> this.renderer.renderDiscards(this, wind));
        this.updateRegion(this.seatRegionKey("melds", wind), this.meldFingerprint(wind), () -> this.renderer.renderMelds(this, wind));
    }

    private void clearLegacyHandPrivateTileRegions(SeatWind wind) {
        for (int tileIndex = 0; tileIndex < MAX_HAND_TILE_REGIONS; tileIndex++) {
            String regionKey = this.handPrivateRegionKey(wind, tileIndex);
            this.clearRegion(regionKey);
        }
    }

    private void updateRegion(String regionKey, String fingerprint, RegionRenderer renderer) {
        String previousFingerprint = this.regionFingerprints.get(regionKey);
        List<Entity> currentEntities = this.regionDisplays.get(regionKey);
        if (this.hasInvalidDisplayEntity(currentEntities)) {
            this.removeRegionDisplays(regionKey);
            previousFingerprint = null;
            currentEntities = null;
        }
        if (Objects.equals(previousFingerprint, fingerprint) && (currentEntities != null || this.regionFingerprints.containsKey(regionKey))) {
            return;
        }
        this.removeRegionDisplays(regionKey);
        List<Entity> entities = renderer.render();
        if (!entities.isEmpty()) {
            this.regionDisplays.put(regionKey, entities);
        }
        this.regionFingerprints.put(regionKey, fingerprint);
    }

    private void clearRegion(String regionKey) {
        this.removeRegionDisplays(regionKey);
        this.regionFingerprints.remove(regionKey);
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

    private void restoreDisplaysIfNeeded() {
        if (!this.isCenterChunkLoaded() || !this.hasStaleDisplayRegions()) {
            return;
        }
        this.render();
    }

    private boolean isCenterChunkLoaded() {
        World world = this.center.getWorld();
        return world != null
            && world.isChunkLoaded(Math.floorDiv(this.center.getBlockX(), 16), Math.floorDiv(this.center.getBlockZ(), 16));
    }

    private boolean hasStaleDisplayRegions() {
        for (List<Entity> entities : this.regionDisplays.values()) {
            if (this.hasInvalidDisplayEntity(entities)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInvalidDisplayEntity(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        for (Entity entity : entities) {
            if (entity == null || entity.isDead() || !entity.isValid()) {
                return true;
            }
        }
        return false;
    }

    private String seatRegionKey(String region, SeatWind wind) {
        return region + ":" + wind.name();
    }

    private String handPrivateRegionKey(SeatWind wind, int tileIndex) {
        return this.seatRegionKey("hand-private-" + tileIndex, wind);
    }

    private String wallFingerprint() {
        if (this.engine == null) {
            return "waiting";
        }
        return fingerprintBuilder(32)
            .field("wall")
            .field(this.engine.getStarted())
            .field(this.engine.getGameFinished())
            .field(this.engine.getWall().size())
            .toString();
    }

    private String tableFingerprint() {
        return fingerprintBuilder(48)
            .field("table")
            .field(this.center.getWorld().getName())
            .field(this.center.getBlockX())
            .field(this.center.getBlockY())
            .field(this.center.getBlockZ())
            .toString();
    }

    private String doraFingerprint() {
        if (this.engine == null) {
            return "dora:waiting";
        }
        FingerprintBuilder builder = fingerprintBuilder(64)
            .field("dora")
            .field(this.engine.getStarted())
            .field(this.engine.getDoraIndicators().size());
        this.engine.getDoraIndicators().forEach(tile -> builder.raw(tile.getMahjongTile().name()).raw(','));
        return builder.toString();
    }

    private String centerFingerprint() {
        FingerprintBuilder builder = fingerprintBuilder(128).field("center");
        if (this.isStarted()) {
            return builder.field("started")
                .field(this.roundDisplay())
                .field(this.remainingWall().size())
                .field(this.dicePoints())
                .field(this.dealerName())
                .field(this.currentSeat().name())
                .field(this.lastPublicDiscardPlayerId)
                .field(this.lastPublicDiscardTile)
                .toString();
        }
        return builder.field("waiting")
            .field(this.waitingDisplaySummary())
            .field(this.ruleDisplaySummary())
            .toString();
    }

    private String seatLabelFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        return fingerprintBuilder(96)
            .field("labels")
            .field(wind.name())
            .field(this.currentSeat().name())
            .field(Objects.toString(playerId, "empty"))
            .field(this.displayName(playerId))
            .field(this.points(playerId))
            .field(this.isRiichi(playerId))
            .field(this.isReady(playerId))
            .field(this.isQueuedToLeave(playerId))
            .toString();
    }

    private String handPublicFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        FingerprintBuilder builder = this.seatFingerprintBuilder("hand-public", wind, playerId, 256);
        if (playerId == null) {
            return builder.toString();
        }
        builder.field(this.onlinePlayer(playerId) != null);
        builder.field(this.viewerMembershipSignature(playerId));
        builder.field(this.stickLayoutCount(wind));
        this.hand(playerId).forEach(tile -> builder.field(tile.name()));
        return builder.toString();
    }

    private String handPrivateFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        FingerprintBuilder builder = this.seatFingerprintBuilder("hand-private", wind, playerId, 256);
        if (playerId == null) {
            return builder.toString();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand = this.hand(playerId);
        builder.field(this.onlinePlayer(playerId) != null)
            .field(this.stickLayoutCount(wind))
            .field(this.selectedHandTileIndex(playerId));
        hand.forEach(tile -> builder.field(tile.name()));
        return builder.toString();
    }

    private String discardFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        FingerprintBuilder builder = this.seatFingerprintBuilder("discards", wind, playerId, 256);
        if (playerId == null) {
            return builder.toString();
        }
        builder.field(this.riichiDiscardIndex(playerId));
        this.discards(playerId).forEach(tile -> builder.field(tile.name()));
        return builder.toString();
    }

    private String meldFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        FingerprintBuilder builder = this.seatFingerprintBuilder("melds", wind, playerId, 256);
        if (playerId == null) {
            return builder.toString();
        }
        builder.field(this.stickLayoutCount(wind));
        this.fuuro(playerId).forEach(meld -> this.appendMeldFingerprint(builder, meld));
        return builder.toString();
    }

    private String stickFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        FingerprintBuilder builder = this.seatFingerprintBuilder("sticks", wind, playerId, 128)
            .field(this.honbaCount())
            .field(this.dealerSeat().name());
        if (playerId == null) {
            return builder.toString();
        }
        builder.field(this.isRiichi(playerId));
        this.scoringSticks(playerId).forEach(stick -> builder.field(stick.name()));
        this.cornerSticks(wind).forEach(stick -> builder.field(stick.name()));
        return builder.toString();
    }

    private FingerprintBuilder seatFingerprintBuilder(String region, SeatWind wind, UUID playerId, int capacity) {
        return fingerprintBuilder(capacity)
            .field(region)
            .field(wind.name())
            .field(Objects.toString(playerId, "empty"));
    }

    private FingerprintBuilder appendActiveRoundFingerprint(FingerprintBuilder builder) {
        return builder
            .field(this.roundDisplay())
            .field(this.engine.getWall().size())
            .field(this.engine.getCurrentPlayerIndex())
            .field(this.lastPublicDiscardPlayerId)
            .field(this.lastPublicDiscardTile);
    }

    private void appendMeldFingerprint(FingerprintBuilder builder, MeldView meld) {
        builder.raw('[')
            .raw(meld.claimTileIndex())
            .raw('/')
            .raw(meld.claimYawOffset())
            .raw('/')
            .raw(Objects.toString(meld.addedKanTile(), "none"))
            .raw(':');
        for (int i = 0; i < meld.tiles().size(); i++) {
            builder.raw(meld.tiles().get(i).name())
                .raw('/')
                .raw(meld.faceDownAt(i))
                .raw(',');
        }
        builder.raw(']');
    }

    private static FingerprintBuilder fingerprintBuilder(int capacity) {
        return new FingerprintBuilder(capacity);
    }

    @FunctionalInterface
    private interface RegionRenderer {
        List<Entity> render();
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

    private record ViewerSummarySnapshot(
        Locale locale,
        UUID viewerId,
        boolean spectator,
        String waitingSummary,
        String ruleSummary,
        String round,
        String turn,
        int wall,
        String roleLabel,
        String lastDiscardSummary,
        String viewerPrompt,
        String resolutionTitle,
        String reactionOptionsFingerprint,
        String commandStateSummary
    ) {
    }

    private record PlayerFeedbackSnapshot(
        UUID playerId,
        String signature,
        Component actionBar,
        Component reactionPrompt
    ) {
    }

    private record ViewerHudSnapshot(
        Component title,
        float progress,
        BossBar.Color color,
        String stateSignature
    ) {
    }

    private record RenderPrecomputeResult(
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

    public String seatDisplayName(SeatWind wind, Locale locale) {
        return this.plugin.messages().plain(locale, wind.translationKey());
    }

    public String publicSeatStatus(SeatWind wind) {
        Locale locale = this.publicLocale();
        UUID playerId = this.playerAt(wind);
        if (playerId == null) {
            return this.plugin.messages().plain(
                locale,
                "table.public.seat_empty",
                this.plugin.messages().tag("seat", this.seatDisplayName(wind, locale))
            );
        }
        String status = this.waitingSeatStatus(locale, playerId);
        if (this.isStarted() && this.isRiichi(playerId)) {
            String riichi = this.plugin.messages().plain(locale, "overlay.status_riichi");
            status = status.isBlank() ? riichi : status + " | " + riichi;
        }
        return this.plugin.messages().plain(
            locale,
            "table.public.seat_status",
            this.plugin.messages().tag("seat", this.seatDisplayName(wind, locale)),
            this.plugin.messages().number(locale, "points", this.points(playerId)),
            this.plugin.messages().tag("status", status.isBlank() ? "" : " | " + status)
        );
    }

    public String publicCenterText() {
        Locale locale = this.publicLocale();
        if (this.isStarted()) {
            return this.plugin.messages().plain(
                locale,
                "table.public.center_active",
                this.plugin.messages().tag("round", this.roundDisplay(locale)),
                this.plugin.messages().number(locale, "wall", this.remainingWall().size()),
                this.plugin.messages().number(locale, "dice", this.dicePoints()),
                this.plugin.messages().tag("dealer", this.dealerName(locale)),
                this.plugin.messages().tag("last_discard", this.lastDiscardSummary(locale))
            );
        }
        return this.plugin.messages().plain(
            locale,
            "table.public.center_waiting",
            this.plugin.messages().tag("table_id", this.id),
            this.plugin.messages().tag("summary", this.waitingDisplaySummary(locale)),
            this.plugin.messages().tag("rules", this.ruleDisplaySummary(locale))
        );
    }

    private String roundWindText(Locale locale) {
        return switch (this.engine.getRound().getWind().name()) {
            case "EAST" -> this.plugin.messages().plain(locale, "seat.wind.east");
            case "SOUTH" -> this.plugin.messages().plain(locale, "seat.wind.south");
            case "WEST" -> this.plugin.messages().plain(locale, "seat.wind.west");
            case "NORTH" -> this.plugin.messages().plain(locale, "seat.wind.north");
            default -> this.engine.getRound().getWind().name();
        };
    }

    private String botDisplayName(UUID playerId, Locale locale) {
        String raw = this.botNames.get(playerId);
        if (raw == null) {
            return this.plugin.messages().plain(locale, "common.unknown");
        }
        int suffix = this.seats.indexOf(playerId) + 1;
        return this.plugin.messages().plain(locale, "table.bot_name", this.plugin.messages().number(locale, "index", Math.max(1, suffix)));
    }

    private String ruleLengthLabel(Locale locale, MahjongRule.GameLength length) {
        return this.plugin.messages().plain(locale, "rule.length." + length.name().toLowerCase(Locale.ROOT));
    }

    private String ruleThinkingLabel(Locale locale, MahjongRule.ThinkingTime thinkingTime) {
        return this.plugin.messages().plain(locale, "rule.thinking." + thinkingTime.name().toLowerCase(Locale.ROOT));
    }

    private String ruleMinimumHanLabel(Locale locale, MahjongRule.MinimumHan minimumHan) {
        return this.plugin.messages().plain(locale, "rule.minimum_han." + minimumHan.name().toLowerCase(Locale.ROOT));
    }

    private String ruleRedFiveLabel(Locale locale, MahjongRule.RedFive redFive) {
        return this.plugin.messages().plain(locale, "rule.red_five." + redFive.name().toLowerCase(Locale.ROOT));
    }

    private String booleanLabel(Locale locale, boolean value) {
        return this.plugin.messages().plain(locale, value ? "common.true" : "common.false");
    }

    private String tileLabel(Locale locale, String tileName) {
        String key = "tile." + tileName.toLowerCase(Locale.ROOT);
        return this.plugin.messages().contains(locale, key) ? this.plugin.messages().plain(locale, key) : tileName.toLowerCase(Locale.ROOT);
    }

    private String lastDiscardSummary(Locale locale) {
        if (this.lastPublicDiscardPlayerId == null || this.lastPublicDiscardTile == null) {
            return this.plugin.messages().plain(locale, "table.last_discard_none");
        }
        return this.plugin.messages().plain(
            locale,
            "table.last_discard",
            this.plugin.messages().tag("player", this.displayName(this.lastPublicDiscardPlayerId, locale)),
            this.plugin.messages().tag("tile", this.tileLabel(locale, this.lastPublicDiscardTile.name()))
        );
    }

    private String resolutionLabel(Locale locale, String title) {
        String key = "resolution." + title.toLowerCase(Locale.ROOT);
        return this.plugin.messages().contains(locale, key) ? this.plugin.messages().plain(locale, key) : title;
    }

    private String readySummary(Locale locale) {
        return this.plugin.messages().plain(
            locale,
            "table.ready_summary",
            this.plugin.messages().number(locale, "ready", this.readyCount()),
            this.plugin.messages().number(locale, "total", this.size())
        );
    }

    private String waitingSeatStatus(Locale locale, UUID playerId) {
        if (playerId == null || this.isStarted()) {
            return "";
        }
        if (this.isQueuedToLeave(playerId)) {
            return this.plugin.messages().plain(locale, "table.status.leaving");
        }
        return this.plugin.messages().plain(locale, this.isReady(playerId) ? "table.status.ready" : "table.status.waiting");
    }

    private void resetReadyStateForNextRound() {
        this.readyPlayers.clear();
        for (UUID playerId : this.seats) {
            if (playerId != null && this.isBot(playerId)) {
                this.readyPlayers.add(playerId);
            }
        }
    }

    private void promptPlayersToReady() {
        for (UUID playerId : this.seats) {
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
        if (this.isStarted() || this.size() != 4) {
            return false;
        }
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null || !this.isReady(playerId)) {
                return false;
            }
        }
        this.startRound();
        this.broadcastSound(Sound.BLOCK_BEACON_POWER_SELECT, 0.9F, 1.2F);
        return true;
    }

    private void processDeferredLeaves() {
        if (this.leaveAfterRoundPlayers.isEmpty() || this.isStarted()) {
            return;
        }
        List<UUID> removed = new ArrayList<>();
        for (UUID playerId : List.copyOf(this.leaveAfterRoundPlayers)) {
            if (this.removePlayer(playerId)) {
                removed.add(playerId);
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        this.leaveAfterRoundPlayers.removeAll(removed);
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

    private static Location normalizedTableCenter(Location source) {
        Location normalized = source.clone();
        normalized.setYaw(0.0F);
        normalized.setPitch(0.0F);
        return normalized;
    }

    private SeatWind firstEmptySeat() {
        for (SeatWind wind : SeatWind.values()) {
            if (this.playerAt(wind) == null) {
                return wind;
            }
        }
        return null;
    }

    public static final class FinalStanding {
        private final UUID playerId;
        private final String displayName;
        private final int place;
        private final int points;
        private final double gameScore;

        public FinalStanding(UUID playerId, String displayName, int place, int points, double gameScore) {
            this.playerId = playerId;
            this.displayName = displayName;
            this.place = place;
            this.points = points;
            this.gameScore = gameScore;
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
    }

    public enum ReadyResult {
        READY,
        UNREADY,
        STARTED,
        BLOCKED
    }
}
