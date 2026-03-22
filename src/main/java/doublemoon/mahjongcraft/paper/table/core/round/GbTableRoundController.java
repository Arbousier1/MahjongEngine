package doublemoon.mahjongcraft.paper.table.core.round;

import doublemoon.mahjongcraft.paper.gb.jni.GbFanEntry;
import doublemoon.mahjongcraft.paper.gb.jni.GbFanRequest;
import doublemoon.mahjongcraft.paper.gb.jni.GbFanResponse;
import doublemoon.mahjongcraft.paper.gb.jni.GbMeldInput;
import doublemoon.mahjongcraft.paper.gb.jni.GbScoreDelta;
import doublemoon.mahjongcraft.paper.gb.jni.GbSeatPointsInput;
import doublemoon.mahjongcraft.paper.gb.jni.GbTingCandidate;
import doublemoon.mahjongcraft.paper.gb.jni.GbTingRequest;
import doublemoon.mahjongcraft.paper.gb.jni.GbTingResponse;
import doublemoon.mahjongcraft.paper.gb.jni.GbWinRequest;
import doublemoon.mahjongcraft.paper.gb.jni.GbWinResponse;
import doublemoon.mahjongcraft.paper.gb.runtime.GbNativeRulesGateway;
import doublemoon.mahjongcraft.paper.gb.runtime.GbTileEncoding;
import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.scene.MeldView;
import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.ReactionType;
import doublemoon.mahjongcraft.paper.riichi.RoundResolution;
import doublemoon.mahjongcraft.paper.riichi.model.ExhaustiveDraw;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRound;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule;
import doublemoon.mahjongcraft.paper.riichi.model.ScoreItem;
import doublemoon.mahjongcraft.paper.riichi.model.ScoreSettlement;
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick;
import doublemoon.mahjongcraft.paper.riichi.model.Wind;
import doublemoon.mahjongcraft.paper.riichi.model.YakuSettlement;
import doublemoon.mahjongcraft.paper.table.core.MahjongVariant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import kotlin.Pair;

public final class GbTableRoundController implements TableRoundController {
    private static final int MIN_GB_FAN = 8;

    private final MahjongRule rule;
    private final GbNativeRulesGateway nativeGateway;
    private final EnumMap<SeatWind, UUID> seats;
    private final Map<UUID, String> displayNames;
    private final IntSupplier dicePointsSupplier;
    private final Supplier<List<MahjongTile>> wallSupplier;
    private final MahjongRound round;
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, List<MahjongTile>> hands = new HashMap<>();
    private final Map<UUID, List<MahjongTile>> discards = new HashMap<>();
    private final Map<UUID, List<MahjongTile>> flowers = new HashMap<>();
    private final Map<UUID, Boolean> hasDrawnTile = new HashMap<>();
    private final Map<UUID, List<GbMeldState>> melds = new HashMap<>();
    private final Map<UUID, GbTingResponse> tingCache = new HashMap<>();
    private List<MahjongTile> wall = List.of();
    private boolean started;
    private boolean gameFinished;
    private int dicePoints;
    private int currentPlayerIndex;
    private int kanCount;
    private RoundResolution lastResolution;
    private PendingReactionWindow pendingReactionWindow;
    private OpeningDiceRoll pendingDiceRoll;
    private UUID afterKanTsumoPlayer;

    public GbTableRoundController(MahjongRule rule, EnumMap<SeatWind, UUID> seats, Map<UUID, String> displayNames, GbNativeRulesGateway nativeGateway) {
        this(rule, seats, displayNames, nativeGateway, GbRoundSupport::rollDicePoints, GbRoundSupport::buildWall);
    }

    public GbTableRoundController(
        MahjongRule rule,
        EnumMap<SeatWind, UUID> seats,
        Map<UUID, String> displayNames,
        GbNativeRulesGateway nativeGateway,
        IntSupplier dicePointsSupplier,
        Supplier<List<MahjongTile>> wallSupplier
    ) {
        this.rule = rule;
        this.nativeGateway = nativeGateway;
        this.seats = new EnumMap<>(seats);
        this.displayNames = new HashMap<>(displayNames);
        this.dicePointsSupplier = dicePointsSupplier;
        this.wallSupplier = wallSupplier;
        this.round = rule.getLength().getStartingRound();
        for (UUID playerId : seats.values()) {
            if (playerId == null) {
                continue;
            }
            this.points.put(playerId, rule.getStartingPoints());
            this.hands.put(playerId, new ArrayList<>());
            this.discards.put(playerId, new ArrayList<>());
            this.flowers.put(playerId, new ArrayList<>());
            this.hasDrawnTile.put(playerId, false);
            this.melds.put(playerId, new ArrayList<>());
            this.tingCache.put(playerId, new GbTingResponse(false, List.of(), "Round has not started yet."));
        }
    }

    @Override
    public MahjongVariant variant() {
        return MahjongVariant.GB;
    }

    @Override
    public MahjongRule rule() {
        return this.rule;
    }

    @Override
    public boolean started() {
        return this.started;
    }

    @Override
    public boolean gameFinished() {
        return this.gameFinished;
    }

    @Override
    public void startRound() {
        OpeningDiceRoll diceRoll = this.pendingDiceRoll;
        this.pendingDiceRoll = null;
        this.dicePoints = diceRoll == null
            ? GbRoundSupport.requireValidDicePoints(this.dicePointsSupplier.getAsInt())
            : diceRoll.total();
        this.wall = GbRoundSupport.reorderWallForDice(this.wallSupplier.get(), this.dicePoints, this.round.getRound());
        this.pendingReactionWindow = null;
        this.lastResolution = null;
        this.started = true;
        this.gameFinished = false;
        this.currentPlayerIndex = this.dealerSeat().index();
        this.kanCount = 0;
        this.afterKanTsumoPlayer = null;
        for (UUID playerId : this.seats.values()) {
            if (playerId == null) {
                continue;
            }
            this.hands.get(playerId).clear();
            this.discards.get(playerId).clear();
            this.flowers.get(playerId).clear();
            this.hasDrawnTile.put(playerId, false);
            this.melds.get(playerId).clear();
        }

        for (int draw = 0; draw < 13; draw++) {
            for (SeatWind wind : SeatWind.values()) {
                this.drawTile(this.playerAt(wind), false, false, false);
            }
        }
        this.drawTile(this.currentPlayerId(), false, false, true);
        this.refreshAllTing();
    }

    @Override
    public boolean discard(UUID playerId, int tileIndex) {
        if (!this.canSelectHandTile(playerId, tileIndex)) {
            return false;
        }
        MahjongTile discarded = this.hands.get(playerId).remove(tileIndex);
        this.hasDrawnTile.put(playerId, false);
        this.sortHand(playerId);
        this.discards.get(playerId).add(discarded);
        this.afterKanTsumoPlayer = null;
        this.pendingReactionWindow = this.buildPendingReactionWindow(playerId, discarded);
        if (this.pendingReactionWindow == null) {
            this.advanceAfterDiscard();
        }
        this.refreshAllTing();
        return true;
    }

    @Override
    public boolean declareTsumo(UUID playerId) {
        if (!this.isCurrentPlayer(playerId) || this.hasPendingReaction()) {
            return false;
        }
        MahjongTile winningTile = this.drawnTile(playerId);
        if (winningTile == null) {
            return false;
        }
        GbWinResponse response = this.nativeGateway.evaluateWin(
            this.buildWinRequest(playerId, null, winningTile, "SELF_DRAW", this.selfDrawFlags(playerId, winningTile))
        );
        if (!response.getValid() || response.getTotalFan() < MIN_GB_FAN) {
            return false;
        }
        this.finishWins(List.of(new ResolvedGbWin(playerId, null, winningTile, response)));
        return true;
    }

    @Override
    public boolean react(UUID playerId, ReactionResponse response) {
        if (playerId == null || response == null || this.pendingReactionWindow == null) {
            return false;
        }
        ReactionOptions options = this.pendingReactionWindow.options().get(playerId);
        if (options == null) {
            return false;
        }
        switch (response.getType()) {
            case RON -> {
                if (!options.getCanRon()) {
                    return false;
                }
            }
            case PON -> {
                if (!options.getCanPon()) {
                    return false;
                }
            }
            case MINKAN -> {
                if (!options.getCanMinkan()) {
                    return false;
                }
            }
            case CHII -> {
                if (response.getChiiPair() == null || !options.getChiiPairs().contains(response.getChiiPair())) {
                    return false;
                }
            }
            case SKIP -> {
            }
        }
        this.pendingReactionWindow.responses().put(playerId, response);
        if (!this.pendingReactionWindow.responses().keySet().containsAll(this.pendingReactionWindow.options().keySet())) {
            return true;
        }
        return this.resolvePendingReactions();
    }

    @Override
    public boolean declareKan(UUID playerId, String tileName) {
        if (playerId == null || tileName == null || tileName.isBlank() || !this.isCurrentPlayer(playerId) || this.hasPendingReaction()) {
            return false;
        }
        MahjongTile target;
        try {
            target = MahjongTile.valueOf(tileName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        List<MahjongTile> hand = this.hands.get(playerId);
        if (hand == null) {
            return false;
        }
        if (GbRoundSupport.countMatchingTiles(hand, target) >= 4) {
            GbRoundSupport.removeTiles(hand, target, 4);
            this.hasDrawnTile.put(playerId, false);
            this.sortHand(playerId);
            this.melds.get(playerId).add(GbMeldState.ankan(target));
            this.kanCount++;
            boolean drew = this.drawReplacementTileOrFinish(playerId);
            this.refreshAllTing();
            return drew;
        }
        for (int i = 0; i < this.melds.get(playerId).size(); i++) {
            GbMeldState meld = this.melds.get(playerId).get(i);
            if (meld.type() == GbMeldType.PUNG
                && GbRoundSupport.sameKind(meld.baseTile(), target)
                && GbRoundSupport.countMatchingTiles(hand, target) >= 1) {
                PendingReactionWindow robbingKongWindow = this.buildRobbingKongWindow(playerId, target, i);
                if (robbingKongWindow != null) {
                    this.pendingReactionWindow = robbingKongWindow;
                    this.refreshAllTing();
                    return true;
                }
                this.finishAddedKong(playerId, target, i);
                return true;
            }
        }
        return false;
    }

    @Override
    public UUID playerAt(SeatWind wind) {
        return wind == null ? null : this.seats.get(wind);
    }

    @Override
    public int points(UUID playerId) {
        return this.points.getOrDefault(playerId, 0);
    }

    @Override
    public boolean isRiichi(UUID playerId) {
        return false;
    }

    @Override
    public int dicePoints() {
        return this.dicePoints;
    }

    @Override
    public int kanCount() {
        return this.kanCount;
    }

    @Override
    public int roundIndex() {
        return this.round.getRound();
    }

    @Override
    public int honbaCount() {
        return this.round.getHonba();
    }

    @Override
    public SeatWind roundWind() {
        return this.roundWindSeat();
    }

    @Override
    public SeatWind dealerSeat() {
        return SeatWind.fromIndex(this.round.getRound());
    }

    @Override
    public SeatWind currentSeat() {
        return SeatWind.fromIndex(this.currentPlayerIndex);
    }

    @Override
    public String currentPlayerDisplayName() {
        UUID playerId = this.currentPlayerId();
        return playerId == null ? "" : this.displayNames.getOrDefault(playerId, playerId.toString());
    }

    @Override
    public List<MahjongTile> hand(UUID playerId) {
        return playerId == null ? List.of() : List.copyOf(this.hands.getOrDefault(playerId, List.of()));
    }

    @Override
    public List<MahjongTile> discards(UUID playerId) {
        return playerId == null ? List.of() : List.copyOf(this.discards.getOrDefault(playerId, List.of()));
    }

    @Override
    public List<MahjongTile> remainingWall() {
        List<MahjongTile> hiddenWall = new ArrayList<>(this.wall.size());
        for (int i = 0; i < this.wall.size(); i++) {
            hiddenWall.add(MahjongTile.UNKNOWN);
        }
        return List.copyOf(hiddenWall);
    }

    @Override
    public int remainingWallCount() {
        return this.wall.size();
    }

    @Override
    public List<MeldView> fuuro(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        List<GbMeldState> playerMelds = this.melds.get(playerId);
        List<MeldView> views = new ArrayList<>();
        views.addAll(this.flowerDisplayViews(playerId));
        if (playerMelds != null) {
            for (GbMeldState meld : playerMelds) {
                views.add(this.toMeldView(meld));
            }
        }
        return List.copyOf(views);
    }

    private MeldView toMeldView(GbMeldState meld) {
        List<Boolean> faceDownFlags = new ArrayList<>(meld.tiles().size());
        for (int i = 0; i < meld.tiles().size(); i++) {
            faceDownFlags.add(meld.type() == GbMeldType.CONCEALED_KONG && (i == 0 || i == meld.tiles().size() - 1));
        }
        return new MeldView(List.copyOf(meld.tiles()), List.copyOf(faceDownFlags), meld.claimTileIndex(), meld.claimYawOffset(), meld.addedKanTile());
    }

    private List<MeldView> flowerDisplayViews(UUID playerId) {
        List<MahjongTile> playerFlowers = this.flowers.getOrDefault(playerId, List.of());
        if (playerFlowers.isEmpty()) {
            return List.of();
        }
        List<MeldView> views = new ArrayList<>((playerFlowers.size() + 3) / 4);
        for (int start = 0; start < playerFlowers.size(); start += 4) {
            List<MahjongTile> chunk = List.copyOf(playerFlowers.subList(start, Math.min(start + 4, playerFlowers.size())));
            List<Boolean> faceDownFlags = new ArrayList<>(chunk.size());
            for (int i = 0; i < chunk.size(); i++) {
                faceDownFlags.add(false);
            }
            views.add(new MeldView(chunk, List.copyOf(faceDownFlags), -1, 0, null));
        }
        return List.copyOf(views);
    }

    @Override
    public List<ScoringStick> scoringSticks(UUID playerId) {
        return List.of();
    }

    @Override
    public List<MahjongTile> doraIndicators() {
        return List.of();
    }

    @Override
    public List<MahjongTile> uraDoraIndicators() {
        return List.of();
    }

    @Override
    public RoundResolution lastResolution() {
        return this.lastResolution;
    }

    @Override
    public ReactionOptions availableReactions(UUID playerId) {
        if (playerId == null || this.pendingReactionWindow == null || this.pendingReactionWindow.responses().containsKey(playerId)) {
            return null;
        }
        return this.pendingReactionWindow.options().get(playerId);
    }

    @Override
    public boolean hasPendingReaction() {
        return this.pendingReactionWindow != null;
    }

    @Override
    public String pendingReactionFingerprint() {
        return this.pendingReactionWindow == null ? "" : this.pendingReactionWindow.toString();
    }

    @Override
    public String pendingReactionTileKey() {
        return this.pendingReactionWindow == null ? "" : this.pendingReactionWindow.tile().name();
    }

    @Override
    public boolean isCurrentPlayer(UUID playerId) {
        return this.started && Objects.equals(this.currentPlayerId(), playerId);
    }

    @Override
    public boolean canSelectHandTile(UUID playerId, int tileIndex) {
        if (!this.started || this.pendingReactionWindow != null || !this.isCurrentPlayer(playerId)) {
            return false;
        }
        List<MahjongTile> hand = this.hands.get(playerId);
        return hand != null && tileIndex >= 0 && tileIndex < hand.size();
    }

    @Override
    public boolean canDeclareKan(UUID playerId) {
        if (playerId == null || !this.isCurrentPlayer(playerId) || this.hasPendingReaction()) {
            return false;
        }
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        for (MahjongTile tile : hand) {
            if (GbRoundSupport.countMatchingTiles(hand, tile) >= 4) {
                return true;
            }
        }
        for (GbMeldState meld : this.melds.getOrDefault(playerId, List.of())) {
            if (meld.type() == GbMeldType.PUNG && GbRoundSupport.countMatchingTiles(hand, meld.baseTile()) >= 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> suggestedKanTiles(UUID playerId) {
        if (playerId == null || !this.isCurrentPlayer(playerId) || this.hasPendingReaction()) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        for (MahjongTile tile : hand) {
            String lowered = tile.name().toLowerCase(Locale.ROOT);
            if (GbRoundSupport.countMatchingTiles(hand, tile) >= 4 && !suggestions.contains(lowered)) {
                suggestions.add(lowered);
            }
        }
        for (GbMeldState meld : this.melds.getOrDefault(playerId, List.of())) {
            if (meld.type() == GbMeldType.PUNG && GbRoundSupport.countMatchingTiles(hand, meld.baseTile()) >= 1) {
                String lowered = meld.baseTile().name().toLowerCase(Locale.ROOT);
                if (!suggestions.contains(lowered)) {
                    suggestions.add(lowered);
                }
            }
        }
        return List.copyOf(suggestions);
    }

    public int suggestedBotDiscardIndex(UUID playerId) {
        if (playerId == null || !this.isCurrentPlayer(playerId) || this.hasPendingReaction()) {
            return -1;
        }
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        if (hand.isEmpty()) {
            return -1;
        }
        GbBotDiscardChoice best = null;
        List<GbMeldState> currentMelds = this.melds.getOrDefault(playerId, List.of());
        EnumMap<MahjongTile, GbTingResponse> tingMemo = new EnumMap<>(MahjongTile.class);
        for (int i = 0; i < hand.size(); i++) {
            MahjongTile discarded = hand.get(i);
            List<MahjongTile> remaining = new ArrayList<>(hand);
            remaining.remove(i);
            GbTingResponse response = tingMemo.computeIfAbsent(discarded, ignored -> this.evaluateTing(playerId, remaining, currentMelds));
            GbBotDiscardChoice candidate = new GbBotDiscardChoice(i, botReadyScore(response), botDiscardPreference(hand, discarded));
            if (best == null || candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best == null ? hand.size() - 1 : best.index();
    }

    public ReactionResponse suggestedBotReaction(UUID playerId) {
        ReactionOptions options = this.availableReactions(playerId);
        if (playerId == null || options == null || this.pendingReactionWindow == null) {
            return new ReactionResponse(ReactionType.SKIP, null);
        }
        if (options.getCanRon()) {
            return new ReactionResponse(ReactionType.RON, null);
        }
        long skipReadyScore = botReadyScore(this.tingOptions(playerId));
        GbBotReactionChoice best = new GbBotReactionChoice(new ReactionResponse(ReactionType.SKIP, null), skipReadyScore, 0);
        MahjongTile claimedTile = this.pendingReactionWindow.tile();
        SeatWind fromSeat = this.seatOf(this.pendingReactionWindow.discarderId());
        if (options.getCanPon()) {
            GbBotReactionChoice candidate = this.evaluateBotPung(playerId, claimedTile, fromSeat);
            if (candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        if (options.getCanMinkan()) {
            GbBotReactionChoice candidate = this.evaluateBotOpenKong(playerId, claimedTile, fromSeat);
            if (candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        for (Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> pair : options.getChiiPairs()) {
            GbBotReactionChoice candidate = this.evaluateBotChow(playerId, claimedTile, fromSeat, pair);
            if (candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best.readyScore() > skipReadyScore ? best.response() : new ReactionResponse(ReactionType.SKIP, null);
    }

    public String suggestedBotKanTile(UUID playerId) {
        if (playerId == null || !this.isCurrentPlayer(playerId) || this.hasPendingReaction()) {
            return null;
        }
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        if (hand.isEmpty()) {
            return null;
        }
        long baselineReadyScore = botReadyScoreForBestDiscard(playerId, hand, this.melds.getOrDefault(playerId, List.of()));
        if (baselineReadyScore > 0) {
            return null;
        }
        GbBotKanChoice best = null;
        for (String tileName : this.suggestedKanTiles(playerId)) {
            MahjongTile target;
            try {
                target = MahjongTile.valueOf(tileName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            GbBotState simulated = this.simulateBotKan(playerId, target);
            if (simulated == null) {
                continue;
            }
            long readyScore = botReadyScore(this.evaluateTing(playerId, simulated.hand(), simulated.melds()));
            if (readyScore <= 0) {
                continue;
            }
            GbBotKanChoice candidate = new GbBotKanChoice(tileName, readyScore);
            if (best == null || candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best == null ? null : best.tileName();
    }

    public GbTingResponse tingOptions(UUID playerId) {
        return this.tingCache.getOrDefault(playerId, new GbTingResponse(false, List.of(), "No ting data."));
    }

    public boolean canWinByTsumo(UUID playerId) {
        GbFanResponse response = this.evaluateSelfDraw(playerId);
        return response.getValid() && response.getTotalFan() >= MIN_GB_FAN;
    }

    private PendingReactionWindow buildPendingReactionWindow(UUID discarderId, MahjongTile discardedTile) {
        if (discarderId == null || discardedTile == null) {
            return null;
        }
        LinkedHashMap<UUID, ReactionOptions> options = new LinkedHashMap<>();
        SeatWind discarderSeat = this.seatOf(discarderId);
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null || playerId.equals(discarderId)) {
                continue;
            }
            boolean canRon = this.canRon(playerId, discarderSeat, discardedTile);
            boolean canPon = GbRoundSupport.countMatchingTiles(this.hands.get(playerId), discardedTile) >= 2;
            boolean canMinkan = GbRoundSupport.countMatchingTiles(this.hands.get(playerId), discardedTile) >= 3;
            List<Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile>> chiiPairs =
                this.canChii(wind, discarderSeat) ? this.availableChiiPairs(playerId, discardedTile) : List.of();
            if (canRon || canPon || canMinkan || !chiiPairs.isEmpty()) {
                options.put(playerId, new ReactionOptions(canRon, canPon, canMinkan, chiiPairs));
            }
        }
        return options.isEmpty()
            ? null
            : new PendingReactionWindow(
                discarderId,
                discardedTile,
                options,
                new HashMap<>(),
                this.discardWinFlags(discardedTile, false),
                false,
                null
            );
    }

    private boolean resolvePendingReactions() {
        PendingReactionWindow pending = this.pendingReactionWindow;
        if (pending == null) {
            return false;
        }
        UUID discarderId = pending.discarderId();
        SeatWind discarderSeat = this.seatOf(discarderId);
        List<ResolvedGbWin> wins = new ArrayList<>();
        for (SeatWind wind : orderedAfter(discarderSeat)) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null) {
                continue;
            }
            ReactionResponse response = pending.responses().get(playerId);
            if (response != null && response.getType() == ReactionType.RON) {
                GbWinResponse win = this.nativeGateway.evaluateWin(
                    this.buildWinRequest(playerId, discarderId, pending.tile(), "DISCARD", pending.flags())
                );
                if (win.getValid() && win.getTotalFan() >= MIN_GB_FAN) {
                    wins.add(new ResolvedGbWin(playerId, discarderId, pending.tile(), win));
                }
            }
        }
        if (!wins.isEmpty()) {
            this.finishWins(List.copyOf(wins));
            return true;
        }
        if (pending.robbingKong()) {
            this.pendingReactionWindow = null;
            this.finishAddedKong(discarderId, pending.tile(), pending.upgradeMeldIndex());
            return true;
        }
        for (SeatWind wind : orderedAfter(discarderSeat)) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null) {
                continue;
            }
            ReactionResponse response = pending.responses().get(playerId);
            if (response == null) {
                continue;
            }
            if (response.getType() == ReactionType.MINKAN) {
                this.consumeClaimedDiscard(discarderId, pending.tile());
                this.claimOpenKong(playerId, pending.tile(), discarderSeat);
                this.pendingReactionWindow = null;
                this.currentPlayerIndex = wind.index();
                this.drawReplacementTileOrFinish(playerId);
                this.refreshAllTing();
                return true;
            }
            if (response.getType() == ReactionType.PON) {
                this.consumeClaimedDiscard(discarderId, pending.tile());
                this.claimPung(playerId, pending.tile(), discarderSeat);
                this.pendingReactionWindow = null;
                this.currentPlayerIndex = wind.index();
                this.refreshAllTing();
                return true;
            }
        }
        for (SeatWind wind : orderedAfter(discarderSeat)) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null) {
                continue;
            }
            ReactionResponse response = pending.responses().get(playerId);
            if (response != null && response.getType() == ReactionType.CHII) {
                this.consumeClaimedDiscard(discarderId, pending.tile());
                this.claimChow(playerId, pending.tile(), discarderSeat, response.getChiiPair());
                this.pendingReactionWindow = null;
                this.currentPlayerIndex = wind.index();
                this.refreshAllTing();
                return true;
            }
        }
        this.pendingReactionWindow = null;
        this.advanceAfterDiscard();
        return true;
    }

    private void claimPung(UUID playerId, MahjongTile claimedTile, SeatWind fromSeat) {
        GbRoundSupport.removeTiles(this.hands.get(playerId), claimedTile, 2);
        this.hasDrawnTile.put(playerId, false);
        this.sortHand(playerId);
        this.melds.get(playerId).add(GbMeldState.pung(claimedTile, fromSeat));
    }

    private void claimOpenKong(UUID playerId, MahjongTile claimedTile, SeatWind fromSeat) {
        GbRoundSupport.removeTiles(this.hands.get(playerId), claimedTile, 3);
        this.hasDrawnTile.put(playerId, false);
        this.sortHand(playerId);
        this.melds.get(playerId).add(GbMeldState.openKong(claimedTile, fromSeat));
        this.kanCount++;
    }

    private void claimChow(UUID playerId, MahjongTile claimedTile, SeatWind fromSeat, Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> pair) {
        MahjongTile first = MahjongTile.valueOf(pair.getFirst().name());
        MahjongTile second = MahjongTile.valueOf(pair.getSecond().name());
        GbRoundSupport.removeTiles(this.hands.get(playerId), first, 1);
        GbRoundSupport.removeTiles(this.hands.get(playerId), second, 1);
        this.hasDrawnTile.put(playerId, false);
        this.sortHand(playerId);
        this.melds.get(playerId).add(GbMeldState.chow(claimedTile, first, second, fromSeat));
    }

    private boolean canRon(UUID playerId, SeatWind discarderSeat, MahjongTile winningTile) {
        GbFanResponse response = this.nativeGateway.evaluateFan(
            this.buildFanRequest(playerId, winningTile, "DISCARD", discarderSeat, this.discardWinFlags(winningTile, false))
        );
        return response.getValid() && response.getTotalFan() >= MIN_GB_FAN;
    }

    private GbFanResponse evaluateSelfDraw(UUID playerId) {
        if (playerId == null || !this.isCurrentPlayer(playerId)) {
            return new GbFanResponse(false, 0, List.of(), "It is not this player's turn.");
        }
        MahjongTile winningTile = this.drawnTile(playerId);
        if (winningTile == null) {
            return new GbFanResponse(false, 0, List.of(), "Player has no drawn tile.");
        }
        return this.nativeGateway.evaluateFan(
            this.buildFanRequest(playerId, winningTile, "SELF_DRAW", null, this.selfDrawFlags(playerId, winningTile))
        );
    }

    private GbFanRequest buildFanRequest(UUID playerId, MahjongTile winningTile, String winType, SeatWind discarderSeat, List<String> flags) {
        List<MahjongTile> concealed = new ArrayList<>(this.hands.getOrDefault(playerId, List.of()));
        if ("SELF_DRAW".equals(winType) && this.hasDrawnTile(playerId) && !concealed.isEmpty()) {
            concealed.remove(concealed.size() - 1);
        }
        List<String> encodedHand = concealed.stream().map(GbTileEncoding::encode).toList();
        return new GbFanRequest(
            "GB_MAHJONG",
            encodedHand,
            this.toNativeMelds(playerId),
            GbTileEncoding.encode(winningTile),
            winType,
            GbTileEncoding.encodeWind(this.seatOf(playerId)),
            GbTileEncoding.encodeWind(this.roundWind()),
            this.encodedFlowers(playerId),
            flags
        );
    }

    private GbTingRequest buildTingRequest(UUID playerId) {
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        List<MahjongTile> concealed = hand.stream()
            .limit(this.hasDrawnTile(playerId) && !hand.isEmpty() ? hand.size() - 1L : hand.size())
            .toList();
        return this.buildTingRequest(playerId, concealed, this.melds.getOrDefault(playerId, List.of()));
    }

    private GbTingRequest buildTingRequest(UUID playerId, List<MahjongTile> concealedHand, List<GbMeldState> meldStates) {
        List<String> encodedHand = concealedHand.stream().map(GbTileEncoding::encode).toList();
        return new GbTingRequest(
            "GB_MAHJONG",
            encodedHand,
            this.toNativeMelds(playerId, meldStates),
            GbTileEncoding.encodeWind(this.seatOf(playerId)),
            GbTileEncoding.encodeWind(this.roundWind()),
            this.encodedFlowers(playerId),
            List.of()
        );
    }

    private GbWinRequest buildWinRequest(UUID winnerId, UUID discarderId, MahjongTile winningTile, String winType, List<String> flags) {
        List<MahjongTile> concealed = new ArrayList<>(this.hands.getOrDefault(winnerId, List.of()));
        if ("SELF_DRAW".equals(winType) && this.hasDrawnTile(winnerId) && !concealed.isEmpty()) {
            concealed.remove(concealed.size() - 1);
        }
        List<String> encodedHand = concealed.stream().map(GbTileEncoding::encode).toList();
        List<GbSeatPointsInput> seatPoints = new ArrayList<>(SeatWind.values().length);
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            if (playerId != null) {
                seatPoints.add(new GbSeatPointsInput(GbTileEncoding.encodeWind(wind), this.points(playerId)));
            }
        }
        return new GbWinRequest(
            "GB_MAHJONG",
            encodedHand,
            this.toNativeMelds(winnerId),
            GbTileEncoding.encode(winningTile),
            winType,
            GbTileEncoding.encodeWind(this.seatOf(winnerId)),
            discarderId == null ? null : GbTileEncoding.encodeWind(this.seatOf(discarderId)),
            GbTileEncoding.encodeWind(this.seatOf(winnerId)),
            GbTileEncoding.encodeWind(this.roundWind()),
            seatPoints,
            this.encodedFlowers(winnerId),
            flags
        );
    }

    private List<String> encodedFlowers(UUID playerId) {
        return this.flowers.getOrDefault(playerId, List.of()).stream().map(GbTileEncoding::encode).toList();
    }

    private List<GbMeldInput> toNativeMelds(UUID playerId) {
        return this.toNativeMelds(playerId, this.melds.getOrDefault(playerId, List.of()));
    }

    private List<GbMeldInput> toNativeMelds(UUID playerId, List<GbMeldState> playerMelds) {
        List<GbMeldInput> inputs = new ArrayList<>(playerMelds.size());
        for (GbMeldState meld : playerMelds) {
            List<String> tiles = meld.tiles().stream().map(GbTileEncoding::encode).toList();
            inputs.add(new GbMeldInput(
                meld.nativeType(),
                tiles,
                meld.claimedTile() == null ? null : GbTileEncoding.encode(meld.claimedTile()),
                meld.fromSeat() == null ? null : GbRoundSupport.relationLabel(this.seatOf(playerId), meld.fromSeat()),
                meld.open()
            ));
        }
        return List.copyOf(inputs);
    }

    private void finishWins(List<ResolvedGbWin> winners) {
        if (winners.isEmpty()) {
            return;
        }
        Map<UUID, Integer> originalPoints = new HashMap<>(this.points);
        for (ResolvedGbWin winner : winners) {
            this.applyScoreDeltas(winner.response().getScoreDeltas());
        }
        List<YakuSettlement> settlements = new ArrayList<>(winners.size());
        for (ResolvedGbWin winner : winners) {
            UUID winnerId = winner.winnerId();
            int winnerDelta = this.points.getOrDefault(winnerId, originalPoints.getOrDefault(winnerId, 0)) - originalPoints.getOrDefault(winnerId, 0);
            settlements.add(new YakuSettlement(
                this.displayNames.getOrDefault(winnerId, winnerId.toString()),
                winnerId.toString(),
                fanLabels(winner.response().getFans()),
                List.of(),
                List.of(),
                false,
                0,
                false,
                GbRoundSupport.toRiichiTile(winner.winningTile()),
                GbRoundSupport.toRiichiTiles(this.hands.getOrDefault(winnerId, List.of())),
                this.toSettlementMelds(winnerId),
                List.of(),
                List.of(),
                0,
                winner.response().getTotalFan(),
                winnerDelta
            ));
        }
        ScoreSettlement scoreSettlement = new ScoreSettlement(winners.getFirst().response().getTitle(), this.toScoreItems(originalPoints));
        this.pendingReactionWindow = null;
        this.started = false;
        this.afterKanTsumoPlayer = null;
        this.lastResolution = new RoundResolution(winners.getFirst().response().getTitle(), List.copyOf(settlements), scoreSettlement, null);
        this.advanceMatchState();
    }

    private void applyScoreDeltas(List<GbScoreDelta> scoreDeltas) {
        for (GbScoreDelta delta : scoreDeltas) {
            SeatWind wind = SeatWind.valueOf(delta.getSeat());
            UUID playerId = this.playerAt(wind);
            if (playerId != null) {
                this.points.put(playerId, this.points.getOrDefault(playerId, 0) + delta.getDelta());
            }
        }
    }

    @Override
    public void setPendingDiceRoll(OpeningDiceRoll diceRoll) {
        this.pendingDiceRoll = diceRoll;
    }

    private List<ScoreItem> toScoreItems(Map<UUID, Integer> originalPoints) {
        List<ScoreItem> scoreItems = new ArrayList<>(SeatWind.values().length);
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null) {
                continue;
            }
            int origin = originalPoints.getOrDefault(playerId, this.rule.getStartingPoints());
            int updated = this.points.getOrDefault(playerId, origin);
            scoreItems.add(new ScoreItem(this.displayNames.getOrDefault(playerId, playerId.toString()), playerId.toString(), origin, updated - origin));
        }
        return List.copyOf(scoreItems);
    }

    private List<Pair<Boolean, List<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile>>> toSettlementMelds(UUID playerId) {
        List<Pair<Boolean, List<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile>>> result = new ArrayList<>();
        for (GbMeldState meld : this.melds.getOrDefault(playerId, List.of())) {
            result.add(new Pair<>(meld.open(), GbRoundSupport.toRiichiTiles(meld.tiles())));
        }
        return List.copyOf(result);
    }

    private static List<String> fanLabels(List<GbFanEntry> fans) {
        List<String> labels = new ArrayList<>(fans.size());
        for (GbFanEntry fan : fans) {
            labels.add(fan.getCount() > 1 ? fan.getName() + " x" + fan.getCount() : fan.getName());
        }
        return List.copyOf(labels);
    }

    private List<Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile>> availableChiiPairs(UUID playerId, MahjongTile claimedTile) {
        if (GbRoundSupport.isHonor(claimedTile) || claimedTile == null || claimedTile.isFlower()) {
            return List.of();
        }
        List<Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile>> pairs = new ArrayList<>();
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        int number = GbRoundSupport.tileNumber(claimedTile);
        MahjongTile prev2 = GbRoundSupport.offsetTile(claimedTile, -2);
        MahjongTile prev1 = GbRoundSupport.offsetTile(claimedTile, -1);
        MahjongTile next1 = GbRoundSupport.offsetTile(claimedTile, 1);
        MahjongTile next2 = GbRoundSupport.offsetTile(claimedTile, 2);
        if (number >= 3 && GbRoundSupport.containsTile(hand, prev2) && GbRoundSupport.containsTile(hand, prev1)) {
            pairs.add(new Pair<>(GbRoundSupport.toRiichiTile(prev2), GbRoundSupport.toRiichiTile(prev1)));
        }
        if (number >= 2 && number <= 8 && GbRoundSupport.containsTile(hand, prev1) && GbRoundSupport.containsTile(hand, next1)) {
            pairs.add(new Pair<>(GbRoundSupport.toRiichiTile(prev1), GbRoundSupport.toRiichiTile(next1)));
        }
        if (number <= 7 && GbRoundSupport.containsTile(hand, next1) && GbRoundSupport.containsTile(hand, next2)) {
            pairs.add(new Pair<>(GbRoundSupport.toRiichiTile(next1), GbRoundSupport.toRiichiTile(next2)));
        }
        return List.copyOf(pairs);
    }

    private void advanceAfterDiscard() {
        this.currentPlayerIndex = Math.floorMod(this.currentPlayerIndex + 1, SeatWind.values().length);
        UUID current = this.currentPlayerId();
        if (current == null) {
            this.started = false;
            return;
        }
        if (!this.drawTile(current, false, false, true)) {
            this.finishExhaustiveDraw();
        }
        this.refreshAllTing();
    }

    private boolean drawReplacementTileOrFinish(UUID playerId) {
        if (this.drawTile(playerId, true, true, true)) {
            return true;
        }
        this.finishExhaustiveDraw();
        return true;
    }

    private boolean drawTile(UUID playerId, boolean fromBack) {
        return this.drawTile(playerId, fromBack, false, true);
    }

    private boolean drawTile(UUID playerId, boolean fromBack, boolean markAfterKong) {
        return this.drawTile(playerId, fromBack, markAfterKong, true);
    }

    private boolean drawTile(UUID playerId, boolean fromBack, boolean markAfterKong, boolean markAsDrawn) {
        if (playerId == null || this.wall.isEmpty()) {
            return false;
        }
        boolean nextFromBack = fromBack;
        while (!this.wall.isEmpty()) {
            List<MahjongTile> mutableWall = new ArrayList<>(this.wall);
            MahjongTile tile = nextFromBack ? mutableWall.remove(mutableWall.size() - 1) : mutableWall.remove(0);
            this.wall = List.copyOf(mutableWall);
            if (tile.isFlower()) {
                this.flowers.get(playerId).add(tile);
                nextFromBack = true;
                continue;
            }
            this.hands.get(playerId).add(tile);
            this.hasDrawnTile.put(playerId, markAsDrawn);
            this.sortHand(playerId);
            this.afterKanTsumoPlayer = markAfterKong ? playerId : null;
            return true;
        }
        return false;
    }

    private void finishExhaustiveDraw() {
        this.pendingReactionWindow = null;
        this.started = false;
        this.afterKanTsumoPlayer = null;
        this.lastResolution = new RoundResolution("DRAW", List.of(), null, ExhaustiveDraw.NORMAL);
        this.advanceMatchState();
    }

    private void advanceMatchState() {
        if (!this.round.isAllLast(this.rule)) {
            this.round.nextRound();
            this.gameFinished = false;
            return;
        }
        if (this.points.values().stream().anyMatch(score -> score >= this.rule.getMinPointsToWin())) {
            this.gameFinished = true;
            return;
        }
        Pair<Wind, Integer> finalRound = this.rule.getLength().getFinalRound();
        if (this.round.getWind() == finalRound.getFirst() && this.round.getRound() == finalRound.getSecond()) {
            this.gameFinished = true;
            return;
        }
        this.round.nextRound();
        this.gameFinished = false;
    }

    private PendingReactionWindow buildRobbingKongWindow(UUID discarderId, MahjongTile claimedTile, int upgradeMeldIndex) {
        LinkedHashMap<UUID, ReactionOptions> options = new LinkedHashMap<>();
        SeatWind discarderSeat = this.seatOf(discarderId);
        List<String> flags = this.discardWinFlags(claimedTile, true);
        for (SeatWind wind : orderedAfter(discarderSeat)) {
            UUID playerId = this.playerAt(wind);
            if (playerId == null || playerId.equals(discarderId)) {
                continue;
            }
            GbFanResponse response = this.nativeGateway.evaluateFan(this.buildFanRequest(playerId, claimedTile, "DISCARD", discarderSeat, flags));
            boolean canRon = response.getValid() && response.getTotalFan() >= MIN_GB_FAN;
            if (canRon) {
                options.put(playerId, new ReactionOptions(true, false, false, List.of()));
            }
        }
        return options.isEmpty()
            ? null
            : new PendingReactionWindow(discarderId, claimedTile, options, new HashMap<>(), flags, true, upgradeMeldIndex);
    }

    private void finishAddedKong(UUID playerId, MahjongTile target, Integer meldIndex) {
        if (meldIndex == null) {
            return;
        }
        GbRoundSupport.removeTiles(this.hands.get(playerId), target, 1);
        this.hasDrawnTile.put(playerId, false);
        this.sortHand(playerId);
        GbMeldState meld = this.melds.get(playerId).get(meldIndex);
        this.melds.get(playerId).set(meldIndex, meld.toAddedKong(target));
        this.kanCount++;
        this.drawReplacementTileOrFinish(playerId);
        this.refreshAllTing();
    }

    private List<String> selfDrawFlags(UUID playerId, MahjongTile winningTile) {
        List<String> flags = new ArrayList<>();
        if (this.wall.isEmpty()) {
            flags.add("LAST_TILE");
        }
        if (Objects.equals(this.afterKanTsumoPlayer, playerId)) {
            flags.add("AFTER_KONG");
        }
        if (this.visibleTileCount(winningTile) >= 3) {
            flags.add("LAST_OF_KIND");
        }
        return List.copyOf(flags);
    }

    private List<String> discardWinFlags(MahjongTile winningTile, boolean robbingKong) {
        List<String> flags = new ArrayList<>();
        if (this.wall.isEmpty()) {
            flags.add("LAST_TILE");
        }
        if (robbingKong) {
            flags.add("ROBBING_KONG");
        }
        if (this.visibleTileCount(winningTile) >= 3) {
            flags.add("LAST_OF_KIND");
        }
        return List.copyOf(flags);
    }

    private int visibleTileCount(MahjongTile target) {
        int count = 0;
        for (List<MahjongTile> playerDiscards : this.discards.values()) {
            count += GbRoundSupport.countMatchingTiles(playerDiscards, target);
        }
        for (List<GbMeldState> playerMelds : this.melds.values()) {
            for (GbMeldState meld : playerMelds) {
                if (!meld.open()) {
                    continue;
                }
                count += GbRoundSupport.countMatchingTiles(meld.tiles(), target);
            }
        }
        return count;
    }

    private MahjongTile drawnTile(UUID playerId) {
        List<MahjongTile> hand = this.hands.get(playerId);
        return hand == null || hand.isEmpty() || !this.hasDrawnTile(playerId) ? null : hand.get(hand.size() - 1);
    }

    private boolean hasDrawnTile(UUID playerId) {
        return Boolean.TRUE.equals(this.hasDrawnTile.get(playerId));
    }

    private void sortHand(UUID playerId) {
        List<MahjongTile> hand = this.hands.get(playerId);
        if (hand == null || hand.size() < 2) {
            return;
        }
        if (!this.hasDrawnTile(playerId)) {
            hand.sort((left, right) -> Integer.compare(handTileSort(left), handTileSort(right)));
            return;
        }
        MahjongTile drawn = hand.remove(hand.size() - 1);
        hand.sort((left, right) -> Integer.compare(handTileSort(left), handTileSort(right)));
        hand.add(drawn);
    }

    private static int handTileSort(MahjongTile tile) {
        return tile.ordinal();
    }

    private void consumeClaimedDiscard(UUID discarderId, MahjongTile claimedTile) {
        List<MahjongTile> river = this.discards.get(discarderId);
        if (river == null || river.isEmpty() || claimedTile == null) {
            return;
        }
        for (int i = river.size() - 1; i >= 0; i--) {
            if (GbRoundSupport.sameKind(river.get(i), claimedTile)) {
                river.remove(i);
                return;
            }
        }
    }

    private void refreshAllTing() {
        for (UUID playerId : this.seats.values()) {
            if (playerId == null) {
                continue;
            }
            this.tingCache.put(playerId, this.evaluateTing(playerId, this.currentConcealedHand(playerId), this.melds.getOrDefault(playerId, List.of())));
        }
    }

    private GbTingResponse evaluateTing(UUID playerId, List<MahjongTile> concealedHand, List<GbMeldState> meldStates) {
        return this.nativeGateway.evaluateTing(this.buildTingRequest(playerId, concealedHand, meldStates));
    }

    private List<MahjongTile> currentConcealedHand(UUID playerId) {
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        if (!this.hasDrawnTile(playerId) || hand.isEmpty()) {
            return List.copyOf(hand);
        }
        return List.copyOf(hand.subList(0, hand.size() - 1));
    }

    private GbBotReactionChoice evaluateBotPung(UUID playerId, MahjongTile claimedTile, SeatWind fromSeat) {
        List<MahjongTile> hand = new ArrayList<>(this.hands.getOrDefault(playerId, List.of()));
        GbRoundSupport.removeTiles(hand, claimedTile, 2);
        List<GbMeldState> meldStates = new ArrayList<>(this.melds.getOrDefault(playerId, List.of()));
        meldStates.add(GbMeldState.pung(claimedTile, fromSeat));
        return this.bestBotClaimDiscard(playerId, hand, meldStates, new ReactionResponse(ReactionType.PON, null));
    }

    private GbBotReactionChoice evaluateBotOpenKong(UUID playerId, MahjongTile claimedTile, SeatWind fromSeat) {
        List<MahjongTile> hand = new ArrayList<>(this.hands.getOrDefault(playerId, List.of()));
        GbRoundSupport.removeTiles(hand, claimedTile, 3);
        List<GbMeldState> meldStates = new ArrayList<>(this.melds.getOrDefault(playerId, List.of()));
        meldStates.add(GbMeldState.openKong(claimedTile, fromSeat));
        long readyScore = botReadyScore(this.evaluateTing(playerId, hand, meldStates));
        return new GbBotReactionChoice(new ReactionResponse(ReactionType.MINKAN, null), readyScore, 0);
    }

    private GbBotReactionChoice evaluateBotChow(
        UUID playerId,
        MahjongTile claimedTile,
        SeatWind fromSeat,
        Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> pair
    ) {
        List<MahjongTile> hand = new ArrayList<>(this.hands.getOrDefault(playerId, List.of()));
        MahjongTile first = MahjongTile.valueOf(pair.getFirst().name());
        MahjongTile second = MahjongTile.valueOf(pair.getSecond().name());
        GbRoundSupport.removeTiles(hand, first, 1);
        GbRoundSupport.removeTiles(hand, second, 1);
        List<GbMeldState> meldStates = new ArrayList<>(this.melds.getOrDefault(playerId, List.of()));
        meldStates.add(GbMeldState.chow(claimedTile, first, second, fromSeat));
        return this.bestBotClaimDiscard(playerId, hand, meldStates, new ReactionResponse(ReactionType.CHII, pair));
    }

    private GbBotReactionChoice bestBotClaimDiscard(
        UUID playerId,
        List<MahjongTile> hand,
        List<GbMeldState> meldStates,
        ReactionResponse response
    ) {
        if (hand.isEmpty()) {
            return new GbBotReactionChoice(response, 0, 0);
        }
        GbBotDiscardChoice best = null;
        EnumMap<MahjongTile, GbTingResponse> tingMemo = new EnumMap<>(MahjongTile.class);
        for (int i = 0; i < hand.size(); i++) {
            MahjongTile discarded = hand.get(i);
            List<MahjongTile> remaining = new ArrayList<>(hand);
            remaining.remove(i);
            GbTingResponse ting = tingMemo.computeIfAbsent(discarded, ignored -> this.evaluateTing(playerId, remaining, meldStates));
            GbBotDiscardChoice candidate = new GbBotDiscardChoice(i, botReadyScore(ting), botDiscardPreference(hand, discarded));
            if (best == null || candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best == null ? new GbBotReactionChoice(response, 0, 0) : new GbBotReactionChoice(response, best.readyScore(), best.discardPreference());
    }

    private long botReadyScoreForBestDiscard(UUID playerId, List<MahjongTile> hand, List<GbMeldState> meldStates) {
        long best = 0;
        EnumMap<MahjongTile, GbTingResponse> tingMemo = new EnumMap<>(MahjongTile.class);
        for (int i = 0; i < hand.size(); i++) {
            MahjongTile discarded = hand.get(i);
            List<MahjongTile> remaining = new ArrayList<>(hand);
            remaining.remove(i);
            GbTingResponse ting = tingMemo.computeIfAbsent(discarded, ignored -> this.evaluateTing(playerId, remaining, meldStates));
            best = Math.max(best, botReadyScore(ting));
        }
        return best;
    }

    private GbBotState simulateBotKan(UUID playerId, MahjongTile target) {
        List<MahjongTile> hand = new ArrayList<>(this.hands.getOrDefault(playerId, List.of()));
        List<GbMeldState> meldStates = new ArrayList<>(this.melds.getOrDefault(playerId, List.of()));
        if (GbRoundSupport.countMatchingTiles(hand, target) >= 4) {
            GbRoundSupport.removeTiles(hand, target, 4);
            meldStates.add(GbMeldState.ankan(target));
            return new GbBotState(List.copyOf(hand), List.copyOf(meldStates));
        }
        for (int i = 0; i < meldStates.size(); i++) {
            GbMeldState meld = meldStates.get(i);
            if (meld.type() == GbMeldType.PUNG
                && GbRoundSupport.sameKind(meld.baseTile(), target)
                && GbRoundSupport.countMatchingTiles(hand, target) >= 1) {
                GbRoundSupport.removeTiles(hand, target, 1);
                meldStates.set(i, meld.toAddedKong(target));
                return new GbBotState(List.copyOf(hand), List.copyOf(meldStates));
            }
        }
        return null;
    }

    private static long botReadyScore(GbTingResponse response) {
        if (response == null || !response.getValid() || response.getWaits().isEmpty()) {
            return 0;
        }
        long qualifiedWaits = 0;
        long bestFan = 0;
        long totalFan = 0;
        for (GbTingCandidate candidate : response.getWaits()) {
            int candidateFan = candidateTotalFan(candidate);
            if (candidateFan < MIN_GB_FAN) {
                continue;
            }
            qualifiedWaits++;
            bestFan = Math.max(bestFan, candidateFan);
            totalFan += candidateFan;
        }
        if (qualifiedWaits == 0) {
            return 0;
        }
        return 1_000_000L + bestFan * 10_000L + qualifiedWaits * 100L + totalFan;
    }

    private static int candidateTotalFan(GbTingCandidate candidate) {
        if (candidate.getTotalFan() != null) {
            return candidate.getTotalFan();
        }
        int total = 0;
        for (GbFanEntry fan : candidate.getFans()) {
            total += fan.getFan() * fan.getCount();
        }
        return total;
    }

    private static int botDiscardPreference(List<MahjongTile> hand, MahjongTile tile) {
        int duplicates = 0;
        boolean hasPrev = false;
        boolean hasNext = false;
        boolean hasPrevPrev = false;
        boolean hasNextNext = false;
        for (MahjongTile candidate : hand) {
            if (candidate == tile) {
                duplicates++;
                continue;
            }
            if (GbRoundSupport.isHonor(candidate) || GbRoundSupport.isHonor(tile) || candidate.name().charAt(0) != tile.name().charAt(0)) {
                continue;
            }
            int delta = GbRoundSupport.tileNumber(candidate) - GbRoundSupport.tileNumber(tile);
            if (delta == -2) {
                hasPrevPrev = true;
            }
            if (delta == -1) {
                hasPrev = true;
            }
            if (delta == 1) {
                hasNext = true;
            }
            if (delta == 2) {
                hasNextNext = true;
            }
        }
        int score = 0;
        if (GbRoundSupport.isHonor(tile) || GbRoundSupport.tileNumber(tile) == 1 || GbRoundSupport.tileNumber(tile) == 9) {
            score += 3;
        }
        if (duplicates >= 2) {
            score -= 4;
        }
        if (hasPrev) {
            score -= 2;
        }
        if (hasNext) {
            score -= 2;
        }
        if (hasPrevPrev) {
            score -= 1;
        }
        if (hasNextNext) {
            score -= 1;
        }
        return score;
    }

    private SeatWind roundWindSeat() {
        return switch (this.round.getWind()) {
            case EAST -> SeatWind.EAST;
            case SOUTH -> SeatWind.SOUTH;
            case WEST -> SeatWind.WEST;
            case NORTH -> SeatWind.NORTH;
        };
    }

    private boolean canChii(SeatWind candidate, SeatWind discarder) {
        return candidate == SeatWind.fromIndex(Math.floorMod(discarder.index() + 1, SeatWind.values().length));
    }

    private static List<SeatWind> orderedAfter(SeatWind start) {
        List<SeatWind> winds = new ArrayList<>(SeatWind.values().length - 1);
        for (int offset = 1; offset < SeatWind.values().length; offset++) {
            winds.add(SeatWind.fromIndex(Math.floorMod(start.index() + offset, SeatWind.values().length)));
        }
        return List.copyOf(winds);
    }

    private SeatWind seatOf(UUID playerId) {
        for (Map.Entry<SeatWind, UUID> entry : this.seats.entrySet()) {
            if (Objects.equals(entry.getValue(), playerId)) {
                return entry.getKey();
            }
        }
        return SeatWind.EAST;
    }

    private UUID currentPlayerId() {
        return this.playerAt(SeatWind.fromIndex(this.currentPlayerIndex));
    }

    private record GbBotState(List<MahjongTile> hand, List<GbMeldState> melds) {
    }

    private record GbBotDiscardChoice(int index, long readyScore, int discardPreference) implements Comparable<GbBotDiscardChoice> {
        @Override
        public int compareTo(GbBotDiscardChoice other) {
            int readyComparison = Long.compare(this.readyScore, other.readyScore);
            if (readyComparison != 0) {
                return readyComparison;
            }
            return Integer.compare(this.discardPreference, other.discardPreference);
        }
    }

    private record GbBotReactionChoice(ReactionResponse response, long readyScore, int detailScore) implements Comparable<GbBotReactionChoice> {
        @Override
        public int compareTo(GbBotReactionChoice other) {
            int readyComparison = Long.compare(this.readyScore, other.readyScore);
            if (readyComparison != 0) {
                return readyComparison;
            }
            return Integer.compare(this.detailScore, other.detailScore);
        }
    }

    private record GbBotKanChoice(String tileName, long readyScore) implements Comparable<GbBotKanChoice> {
        @Override
        public int compareTo(GbBotKanChoice other) {
            return Long.compare(this.readyScore, other.readyScore);
        }
    }

    private record PendingReactionWindow(
        UUID discarderId,
        MahjongTile tile,
        Map<UUID, ReactionOptions> options,
        Map<UUID, ReactionResponse> responses,
        List<String> flags,
        boolean robbingKong,
        Integer upgradeMeldIndex
    ) {
    }

    private record ResolvedGbWin(
        UUID winnerId,
        UUID discarderId,
        MahjongTile winningTile,
        GbWinResponse response
    ) {
    }

    private enum GbMeldType {
        CHOW,
        PUNG,
        OPEN_KONG,
        CONCEALED_KONG,
        ADDED_KONG
    }

    private record GbMeldState(
        GbMeldType type,
        List<MahjongTile> tiles,
        MahjongTile baseTile,
        MahjongTile claimedTile,
        SeatWind fromSeat,
        boolean open,
        int claimTileIndex,
        int claimYawOffset,
        MahjongTile addedKanTile
    ) {
        private static GbMeldState chow(MahjongTile claim, MahjongTile first, MahjongTile second, SeatWind fromSeat) {
            List<MahjongTile> ordered = new ArrayList<>(List.of(claim, first, second));
            ordered.sort((left, right) -> Integer.compare(tileSort(left), tileSort(right)));
            int claimIndex = ordered.indexOf(claim);
            return new GbMeldState(GbMeldType.CHOW, List.copyOf(ordered), claim, claim, fromSeat, true, claimIndex, 90, null);
        }

        private static GbMeldState pung(MahjongTile claim, SeatWind fromSeat) {
            List<MahjongTile> ordered = List.of(claim, claim, claim);
            return new GbMeldState(GbMeldType.PUNG, ordered, claim, claim, fromSeat, true, 1, claimYaw(fromSeat), null);
        }

        private static GbMeldState openKong(MahjongTile claim, SeatWind fromSeat) {
            List<MahjongTile> ordered = List.of(claim, claim, claim, claim);
            return new GbMeldState(GbMeldType.OPEN_KONG, ordered, claim, claim, fromSeat, true, 1, claimYaw(fromSeat), null);
        }

        private static GbMeldState ankan(MahjongTile tile) {
            List<MahjongTile> ordered = List.of(tile, tile, tile, tile);
            return new GbMeldState(GbMeldType.CONCEALED_KONG, ordered, tile, null, null, false, -1, 0, null);
        }

        private GbMeldState toAddedKong(MahjongTile tile) {
            List<MahjongTile> ordered = new ArrayList<>(this.tiles);
            ordered.add(tile);
            return new GbMeldState(GbMeldType.ADDED_KONG, List.copyOf(ordered), this.baseTile, this.claimedTile, this.fromSeat, true, this.claimTileIndex, this.claimYawOffset, tile);
        }

        private String nativeType() {
            return switch (this.type) {
                case CHOW -> "CHOW";
                case PUNG -> "PUNG";
                case OPEN_KONG -> "OPEN_KONG";
                case CONCEALED_KONG -> "CONCEALED_KONG";
                case ADDED_KONG -> "ADDED_KONG";
            };
        }

        private static int claimYaw(SeatWind fromSeat) {
            return switch (fromSeat) {
                case EAST, SOUTH -> 90;
                case WEST, NORTH -> -90;
            };
        }

        private static int tileSort(MahjongTile tile) {
            return switch (tile) {
                case M1, P1, S1 -> 1;
                case M2, P2, S2 -> 2;
                case M3, P3, S3 -> 3;
                case M4, P4, S4 -> 4;
                case M5, P5, S5, M5_RED, P5_RED, S5_RED -> 5;
                case M6, P6, S6 -> 6;
                case M7, P7, S7 -> 7;
                case M8, P8, S8 -> 8;
                case M9, P9, S9 -> 9;
                default -> 100 + tile.ordinal();
            };
        }
    }
}
