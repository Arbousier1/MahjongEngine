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
import doublemoon.mahjongcraft.paper.riichi.RiichiPlayerState;
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongSoulScoring;
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
    private static final String REGION_WALL = "wall";
    private static final String REGION_DORA = "dora";
    private static final String REGION_CENTER = "center";
    private static final long NEXT_ROUND_DELAY_MILLIS = 8000L;

    private final MahjongPaperPlugin plugin;
    private final String id;
    private final Location center;
    private final List<UUID> seats = new ArrayList<>(4);
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<String, List<Entity>> regionDisplays = new LinkedHashMap<>();
    private final Map<String, String> regionFingerprints = new HashMap<>();
    private final TableRenderer renderer = new TableRenderer();
    private final Map<UUID, String> botNames = new LinkedHashMap<>();
    private final Map<UUID, String> feedbackState = new HashMap<>();
    private final Map<UUID, BossBar> viewerHudBars = new HashMap<>();
    private final Map<UUID, String> viewerHudState = new HashMap<>();
    private MahjongRule configuredRule = majsoulRule(MahjongRule.GameLength.TWO_WIND);
    private RiichiRoundEngine engine;
    private String lastSettlementFingerprint = "";
    private String lastPersistedSettlementFingerprint = "";
    private String lastTurnSoundFingerprint = "";
    private String lastRiichiSoundFingerprint = "";
    private String lastResolutionSoundFingerprint = "";
    private long nextRoundDeadlineMillis;
    private int nextBotNumber = 1;
    private BukkitTask botTask;

    public MahjongTableSession(MahjongPaperPlugin plugin, String id, Location center, Player owner) {
        this.plugin = plugin;
        this.id = id;
        this.center = center.clone();
        this.addPlayer(owner);
    }

    public MahjongPaperPlugin plugin() {
        return this.plugin;
    }

    public String id() {
        return this.id;
    }

    public Location center() {
        return this.center.clone();
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
        return this.seats.getFirst();
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
        boolean result = this.engine.discard(playerId.toString(), tileIndex);
        this.render();
        return result;
    }

    public boolean declareRiichi(UUID playerId, int tileIndex) {
        if (this.engine == null) {
            return false;
        }
        boolean result = this.engine.declareRiichi(playerId.toString(), tileIndex);
        this.render();
        return result;
    }

    public boolean declareTsumo(UUID playerId) {
        if (this.engine == null) {
            return false;
        }
        boolean result = this.engine.tryTsumo(playerId.toString());
        this.render();
        return result;
    }

    public boolean declareKyuushuKyuuhai(UUID playerId) {
        if (this.engine == null) {
            return false;
        }
        boolean result = this.engine.declareKyuushuKyuuhai(playerId.toString());
        this.render();
        return result;
    }

    public boolean react(UUID playerId, ReactionResponse response) {
        if (this.engine == null) {
            return false;
        }
        boolean result = this.engine.react(playerId.toString(), response);
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
            this.render();
            return result;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public void render() {
        this.cancelBotTask();
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
            if (!entity.isDead()) {
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
            List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(fuuro.getTileInstances().size());
            List<Boolean> faceDownFlags = new ArrayList<>(fuuro.getTileInstances().size());
            boolean concealedKan = fuuro.isKan() && !fuuro.isOpen();
            for (int i = 0; i < fuuro.getTileInstances().size(); i++) {
                tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(fuuro.getTileInstances().get(i).getMahjongTile().name()));
                faceDownFlags.add(concealedKan && (i == 0 || i == fuuro.getTileInstances().size() - 1));
            }
            melds.add(new MeldView(List.copyOf(tiles), List.copyOf(faceDownFlags)));
        });
        return List.copyOf(melds);
    }

    public Player onlinePlayer(UUID playerId) {
        return Bukkit.getPlayer(playerId);
    }

    public boolean isSpectator(UUID playerId) {
        return this.spectators.contains(playerId);
    }

    public List<Player> viewers() {
        List<Player> viewers = new ArrayList<>(this.seats.size() + this.spectators.size());
        for (UUID playerId : this.seats) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                viewers.add(player);
            }
        }
        for (UUID spectatorId : this.spectators) {
            Player player = Bukkit.getPlayer(spectatorId);
            if (player != null) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    public List<UUID> viewerIdsExcluding(UUID excludedPlayerId) {
        List<UUID> viewerIds = new ArrayList<>(this.seats.size() + this.spectators.size());
        for (Player viewer : this.viewers()) {
            if (!viewer.getUniqueId().equals(excludedPlayerId)) {
                viewerIds.add(viewer.getUniqueId());
            }
        }
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
        StringBuilder builder = new StringBuilder(96);
        builder.append(this.plugin.messages().plain(locale, "state.label.round")).append(' ').append(this.roundDisplay(locale));
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.turn")).append(' ').append(this.engine.getCurrentPlayer().getDisplayName());
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.wall")).append(' ').append(this.engine.getWall().size());
        builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.spectators")).append(' ').append(this.spectatorCount());

        if (this.engine.getPendingReaction() != null) {
            ReactionOptions options = this.engine.availableReactions(player.getUniqueId().toString());
            if (options != null) {
                builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.reactions"));
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
        }

        if (this.engine.getLastResolution() != null) {
            builder.append(" | ").append(this.plugin.messages().plain(locale, "state.label.resolution")).append(' ').append(this.resolutionLabel(locale, this.engine.getLastResolution().getTitle()));
        }
        if (this.nextRoundDeadlineMillis > 0L && !this.engine.getGameFinished()) {
            builder.append(" | ")
                .append(this.plugin.messages().plain(locale, "state.label.next_round"))
                .append(' ')
                .append(this.plugin.messages().plain(locale, "state.next_round_in", this.plugin.messages().number(locale, "seconds", this.nextRoundSecondsRemaining())));
        }
        if (this.engine.getGameFinished()) {
            builder.append(" | ").append(this.plugin.messages().plain(locale, "state.match_finished"));
        }

        return this.plugin.messages().render(player, "command.rule_summary", this.plugin.messages().tag("summary", builder.toString()));
    }

    public Component viewerOverlay(Player viewer) {
        Locale locale = this.plugin.messages().resolveLocale(viewer);
        if (this.engine == null) {
            return this.plugin.messages().render(
                locale,
                "overlay.waiting",
                this.plugin.messages().tag("table_id", this.id),
                this.plugin.messages().tag("summary", this.waitingDisplaySummary(locale))
            );
        }
        if (!this.engine.getStarted()) {
            if (this.engine.getGameFinished() && this.engine.getLastResolution() != null) {
                return this.plugin.messages().render(
                    locale,
                    "overlay.finished",
                    this.plugin.messages().tag("round", this.roundDisplay(locale)),
                    this.plugin.messages().tag("title", this.resolutionLabel(locale, this.engine.getLastResolution().getTitle()))
                );
            }
            return this.plugin.messages().render(
                locale,
                "overlay.next_round",
                this.plugin.messages().tag("round", this.roundDisplay(locale)),
                this.plugin.messages().number(locale, "seconds", this.nextRoundSecondsRemaining())
            );
        }

        String role = this.plugin.messages().plain(locale, this.isSpectator(viewer.getUniqueId()) ? "hud.role_spectator" : "hud.role_player");
        String prompt = "";
        ReactionOptions options = this.engine.availableReactions(viewer.getUniqueId().toString());
        if (options != null) {
            prompt = this.reactionSummary(locale, options);
        } else if (this.engine.getCurrentPlayer().getUuid().equals(viewer.getUniqueId().toString())) {
            prompt = this.plugin.messages().plain(locale, "overlay.your_turn");
        } else if (this.isSpectator(viewer.getUniqueId())) {
            prompt = this.plugin.messages().plain(locale, "overlay.spectating_turn") + " " + this.engine.getCurrentPlayer().getDisplayName();
        }

        return this.plugin.messages().render(
            locale,
            "overlay.active",
            this.plugin.messages().tag("role", role),
            this.plugin.messages().tag("round", this.roundDisplay(locale)),
            this.plugin.messages().tag("turn", this.engine.getCurrentPlayer().getDisplayName()),
            this.plugin.messages().number(locale, "wall", this.engine.getWall().size()),
            this.plugin.messages().tag("prompt", prompt)
        );
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

        String status = "";
        if (this.currentSeat() == wind && this.isStarted()) {
            status = this.plugin.messages().plain(locale, "overlay.status_turn");
        }
        if (this.isRiichi(playerId)) {
            String riichi = this.plugin.messages().plain(locale, "overlay.status_riichi");
            status = status.isBlank() ? riichi : status + " / " + riichi;
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
            this.plugin.messages().tag("status", status)
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
        return true;
    }

    private MahjongRule copyRule() {
        return new MahjongRule(
            this.configuredRule.getLength(),
            this.configuredRule.getThinkingTime(),
            this.configuredRule.getStartingPoints(),
            this.configuredRule.getMinPointsToWin(),
            this.configuredRule.getMinimumHan(),
            this.configuredRule.getSpectate(),
            this.configuredRule.getRedFive(),
            this.configuredRule.getOpenTanyao(),
            this.configuredRule.getLocalYaku()
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
            this.feedbackState.clear();
            this.lastSettlementFingerprint = "";
            this.lastPersistedSettlementFingerprint = "";
            this.cancelNextRoundCountdown();
            return;
        }

        String settlementFingerprint = Objects.toString(this.engine.getLastResolution(), "");
        if (!settlementFingerprint.isBlank() && !settlementFingerprint.equals(this.lastPersistedSettlementFingerprint) && this.plugin.database() != null) {
            this.plugin.database().persistRoundResultAsync(this, this.engine.getLastResolution());
            this.lastPersistedSettlementFingerprint = settlementFingerprint;
        }
        if (!settlementFingerprint.isBlank() && !settlementFingerprint.equals(this.lastSettlementFingerprint)) {
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
            if (!this.engine.getGameFinished()) {
                this.scheduleNextRoundCountdown();
            } else {
                this.cancelNextRoundCountdown();
            }
        }
        this.lastSettlementFingerprint = settlementFingerprint;

        for (UUID playerId : this.seats) {
            if (this.isBot(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            String signature = this.feedbackSignature(playerId);
            String previous = this.feedbackState.put(playerId, signature);
            if (Objects.equals(signature, previous)) {
                continue;
            }
            if (signature.isBlank()) {
                player.sendActionBar(Component.empty());
                continue;
            }
            this.sendFeedback(player, playerId);
        }
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

    private void updateViewerOverlayRegions() {
        Set<String> activeKeys = new LinkedHashSet<>();
        for (Player viewer : this.viewers()) {
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
        Set<UUID> onlineViewerIds = new LinkedHashSet<>();
        for (Player viewer : this.viewers()) {
            UUID viewerId = viewer.getUniqueId();
            onlineViewerIds.add(viewerId);
            Locale locale = this.plugin.messages().resolveLocale(viewer);
            String state = this.hudStateSignature(locale, viewerId);
            BossBar bar = this.viewerHudBars.get(viewerId);
            if (bar == null) {
                bar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                this.viewerHudBars.put(viewerId, bar);
                viewer.showBossBar(bar);
            }
            if (!Objects.equals(this.viewerHudState.get(viewerId), state)) {
                bar.name(this.hudTitle(locale, viewerId));
                bar.progress(this.hudProgress());
                bar.color(this.hudColor(viewerId));
                this.viewerHudState.put(viewerId, state);
            }
        }

        for (UUID viewerId : List.copyOf(this.viewerHudBars.keySet())) {
            if (!onlineViewerIds.contains(viewerId)) {
                this.hideHud(viewerId);
            }
        }
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

    private String hudStateSignature(Locale locale, UUID viewerId) {
        return locale.toLanguageTag()
            + ':' + this.hudProgress()
            + ':' + this.hudColor(viewerId)
            + ':' + this.nextRoundSecondsRemaining()
            + ':' + this.isSpectator(viewerId)
            + ':' + this.isStarted()
            + ':' + Objects.toString(this.engine == null ? null : this.engine.getLastResolution(), "")
            + ':' + (this.engine == null ? this.waitingDisplaySummary() + ':' + this.ruleDisplaySummary() : this.roundDisplay() + ':' + this.engine.getWall().size() + ':' + this.engine.getCurrentPlayerIndex());
    }

    private String viewerOverlayFingerprint(Player viewer) {
        Locale locale = this.plugin.messages().resolveLocale(viewer);
        return locale.toLanguageTag()
            + ':' + viewer.getUniqueId()
            + ':' + this.isSpectator(viewer.getUniqueId())
            + ':' + this.nextRoundSecondsRemaining()
            + ':' + Objects.toString(this.engine == null ? null : this.engine.getLastResolution(), "")
            + ':' + this.waitingDisplaySummary()
            + ':' + this.ruleDisplaySummary()
            + ':' + (this.engine == null ? "no-engine" : this.roundDisplay() + ':' + this.engine.getWall().size() + ':' + this.engine.getCurrentPlayerIndex()
                + ':' + Objects.toString(this.engine.availableReactions(viewer.getUniqueId().toString()), "") + ':' + this.spectatorSeatFingerprint());
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
            this.lastTurnSoundFingerprint = "";
            this.lastRiichiSoundFingerprint = "";
            this.lastResolutionSoundFingerprint = "";
            return;
        }

        String turnFingerprint = this.engine.getStarted()
            ? this.engine.getCurrentPlayerIndex() + ":" + this.engine.getWall().size() + ":" + Objects.toString(this.engine.getPendingReaction(), "")
            : "";
        if (!turnFingerprint.isBlank() && !turnFingerprint.equals(this.lastTurnSoundFingerprint)) {
            this.broadcastSound(Sound.UI_BUTTON_CLICK, 0.5F, 1.6F);
        }
        this.lastTurnSoundFingerprint = turnFingerprint;

        String riichiFingerprint = this.riichiFingerprint();
        if (!riichiFingerprint.equals(this.lastRiichiSoundFingerprint) && !this.lastRiichiSoundFingerprint.isBlank()) {
            this.broadcastSound(Sound.BLOCK_BELL_USE, 0.8F, 1.25F);
        }
        this.lastRiichiSoundFingerprint = riichiFingerprint;

        String resolutionFingerprint = Objects.toString(this.engine.getLastResolution(), "");
        if (!resolutionFingerprint.isBlank() && !resolutionFingerprint.equals(this.lastResolutionSoundFingerprint)) {
            Sound sound = this.engine.getLastResolution().getDraw() == null ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_BELL;
            this.broadcastSound(sound, 0.9F, 1.0F);
        }
        this.lastResolutionSoundFingerprint = resolutionFingerprint;
    }

    private void broadcastSound(Sound sound, float volume, float pitch) {
        for (Player viewer : this.viewers()) {
            viewer.playSound(viewer.getLocation(), sound, volume, pitch);
        }
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
        for (Player viewer : this.viewers()) {
            if (!viewer.getUniqueId().equals(excludedPlayerId)) {
                builder.append(viewer.getUniqueId()).append(';');
            }
        }
        return builder.toString();
    }

    private String spectatorSeatFingerprint() {
        StringBuilder builder = new StringBuilder(256);
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            builder.append(wind.name()).append(':').append(Objects.toString(playerId, "empty")).append(':');
            if (playerId != null) {
                builder.append(this.displayName(playerId)).append(':')
                    .append(this.points(playerId)).append(':')
                    .append(this.hand(playerId).size()).append(':')
                    .append(this.discards(playerId).size()).append(':')
                    .append(this.fuuro(playerId).size()).append(':')
                    .append(this.isRiichi(playerId)).append(':')
                    .append(this.currentSeat() == wind).append(';');
            }
        }
        return builder.toString();
    }

    private String riichiFingerprint() {
        StringBuilder builder = new StringBuilder(64);
        for (UUID playerId : this.seats) {
            builder.append(playerId).append(':').append(this.isRiichi(playerId)).append(';');
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
        this.updateRegion(REGION_WALL, this.wallFingerprint(), () -> this.renderer.renderWall(this));
        this.updateRegion(REGION_DORA, this.doraFingerprint(), () -> this.renderer.renderDora(this));
        this.updateRegion(REGION_CENTER, this.centerFingerprint(), () -> this.renderer.renderCenterLabel(this));
    }

    private void updateSeatRegion(SeatWind wind) {
        this.updateRegion(this.seatRegionKey("labels", wind), this.seatLabelFingerprint(wind), () -> this.renderer.renderSeatLabels(this, wind));
        this.updateRegion(this.seatRegionKey("hand", wind), this.handFingerprint(wind), () -> this.renderer.renderHand(this, wind));
        this.updateRegion(this.seatRegionKey("discards", wind), this.discardFingerprint(wind), () -> this.renderer.renderDiscards(this, wind));
        this.updateRegion(this.seatRegionKey("melds", wind), this.meldFingerprint(wind), () -> this.renderer.renderMelds(this, wind));
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

    private String seatRegionKey(String region, SeatWind wind) {
        return region + ":" + wind.name();
    }

    private String wallFingerprint() {
        if (this.engine == null) {
            return "waiting";
        }
        return "wall:" + this.engine.getStarted()
            + ':' + this.engine.getGameFinished()
            + ':' + this.engine.getWall().size();
    }

    private String doraFingerprint() {
        StringBuilder builder = new StringBuilder(64);
        if (this.engine == null) {
            return "dora:waiting";
        }
        builder.append("dora:").append(this.engine.getStarted()).append(':').append(this.engine.getDoraIndicators().size()).append(':');
        this.engine.getDoraIndicators().forEach(tile -> builder.append(tile.getMahjongTile().name()).append(','));
        return builder.toString();
    }

    private String centerFingerprint() {
        return this.isStarted()
            ? "center:started:" + this.roundDisplay() + ':' + this.remainingWall().size() + ':' + this.dicePoints() + ':' + this.dealerName() + ':' + this.currentSeat().name()
            : "center:waiting:" + this.waitingDisplaySummary() + ':' + this.ruleDisplaySummary();
    }

    private String seatLabelFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        return "labels:" + wind.name()
            + ':' + this.currentSeat().name()
            + ':' + Objects.toString(playerId, "empty")
            + ':' + this.displayName(playerId)
            + ':' + this.points(playerId)
            + ':' + this.isRiichi(playerId);
    }

    private String handFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        StringBuilder builder = new StringBuilder(256);
        builder.append("hand:").append(wind.name()).append(':').append(Objects.toString(playerId, "empty"));
        if (playerId == null) {
            return builder.toString();
        }
        builder.append(':').append(this.onlinePlayer(playerId) != null);
        builder.append(':').append(this.viewerMembershipSignature(playerId));
        this.hand(playerId).forEach(tile -> builder.append(':').append(tile.name()));
        return builder.toString();
    }

    private String discardFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        StringBuilder builder = new StringBuilder(256);
        builder.append("discards:").append(wind.name()).append(':').append(Objects.toString(playerId, "empty"));
        if (playerId == null) {
            return builder.toString();
        }
        this.discards(playerId).forEach(tile -> builder.append(':').append(tile.name()));
        return builder.toString();
    }

    private String meldFingerprint(SeatWind wind) {
        UUID playerId = this.playerAt(wind);
        StringBuilder builder = new StringBuilder(256);
        builder.append("melds:").append(wind.name()).append(':').append(Objects.toString(playerId, "empty"));
        if (playerId == null) {
            return builder.toString();
        }
        this.fuuro(playerId).forEach(meld -> {
            builder.append('[');
            for (int i = 0; i < meld.tiles().size(); i++) {
                builder.append(meld.tiles().get(i).name()).append('/').append(meld.faceDownAt(i)).append(',');
            }
            builder.append(']');
        });
        return builder.toString();
    }

    @FunctionalInterface
    private interface RegionRenderer {
        List<Entity> render();
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
                this.plugin.messages().tag("dealer", this.dealerName(locale))
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
