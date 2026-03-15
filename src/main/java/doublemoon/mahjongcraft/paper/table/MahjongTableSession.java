package doublemoon.mahjongcraft.paper.table;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.i18n.LocalizedMessages;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.DisplayVisibilityRegistry;
import doublemoon.mahjongcraft.paper.render.MeldView;
import doublemoon.mahjongcraft.paper.render.TableDisplayRegistry;
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
import org.bukkit.Location;
import org.bukkit.Sound;
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
    private final List<UUID> seats = new ArrayList<>(4);
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<String, List<Entity>> regionDisplays = new LinkedHashMap<>();
    private final Map<String, String> regionFingerprints = new HashMap<>();
    private final TableRenderer renderer = new TableRenderer();
    private final Map<UUID, String> botNames = new LinkedHashMap<>();
    private final Map<UUID, String> feedbackState = new HashMap<>();
    private final Map<UUID, BossBar> viewerHudBars = new HashMap<>();
    private final Map<UUID, String> viewerHudState = new HashMap<>();
    private final Map<UUID, Integer> selectedHandTileIndices = new HashMap<>();
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
        this.center = center.clone();
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
        return this.center.clone();
    }

    public MahjongRule configuredRuleSnapshot() {
        return copyRule(this.configuredRule);
    }

    public boolean addPlayer(Player player) {
        if (this.seats.contains(player.getUniqueId()) || this.seats.size() >= 4) {
            return false;
        }
        this.spectators.remove(player.getUniqueId());
        this.seats.add(player.getUniqueId());
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
        if (this.isStarted() || this.seats.size() >= 4) {
            return false;
        }
        UUID botId;
        do {
            botId = UUID.nameUUIDFromBytes((this.id + "-bot-" + this.nextBotNumber).getBytes(StandardCharsets.UTF_8));
            this.nextBotNumber++;
        } while (this.seats.contains(botId));
        this.seats.add(botId);
        this.botNames.put(botId, "Bot-" + this.botNames.size());
        this.render();
        return true;
    }

    public boolean removeBot() {
        if (this.isStarted()) {
            return false;
        }
        for (int i = this.seats.size() - 1; i >= 0; i--) {
            UUID playerId = this.seats.get(i);
            if (this.botNames.containsKey(playerId)) {
                this.seats.remove(i);
                this.botNames.remove(playerId);
                this.feedbackState.remove(playerId);
                this.selectedHandTileIndices.remove(playerId);
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
        return this.seats.remove(playerId);
    }

    public boolean contains(UUID playerId) {
        return this.seats.contains(playerId);
    }

    public boolean isEmpty() {
        return this.seats.isEmpty();
    }

    public int size() {
        return this.seats.size();
    }

    public int botCount() {
        return this.botNames.size();
    }

    public int spectatorCount() {
        return this.spectators.size();
    }

    public List<UUID> players() {
        return List.copyOf(this.seats);
    }

    public Set<UUID> spectators() {
        return Set.copyOf(this.spectators);
    }

    public UUID owner() {
        return this.seats.isEmpty() ? null : this.seats.getFirst();
    }

    public void startRound() {
        if (this.seats.size() != 4) {
            throw new IllegalStateException("A riichi table needs exactly 4 players");
        }

        this.cancelNextRoundCountdown();
        if (this.engine == null || this.engine.getGameFinished()) {
            List<RiichiPlayerState> players = new ArrayList<>(this.seats.size());
            for (UUID playerId : this.seats) {
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
        this.updateStaticRegions();
        for (SeatWind wind : SeatWind.values()) {
            this.updateSeatRegion(wind);
        }
        this.updateViewerOverlayRegions();
        this.syncPlayerFeedback();
        this.syncHud();
        this.playStateSounds();
        BotActionScheduler.schedule(this);
    }

    public void clearDisplays() {
        this.regionFingerprints.clear();
        this.removeAllDisplays();
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

    public void shutdown() {
        this.cancelBotTask();
        this.cancelNextRoundCountdown();
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
        this.spectators.clear();
        this.clearHud();
        this.engine = null;
    }

    public void forceEndMatch() {
        this.cancelBotTask();
        this.cancelNextRoundCountdown();
        this.feedbackState.clear();
        this.selectedHandTileIndices.clear();
        this.clearLastPublicDiscard();
        this.lastSettlementFingerprint = "";
        this.lastPersistedSettlementFingerprint = "";
        this.regionFingerprints.clear();
        this.lastTurnSoundFingerprint = "";
        this.lastRiichiSoundFingerprint = "";
        this.lastResolutionSoundFingerprint = "";
        this.engine = null;
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
        return wind.index() < this.seats.size() ? this.seats.get(wind.index()) : null;
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
        );
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
        );
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
        StringBuilder builder = this.baseStateSummary(locale);
        this.appendReactionStateSummary(builder, locale, player.getUniqueId());
        this.appendResolutionStateSummary(builder, locale);
        this.appendNextRoundStateSummary(builder, locale);
        this.appendFinishedStateSummary(builder, locale);
        return this.plugin.messages().render(player, "command.rule_summary", this.plugin.messages().tag("summary", builder.toString()));
    }

    public Component viewerOverlay(Player viewer) {
        Locale locale = this.plugin.messages().resolveLocale(viewer);
        if (this.engine == null) {
            return this.waitingViewerOverlay(locale);
        }
        if (!this.engine.getStarted()) {
            if (this.engine.getGameFinished() && this.engine.getLastResolution() != null) {
                return this.finishedViewerOverlay(locale);
            }
            return this.nextRoundViewerOverlay(locale);
        }
        return this.activeViewerOverlay(locale, viewer);
    }

    public Component spectatorSeatOverlay(Player viewer, SeatWind wind) {
        Locale locale = this.plugin.messages().resolveLocale(viewer);
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
        this.updateViewerOverlayRegions();
        this.syncHud();
        if (this.engine == null || this.engine.getStarted() || this.engine.getGameFinished() || this.nextRoundDeadlineMillis <= 0L) {
            return;
        }
        if (System.currentTimeMillis() >= this.nextRoundDeadlineMillis) {
            this.startRound();
            this.broadcastSound(Sound.BLOCK_BEACON_POWER_SELECT, 0.9F, 1.2F);
        }
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

    private String feedbackSignature(UUID playerId) {
        ReactionOptions options = this.engine.availableReactions(playerId.toString());
        if (options != null && this.engine.getPendingReaction() != null) {
            return "reaction:" + this.engine.getPendingReaction().getTile().getId() + ":" + options;
        }
        if (this.engine.getStarted() && this.engine.getCurrentPlayer().getUuid().equals(playerId.toString())) {
            return "turn:" + this.engine.getWall().size() + ":" + this.engine.getCurrentPlayer().isRiichiable()
                + ":" + this.engine.getCurrentPlayer().getCanAnkan() + ":" + this.engine.getCurrentPlayer().getCanKakan()
                + ":" + this.engine.canKyuushuKyuuhai(playerId.toString());
        }
        return "";
    }

    private void sendFeedback(Player player, UUID playerId) {
        ReactionOptions options = this.engine.availableReactions(playerId.toString());
        if (options != null) {
            this.plugin.messages().actionBar(player, "table.reaction_available");
            player.sendMessage(this.reactionPrompt(player, options));
            return;
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
            this.plugin.messages().actionBar(player, "table.turn_prompt", this.plugin.messages().tag("suffix", suffix));
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
        this.openSettlementForViewers();
        if (!this.engine.getGameFinished()) {
            this.scheduleNextRoundCountdown();
        } else {
            this.cancelNextRoundCountdown();
        }
    }

    private void openSettlementForViewers() {
        for (Player player : this.viewers()) {
            SettlementUi.open(player, this);
            if (!this.engine.getGameFinished()) {
                this.plugin.messages().send(
                    player,
                    "table.next_round_countdown",
                    this.plugin.messages().number(this.plugin.messages().resolveLocale(player), "seconds", NEXT_ROUND_DELAY_MILLIS / 1000L)
                );
            } else {
                this.plugin.messages().send(player, "table.match_finished");
            }
        }
    }

    private void syncSeatFeedbackStates() {
        for (UUID playerId : this.seats) {
            if (this.isBot(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            this.syncSeatFeedbackState(player, playerId);
        }
    }

    private void syncSeatFeedbackState(Player player, UUID playerId) {
        String signature = this.feedbackSignature(playerId);
        String previous = this.feedbackState.put(playerId, signature);
        if (Objects.equals(signature, previous)) {
            return;
        }
        if (signature.isBlank()) {
            player.sendActionBar(Component.empty());
            return;
        }
        this.sendFeedback(player, playerId);
    }

    private Component reactionPrompt(Player player, ReactionOptions options) {
        Locale locale = this.plugin.messages().resolveLocale(player);
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

    private StringBuilder baseStateSummary(Locale locale) {
        StringBuilder builder = new StringBuilder(96);
        builder.append(this.plugin.messages().plain(locale, "state.label.round")).append(' ').append(this.roundDisplay(locale));
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.turn")).append(' ').append(this.engine.getCurrentPlayer().getDisplayName());
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.wall")).append(' ').append(this.engine.getWall().size());
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.spectators")).append(' ').append(this.spectatorCount());
        return builder;
    }

    private void appendReactionStateSummary(StringBuilder builder, Locale locale, UUID playerId) {
        if (this.engine.getPendingReaction() == null) {
            return;
        }
        ReactionOptions options = this.engine.availableReactions(playerId.toString());
        if (options == null) {
            return;
        }
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.reactions"));
        this.appendReactionActionLabels(builder, locale, options);
    }

    private void appendResolutionStateSummary(StringBuilder builder, Locale locale) {
        if (this.engine.getLastResolution() == null) {
            return;
        }
        builder.append(" | ")
            .append(this.plugin.messages().plain(locale, "state.label.resolution"))
            .append(' ')
            .append(this.resolutionLabel(locale, this.engine.getLastResolution().getTitle()));
    }

    private void appendNextRoundStateSummary(StringBuilder builder, Locale locale) {
        if (this.nextRoundDeadlineMillis <= 0L || this.engine.getGameFinished()) {
            return;
        }
        builder.append(" | ")
            .append(this.plugin.messages().plain(locale, "state.label.next_round"))
            .append(' ')
            .append(this.plugin.messages().plain(locale, "state.next_round_in", this.plugin.messages().number(locale, "seconds", this.nextRoundSecondsRemaining())));
    }

    private void appendFinishedStateSummary(StringBuilder builder, Locale locale) {
        if (!this.engine.getGameFinished()) {
            return;
        }
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.match_finished"));
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

    private Component waitingViewerOverlay(Locale locale) {
        return this.plugin.messages().render(
            locale,
            "overlay.waiting",
            this.plugin.messages().tag("table_id", this.id),
            this.plugin.messages().tag("summary", this.waitingDisplaySummary(locale))
        );
    }

    private Component finishedViewerOverlay(Locale locale) {
        return this.plugin.messages().render(
            locale,
            "overlay.finished",
            this.plugin.messages().tag("round", this.roundDisplay(locale)),
            this.plugin.messages().tag("title", this.resolutionLabel(locale, this.engine.getLastResolution().getTitle()))
        );
    }

    private Component nextRoundViewerOverlay(Locale locale) {
        return this.plugin.messages().render(
            locale,
            "overlay.next_round",
            this.plugin.messages().tag("round", this.roundDisplay(locale)),
            this.plugin.messages().number(locale, "seconds", this.nextRoundSecondsRemaining())
        );
    }

    private Component activeViewerOverlay(Locale locale, Player viewer) {
        return this.plugin.messages().render(
            locale,
            "overlay.active",
            this.plugin.messages().tag("role", this.viewerRoleLabel(locale, viewer.getUniqueId())),
            this.plugin.messages().tag("round", this.roundDisplay(locale)),
            this.plugin.messages().tag("turn", this.engine.getCurrentPlayer().getDisplayName()),
            this.plugin.messages().number(locale, "wall", this.engine.getWall().size()),
            this.plugin.messages().tag("last_discard", this.lastDiscardSummary(locale)),
            this.plugin.messages().tag("prompt", this.viewerPrompt(locale, viewer))
        );
    }

    private String viewerRoleLabel(Locale locale, UUID viewerId) {
        return this.plugin.messages().plain(locale, this.isSpectator(viewerId) ? "hud.role_spectator" : "hud.role_player");
    }

    private String viewerPrompt(Locale locale, Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        ReactionOptions options = this.engine.availableReactions(viewerId.toString());
        if (options != null) {
            return this.reactionSummary(locale, options);
        }
        if (this.engine.getCurrentPlayer().getUuid().equals(viewerId.toString())) {
            return this.plugin.messages().plain(locale, "overlay.your_turn");
        }
        if (this.isSpectator(viewerId)) {
            return this.plugin.messages().plain(locale, "overlay.spectating_turn") + " " + this.engine.getCurrentPlayer().getDisplayName();
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

    private void updateViewerOverlayRegions() {
        List<Player> viewers = this.viewers();
        Set<String> activeKeys = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            String regionKey = "viewer-overlay:" + viewer.getUniqueId();
            activeKeys.add(regionKey);
            this.updateRegion(regionKey, this.viewerOverlayFingerprint(viewer), () -> this.renderer.renderViewerOverlay(this, viewer));
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

    private Component hudTitle(Locale locale, UUID viewerId) {
        if (this.engine == null) {
            return this.plugin.messages().render(
                locale,
                "hud.waiting",
                this.plugin.messages().tag("table_id", this.id),
                this.plugin.messages().tag("summary", this.waitingDisplaySummary(locale))
            );
        }
        if (this.engine.getGameFinished() && this.engine.getLastResolution() != null) {
            return this.plugin.messages().render(
                locale,
                "hud.finished",
                this.plugin.messages().tag("round", this.roundDisplay(locale)),
                this.plugin.messages().tag("title", this.resolutionLabel(locale, this.engine.getLastResolution().getTitle()))
            );
        }
        if (!this.engine.getStarted() && this.nextRoundDeadlineMillis > 0L) {
            return this.plugin.messages().render(
                locale,
                "hud.next_round",
                this.plugin.messages().tag("round", this.roundDisplay(locale)),
                this.plugin.messages().number(locale, "seconds", this.nextRoundSecondsRemaining())
            );
        }
        String role = this.isSpectator(viewerId)
            ? this.plugin.messages().plain(locale, "hud.role_spectator")
            : this.plugin.messages().plain(locale, "hud.role_player");
        return this.plugin.messages().render(
            locale,
            "hud.round",
            this.plugin.messages().tag("round", this.roundDisplay(locale)),
            this.plugin.messages().tag("turn", this.engine.getCurrentPlayer().getDisplayName()),
            this.plugin.messages().number(locale, "wall", this.engine.getWall().size()),
            this.plugin.messages().tag("role", role)
        );
    }

    private float hudProgress() {
        if (this.engine == null) {
            return Math.min(1.0F, this.seats.size() / 4.0F);
        }
        if (!this.engine.getStarted() && this.nextRoundDeadlineMillis > 0L) {
            return Math.max(0.0F, Math.min(1.0F, this.nextRoundSecondsRemaining() / (float) (NEXT_ROUND_DELAY_MILLIS / 1000L)));
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
        if (!this.engine.getStarted() && this.nextRoundDeadlineMillis > 0L) {
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
        String state = this.hudStateSignature(locale, viewerId);
        BossBar bar = this.viewerHudBars.get(viewerId);
        if (bar == null) {
            bar = this.createHudBar(viewerId, viewer);
        }
        if (Objects.equals(this.viewerHudState.get(viewerId), state)) {
            return;
        }
        bar.name(this.hudTitle(locale, viewerId));
        bar.progress(this.hudProgress());
        bar.color(this.hudColor(viewerId));
        this.viewerHudState.put(viewerId, state);
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

    private String hudStateSignature(Locale locale, UUID viewerId) {
        FingerprintBuilder builder = fingerprintBuilder(160)
            .field(locale.toLanguageTag())
            .field(this.hudProgress())
            .field(this.hudColor(viewerId))
            .field(this.nextRoundSecondsRemaining())
            .field(this.isSpectator(viewerId))
            .field(this.isStarted())
            .field(this.engine == null ? null : this.engine.getLastResolution());
        if (this.engine == null) {
            return builder
                .field(this.waitingDisplaySummary())
                .field(this.ruleDisplaySummary())
                .toString();
        }
        return this.appendActiveRoundFingerprint(builder).toString();
    }

    private String viewerOverlayFingerprint(Player viewer) {
        Locale locale = this.plugin.messages().resolveLocale(viewer);
        FingerprintBuilder builder = fingerprintBuilder(224)
            .field(locale.toLanguageTag())
            .field(viewer.getUniqueId())
            .field(this.isSpectator(viewer.getUniqueId()))
            .field(this.nextRoundSecondsRemaining())
            .field(this.engine == null ? null : this.engine.getLastResolution())
            .field(this.waitingDisplaySummary())
            .field(this.ruleDisplaySummary());
        if (this.engine == null) {
            return builder.field("no-engine").toString();
        }
        return this.appendActiveRoundFingerprint(builder)
            .field(this.engine.availableReactions(viewer.getUniqueId().toString()))
            .field(this.spectatorSeatFingerprint())
            .toString();
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

    private String spectatorSeatFingerprint() {
        FingerprintBuilder builder = fingerprintBuilder(256);
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            builder.field(wind.name()).field(Objects.toString(playerId, "empty"));
            if (playerId != null) {
                builder.field(this.displayName(playerId))
                    .field(this.points(playerId))
                    .field(this.hand(playerId).size())
                    .field(this.discards(playerId).size())
                    .field(this.fuuro(playerId).size())
                    .field(this.isRiichi(playerId))
                    .field(this.currentSeat() == wind);
            }
            builder.entrySeparator();
        }
        return builder.toString();
    }

    private String riichiFingerprint() {
        FingerprintBuilder builder = fingerprintBuilder(64);
        for (UUID playerId : this.seats) {
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
        this.updateHandPrivateRegions(wind);
        this.updateRegion(this.seatRegionKey("discards", wind), this.discardFingerprint(wind), () -> this.renderer.renderDiscards(this, wind));
        this.updateRegion(this.seatRegionKey("melds", wind), this.meldFingerprint(wind), () -> this.renderer.renderMelds(this, wind));
    }

    private void updateHandPrivateRegions(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        int handSize = playerId == null ? 0 : this.hand(playerId).size();
        for (int tileIndex = 0; tileIndex < MAX_HAND_TILE_REGIONS; tileIndex++) {
            String regionKey = this.handPrivateRegionKey(wind, tileIndex);
            if (tileIndex >= handSize) {
                this.clearRegion(regionKey);
                continue;
            }
            int capturedTileIndex = tileIndex;
            this.updateRegion(
                regionKey,
                this.handPrivateTileFingerprint(wind, capturedTileIndex),
                () -> this.renderer.renderHandPrivateTile(this, wind, capturedTileIndex)
            );
        }
    }

    private void updateRegion(String regionKey, String fingerprint, RegionRenderer renderer) {
        String previousFingerprint = this.regionFingerprints.get(regionKey);
        List<Entity> currentEntities = this.regionDisplays.get(regionKey);
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

    private String handPrivateTileFingerprint(SeatWind wind, int tileIndex) {
        UUID playerId = this.playerAt(wind);
        FingerprintBuilder builder = this.seatFingerprintBuilder("hand-private", wind, playerId, 128)
            .field(tileIndex);
        if (playerId == null) {
            return builder.toString();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand = this.hand(playerId);
        if (tileIndex < 0 || tileIndex >= hand.size()) {
            return builder.field("empty").toString();
        }
        return builder.field(this.onlinePlayer(playerId) != null)
            .field(this.stickLayoutCount(wind))
            .field(tileIndex == this.selectedHandTileIndex(playerId))
            .field(hand.get(tileIndex).name())
            .toString();
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
        String riichi = this.isRiichi(playerId) ? this.plugin.messages().plain(locale, "overlay.status_riichi") : "";
        return this.plugin.messages().plain(
            locale,
            "table.public.seat_status",
            this.plugin.messages().tag("seat", this.seatDisplayName(wind, locale)),
            this.plugin.messages().number(locale, "points", this.points(playerId)),
            this.plugin.messages().tag("status", riichi.isBlank() ? "" : " | " + riichi)
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
}
