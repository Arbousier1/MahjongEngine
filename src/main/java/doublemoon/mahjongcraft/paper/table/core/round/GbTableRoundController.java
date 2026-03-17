package doublemoon.mahjongcraft.paper.table.core.round;

import doublemoon.mahjongcraft.paper.gb.jni.GbFanEntry;
import doublemoon.mahjongcraft.paper.gb.jni.GbFanRequest;
import doublemoon.mahjongcraft.paper.gb.jni.GbFanResponse;
import doublemoon.mahjongcraft.paper.gb.jni.GbMeldInput;
import doublemoon.mahjongcraft.paper.gb.jni.GbScoreDelta;
import doublemoon.mahjongcraft.paper.gb.jni.GbSeatPointsInput;
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
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule;
import doublemoon.mahjongcraft.paper.riichi.model.ScoreItem;
import doublemoon.mahjongcraft.paper.riichi.model.ScoreSettlement;
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick;
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
import kotlin.Pair;

public final class GbTableRoundController implements TableRoundController {
    private static final int MIN_GB_FAN = 8;

    private final MahjongRule rule;
    private final GbNativeRulesGateway nativeGateway;
    private final EnumMap<SeatWind, UUID> seats;
    private final Map<UUID, String> displayNames;
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, List<MahjongTile>> hands = new HashMap<>();
    private final Map<UUID, List<MahjongTile>> discards = new HashMap<>();
    private final Map<UUID, List<GbMeldState>> melds = new HashMap<>();
    private final Map<UUID, GbTingResponse> tingCache = new HashMap<>();
    private List<MahjongTile> wall = List.of();
    private boolean started;
    private boolean gameFinished;
    private int currentPlayerIndex;
    private int kanCount;
    private RoundResolution lastResolution;
    private PendingReactionWindow pendingReactionWindow;
    private UUID afterKanTsumoPlayer;

    public GbTableRoundController(MahjongRule rule, EnumMap<SeatWind, UUID> seats, Map<UUID, String> displayNames, GbNativeRulesGateway nativeGateway) {
        this.rule = rule;
        this.nativeGateway = nativeGateway;
        this.seats = new EnumMap<>(seats);
        this.displayNames = new HashMap<>(displayNames);
        for (UUID playerId : seats.values()) {
            if (playerId == null) {
                continue;
            }
            this.points.put(playerId, rule.getStartingPoints());
            this.hands.put(playerId, new ArrayList<>());
            this.discards.put(playerId, new ArrayList<>());
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
        this.wall = buildWall();
        this.pendingReactionWindow = null;
        this.lastResolution = null;
        this.started = true;
        this.gameFinished = false;
        this.currentPlayerIndex = SeatWind.EAST.index();
        this.kanCount = 0;
        this.afterKanTsumoPlayer = null;
        for (UUID playerId : this.seats.values()) {
            if (playerId == null) {
                continue;
            }
            this.hands.get(playerId).clear();
            this.discards.get(playerId).clear();
            this.melds.get(playerId).clear();
        }

        for (int draw = 0; draw < 13; draw++) {
            for (SeatWind wind : SeatWind.values()) {
                this.drawTile(this.playerAt(wind), false);
            }
        }
        this.drawTile(this.currentPlayerId(), false);
        this.refreshAllTing();
    }

    @Override
    public boolean discard(UUID playerId, int tileIndex) {
        if (!this.canSelectHandTile(playerId, tileIndex)) {
            return false;
        }
        MahjongTile discarded = this.hands.get(playerId).remove(tileIndex);
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
        if (countMatchingTiles(hand, target) >= 4) {
            removeTiles(hand, target, 4);
            this.melds.get(playerId).add(GbMeldState.ankan(target));
            this.kanCount++;
            boolean drew = this.drawTile(playerId, true);
            this.refreshAllTing();
            return drew;
        }
        for (int i = 0; i < this.melds.get(playerId).size(); i++) {
            GbMeldState meld = this.melds.get(playerId).get(i);
            if (meld.type() == GbMeldType.PUNG && sameKind(meld.baseTile(), target) && countMatchingTiles(hand, target) >= 1) {
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
        return 0;
    }

    @Override
    public int kanCount() {
        return this.kanCount;
    }

    @Override
    public int roundIndex() {
        return 0;
    }

    @Override
    public int honbaCount() {
        return 0;
    }

    @Override
    public SeatWind dealerSeat() {
        return SeatWind.EAST;
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
    public List<MeldView> fuuro(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        List<GbMeldState> playerMelds = this.melds.get(playerId);
        if (playerMelds == null || playerMelds.isEmpty()) {
            return List.of();
        }
        List<MeldView> views = new ArrayList<>(playerMelds.size());
        for (GbMeldState meld : playerMelds) {
            List<Boolean> faceDownFlags = new ArrayList<>(meld.tiles().size());
            for (int i = 0; i < meld.tiles().size(); i++) {
                faceDownFlags.add(meld.type() == GbMeldType.CONCEALED_KONG && (i == 0 || i == meld.tiles().size() - 1));
            }
            views.add(new MeldView(List.copyOf(meld.tiles()), List.copyOf(faceDownFlags), meld.claimTileIndex(), meld.claimYawOffset(), meld.addedKanTile()));
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
            if (countMatchingTiles(hand, tile) >= 4) {
                return true;
            }
        }
        for (GbMeldState meld : this.melds.getOrDefault(playerId, List.of())) {
            if (meld.type() == GbMeldType.PUNG && countMatchingTiles(hand, meld.baseTile()) >= 1) {
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
            if (countMatchingTiles(hand, tile) >= 4 && !suggestions.contains(lowered)) {
                suggestions.add(lowered);
            }
        }
        for (GbMeldState meld : this.melds.getOrDefault(playerId, List.of())) {
            if (meld.type() == GbMeldType.PUNG && countMatchingTiles(hand, meld.baseTile()) >= 1) {
                String lowered = meld.baseTile().name().toLowerCase(Locale.ROOT);
                if (!suggestions.contains(lowered)) {
                    suggestions.add(lowered);
                }
            }
        }
        return List.copyOf(suggestions);
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
            boolean canPon = countMatchingTiles(this.hands.get(playerId), discardedTile) >= 2;
            boolean canMinkan = countMatchingTiles(this.hands.get(playerId), discardedTile) >= 3;
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
                this.claimOpenKong(playerId, pending.tile(), discarderSeat);
                this.pendingReactionWindow = null;
                this.currentPlayerIndex = wind.index();
                this.drawTile(playerId, true);
                this.refreshAllTing();
                return true;
            }
            if (response.getType() == ReactionType.PON) {
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
        removeTiles(this.hands.get(playerId), claimedTile, 2);
        this.melds.get(playerId).add(GbMeldState.pung(claimedTile, fromSeat));
    }

    private void claimOpenKong(UUID playerId, MahjongTile claimedTile, SeatWind fromSeat) {
        removeTiles(this.hands.get(playerId), claimedTile, 3);
        this.melds.get(playerId).add(GbMeldState.openKong(claimedTile, fromSeat));
        this.kanCount++;
    }

    private void claimChow(UUID playerId, MahjongTile claimedTile, SeatWind fromSeat, Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> pair) {
        MahjongTile first = MahjongTile.valueOf(pair.getFirst().name());
        MahjongTile second = MahjongTile.valueOf(pair.getSecond().name());
        removeTiles(this.hands.get(playerId), first, 1);
        removeTiles(this.hands.get(playerId), second, 1);
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
        if ("SELF_DRAW".equals(winType) && !concealed.isEmpty()) {
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
            GbTileEncoding.encodeWind(this.dealerSeat()),
            List.of(),
            flags
        );
    }

    private GbTingRequest buildTingRequest(UUID playerId) {
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        List<String> encodedHand = hand.stream()
            .limit(hand.size() > 13 ? hand.size() - 1L : hand.size())
            .map(GbTileEncoding::encode)
            .toList();
        return new GbTingRequest(
            "GB_MAHJONG",
            encodedHand,
            this.toNativeMelds(playerId),
            GbTileEncoding.encodeWind(this.seatOf(playerId)),
            GbTileEncoding.encodeWind(this.dealerSeat()),
            List.of(),
            List.of()
        );
    }

    private GbWinRequest buildWinRequest(UUID winnerId, UUID discarderId, MahjongTile winningTile, String winType, List<String> flags) {
        List<MahjongTile> concealed = new ArrayList<>(this.hands.getOrDefault(winnerId, List.of()));
        if ("SELF_DRAW".equals(winType) && !concealed.isEmpty()) {
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
            GbTileEncoding.encodeWind(this.dealerSeat()),
            seatPoints,
            List.of(),
            flags
        );
    }

    private List<GbMeldInput> toNativeMelds(UUID playerId) {
        List<GbMeldState> playerMelds = this.melds.getOrDefault(playerId, List.of());
        List<GbMeldInput> inputs = new ArrayList<>(playerMelds.size());
        for (GbMeldState meld : playerMelds) {
            List<String> tiles = meld.tiles().stream().map(GbTileEncoding::encode).toList();
            inputs.add(new GbMeldInput(
                meld.nativeType(),
                tiles,
                meld.claimedTile() == null ? null : GbTileEncoding.encode(meld.claimedTile()),
                meld.fromSeat() == null ? null : relationLabel(this.seatOf(playerId), meld.fromSeat()),
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
                toRiichiTile(winner.winningTile()),
                this.toRiichiTiles(this.hands.getOrDefault(winnerId, List.of())),
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
        this.gameFinished = this.points.values().stream().anyMatch(score -> score >= this.rule.getMinPointsToWin());
        this.lastResolution = new RoundResolution(winners.getFirst().response().getTitle(), List.copyOf(settlements), scoreSettlement, null);
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
            result.add(new Pair<>(meld.open(), this.toRiichiTiles(meld.tiles())));
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
        if (isHonor(claimedTile)) {
            return List.of();
        }
        List<Pair<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile, doublemoon.mahjongcraft.paper.riichi.model.MahjongTile>> pairs = new ArrayList<>();
        List<MahjongTile> hand = this.hands.getOrDefault(playerId, List.of());
        int number = tileNumber(claimedTile);
        MahjongTile prev2 = offsetTile(claimedTile, -2);
        MahjongTile prev1 = offsetTile(claimedTile, -1);
        MahjongTile next1 = offsetTile(claimedTile, 1);
        MahjongTile next2 = offsetTile(claimedTile, 2);
        if (number >= 3 && containsTile(hand, prev2) && containsTile(hand, prev1)) {
            pairs.add(new Pair<>(toRiichiTile(prev2), toRiichiTile(prev1)));
        }
        if (number >= 2 && number <= 8 && containsTile(hand, prev1) && containsTile(hand, next1)) {
            pairs.add(new Pair<>(toRiichiTile(prev1), toRiichiTile(next1)));
        }
        if (number <= 7 && containsTile(hand, next1) && containsTile(hand, next2)) {
            pairs.add(new Pair<>(toRiichiTile(next1), toRiichiTile(next2)));
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
        if (!this.drawTile(current, false)) {
            this.started = false;
            this.lastResolution = new RoundResolution("DRAW", List.of(), null, ExhaustiveDraw.NORMAL);
        }
        this.refreshAllTing();
    }

    private boolean drawTile(UUID playerId, boolean fromBack) {
        if (playerId == null || this.wall.isEmpty()) {
            return false;
        }
        List<MahjongTile> mutableWall = new ArrayList<>(this.wall);
        MahjongTile tile = fromBack ? mutableWall.remove(mutableWall.size() - 1) : mutableWall.remove(0);
        this.wall = List.copyOf(mutableWall);
        this.hands.get(playerId).add(tile);
        this.afterKanTsumoPlayer = fromBack ? playerId : null;
        return true;
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
        removeTiles(this.hands.get(playerId), target, 1);
        GbMeldState meld = this.melds.get(playerId).get(meldIndex);
        this.melds.get(playerId).set(meldIndex, meld.toAddedKong(target));
        this.kanCount++;
        this.drawTile(playerId, true);
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
            count += countMatchingTiles(playerDiscards, target);
        }
        for (List<GbMeldState> playerMelds : this.melds.values()) {
            for (GbMeldState meld : playerMelds) {
                if (!meld.open()) {
                    continue;
                }
                count += countMatchingTiles(meld.tiles(), target);
            }
        }
        return count;
    }

    private MahjongTile drawnTile(UUID playerId) {
        List<MahjongTile> hand = this.hands.get(playerId);
        return hand == null || hand.isEmpty() ? null : hand.get(hand.size() - 1);
    }

    private void refreshAllTing() {
        for (UUID playerId : this.seats.values()) {
            if (playerId == null) {
                continue;
            }
            this.tingCache.put(playerId, this.nativeGateway.evaluateTing(this.buildTingRequest(playerId)));
        }
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

    private static String relationLabel(SeatWind claimant, SeatWind source) {
        int diff = Math.floorMod(source.index() - claimant.index(), SeatWind.values().length);
        return switch (diff) {
            case 1 -> "LEFT";
            case 2 -> "ACROSS";
            case 3 -> "RIGHT";
            default -> "SELF";
        };
    }

    private static boolean containsTile(List<MahjongTile> hand, MahjongTile target) {
        return countMatchingTiles(hand, target) > 0;
    }

    private static int countMatchingTiles(List<MahjongTile> hand, MahjongTile target) {
        if (hand == null || target == null) {
            return 0;
        }
        int count = 0;
        for (MahjongTile tile : hand) {
            if (sameKind(tile, target)) {
                count++;
            }
        }
        return count;
    }

    private static void removeTiles(List<MahjongTile> hand, MahjongTile target, int amount) {
        int remaining = amount;
        for (int i = hand.size() - 1; i >= 0 && remaining > 0; i--) {
            if (sameKind(hand.get(i), target)) {
                hand.remove(i);
                remaining--;
            }
        }
    }

    private static boolean sameKind(MahjongTile left, MahjongTile right) {
        if (left == null || right == null) {
            return false;
        }
        MahjongTile leftBase = left.isRedFive() ? MahjongTile.valueOf(left.name().replace("_RED", "")) : left;
        MahjongTile rightBase = right.isRedFive() ? MahjongTile.valueOf(right.name().replace("_RED", "")) : right;
        return leftBase == rightBase;
    }

    private static boolean isHonor(MahjongTile tile) {
        return tile.ordinal() >= MahjongTile.EAST.ordinal() && tile.ordinal() <= MahjongTile.RED_DRAGON.ordinal();
    }

    private static int tileNumber(MahjongTile tile) {
        return Integer.parseInt(tile.name().substring(1, 2));
    }

    private static MahjongTile offsetTile(MahjongTile tile, int delta) {
        if (tile == null || isHonor(tile)) {
            return MahjongTile.UNKNOWN;
        }
        char suit = tile.name().charAt(0);
        int number = tileNumber(tile) + delta;
        if (number < 1 || number > 9) {
            return MahjongTile.UNKNOWN;
        }
        return MahjongTile.valueOf("" + suit + number);
    }

    private static doublemoon.mahjongcraft.paper.riichi.model.MahjongTile toRiichiTile(MahjongTile tile) {
        return doublemoon.mahjongcraft.paper.riichi.model.MahjongTile.valueOf(tile.name());
    }

    private List<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> toRiichiTiles(List<MahjongTile> tiles) {
        List<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> converted = new ArrayList<>(tiles.size());
        for (MahjongTile tile : tiles) {
            converted.add(toRiichiTile(tile));
        }
        return List.copyOf(converted);
    }

    private static List<MahjongTile> buildWall() {
        List<MahjongTile> wall = new ArrayList<>(136);
        for (MahjongTile tile : MahjongTile.values()) {
            if (tile == MahjongTile.UNKNOWN || tile.isRedFive()) {
                continue;
            }
            for (int i = 0; i < 4; i++) {
                wall.add(tile);
            }
        }
        Collections.shuffle(wall);
        return List.copyOf(wall);
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
