package doublemoon.mahjongcraft.paper.table.core.round;

import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.render.scene.MeldView;
import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.RiichiDiscardSuggestion;
import doublemoon.mahjongcraft.paper.riichi.RiichiPlayerState;
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine;
import doublemoon.mahjongcraft.paper.riichi.RoundResolution;
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick;
import doublemoon.mahjongcraft.paper.table.core.MahjongVariant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RiichiTableRoundController implements TableRoundController {
    private final RiichiRoundEngine engine;

    public RiichiTableRoundController(RiichiRoundEngine engine) {
        this.engine = engine;
    }

    @Override
    public MahjongVariant variant() {
        return MahjongVariant.RIICHI;
    }

    @Override
    public doublemoon.mahjongcraft.paper.riichi.model.MahjongRule rule() {
        return this.engine.getRule();
    }

    @Override
    public boolean started() {
        return this.engine.getStarted();
    }

    @Override
    public boolean gameFinished() {
        return this.engine.getGameFinished();
    }

    @Override
    public void startRound() {
        this.engine.startRound();
    }

    @Override
    public void setPendingDiceRoll(OpeningDiceRoll diceRoll) {
        this.engine.setPendingDiceRoll(diceRoll);
    }

    @Override
    public boolean discard(UUID playerId, int tileIndex) {
        return playerId != null && this.engine.discard(playerId.toString(), tileIndex);
    }

    @Override
    public boolean declareRiichi(UUID playerId, int tileIndex) {
        return playerId != null && this.engine.declareRiichi(playerId.toString(), tileIndex);
    }

    @Override
    public boolean declareTsumo(UUID playerId) {
        return playerId != null && this.engine.tryTsumo(playerId.toString());
    }

    @Override
    public boolean declareKyuushuKyuuhai(UUID playerId) {
        return playerId != null && this.engine.declareKyuushuKyuuhai(playerId.toString());
    }

    @Override
    public boolean react(UUID playerId, ReactionResponse response) {
        return playerId != null && response != null && this.engine.react(playerId.toString(), response);
    }

    @Override
    public boolean declareKan(UUID playerId, String tileName) {
        if (playerId == null || tileName == null || tileName.isBlank()) {
            return false;
        }
        try {
            doublemoon.mahjongcraft.paper.riichi.model.MahjongTile tile =
                doublemoon.mahjongcraft.paper.riichi.model.MahjongTile.valueOf(GbRoundSupport.normalizeTileToken(tileName));
            return this.engine.tryAnkanOrKakan(playerId.toString(), tile);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public UUID playerAt(SeatWind wind) {
        if (wind == null || this.engine.getSeats().size() <= wind.index()) {
            return null;
        }
        return UUID.fromString(this.engine.getSeats().get(wind.index()).getUuid());
    }

    @Override
    public int points(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        return player == null ? 0 : player.getPoints();
    }

    @Override
    public boolean isRiichi(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        return player != null && (player.getRiichi() || player.getDoubleRiichi());
    }

    @Override
    public int dicePoints() {
        return this.engine.getDicePoints();
    }

    @Override
    public int kanCount() {
        return this.engine.getKanCount();
    }

    @Override
    public int roundIndex() {
        return this.engine.getRound().getRound();
    }

    @Override
    public int honbaCount() {
        return this.engine.getRound().getHonba();
    }

    @Override
    public SeatWind roundWind() {
        return switch (this.engine.getRound().getWind()) {
            case EAST -> SeatWind.EAST;
            case SOUTH -> SeatWind.SOUTH;
            case WEST -> SeatWind.WEST;
            case NORTH -> SeatWind.NORTH;
        };
    }

    @Override
    public SeatWind dealerSeat() {
        return SeatWind.fromIndex(this.engine.getRound().getRound());
    }

    @Override
    public SeatWind currentSeat() {
        return SeatWind.fromIndex(this.engine.getCurrentPlayerIndex());
    }

    @Override
    public String currentPlayerDisplayName() {
        return this.engine.getCurrentPlayer().getDisplayName();
    }

    @Override
    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null) {
            return List.of();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(player.getHands().size());
        player.getHands().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile.getMahjongTile().name())));
        return List.copyOf(tiles);
    }

    @Override
    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> discards(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null) {
            return List.of();
        }
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(player.getDiscardedTilesForDisplay().size());
        player.getDiscardedTilesForDisplay().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile.getMahjongTile().name())));
        return List.copyOf(tiles);
    }

    @Override
    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> remainingWall() {
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(this.engine.getWall().size());
        this.engine.getWall().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.UNKNOWN));
        return List.copyOf(tiles);
    }

    @Override
    public int remainingWallCount() {
        return this.engine.getWall().size();
    }

    @Override
    public List<MeldView> fuuro(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
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

    @Override
    public List<ScoringStick> scoringSticks(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        return player == null ? List.of() : List.copyOf(player.getSticks());
    }

    @Override
    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> doraIndicators() {
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(this.engine.getDoraIndicators().size());
        this.engine.getDoraIndicators().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile.getMahjongTile().name())));
        return List.copyOf(tiles);
    }

    @Override
    public List<doublemoon.mahjongcraft.paper.model.MahjongTile> uraDoraIndicators() {
        List<doublemoon.mahjongcraft.paper.model.MahjongTile> tiles = new ArrayList<>(this.engine.getUraDoraIndicators().size());
        this.engine.getUraDoraIndicators().forEach(tile -> tiles.add(doublemoon.mahjongcraft.paper.model.MahjongTile.valueOf(tile.getMahjongTile().name())));
        return List.copyOf(tiles);
    }

    @Override
    public RoundResolution lastResolution() {
        return this.engine.getLastResolution();
    }

    @Override
    public ReactionOptions availableReactions(UUID playerId) {
        return playerId == null ? null : this.engine.availableReactions(playerId.toString());
    }

    @Override
    public boolean hasPendingReaction() {
        return this.engine.getPendingReaction() != null;
    }

    @Override
    public String pendingReactionFingerprint() {
        return String.valueOf(this.engine.getPendingReaction());
    }

    @Override
    public String pendingReactionTileKey() {
        return this.engine.getPendingReaction() == null ? "" : this.engine.getPendingReaction().getTile().getId().toString();
    }

    @Override
    public boolean isCurrentPlayer(UUID playerId) {
        return playerId != null && this.engine.getCurrentPlayer().getUuid().equals(playerId.toString());
    }

    @Override
    public boolean canSelectHandTile(UUID playerId, int tileIndex) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null || tileIndex < 0 || tileIndex >= player.getHands().size()) {
            return false;
        }
        if (!this.engine.getStarted() || this.engine.getPendingReaction() != null) {
            return false;
        }
        return this.isCurrentPlayer(playerId);
    }

    @Override
    public boolean canDeclareRiichi(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        return player != null && this.isCurrentPlayer(playerId) && player.isRiichiable();
    }

    @Override
    public boolean canDeclareKan(UUID playerId) {
        return this.canDeclareConcealedKan(playerId) || this.canDeclareAddedKan(playerId);
    }

    @Override
    public boolean canDeclareConcealedKan(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        return player != null && this.isCurrentPlayer(playerId) && player.getCanAnkan();
    }

    @Override
    public boolean canDeclareAddedKan(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        return player != null && this.isCurrentPlayer(playerId) && player.getCanKakan();
    }

    @Override
    public boolean canDeclareKyuushu(UUID playerId) {
        return playerId != null && this.engine.canKyuushuKyuuhai(playerId.toString());
    }

    @Override
    public List<Integer> suggestedRiichiIndices(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null || !player.isRiichiable()) {
            return List.of();
        }
        java.util.Set<doublemoon.mahjongcraft.paper.riichi.model.MahjongTile> discardables = new java.util.LinkedHashSet<>();
        player.getTilePairsForRiichi().forEach(pair -> discardables.add(pair.getFirst()));
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < player.getHands().size(); i++) {
            if (discardables.contains(player.getHands().get(i).getMahjongTile())) {
                indices.add(i);
            }
        }
        return List.copyOf(indices);
    }

    @Override
    public List<String> suggestedKanTiles(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null) {
            return List.of();
        }
        java.util.Set<String> suggestions = new java.util.LinkedHashSet<>();
        suggestions.addAll(this.suggestedConcealedKanTiles(playerId));
        suggestions.addAll(this.suggestedAddedKanTiles(playerId));
        return List.copyOf(suggestions);
    }

    @Override
    public List<String> suggestedConcealedKanTiles(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null) {
            return List.of();
        }
        java.util.Set<String> suggestions = new java.util.LinkedHashSet<>();
        player.getTilesCanAnkan().forEach(tile -> suggestions.add(tile.getMahjongTile().name().toLowerCase(Locale.ROOT)));
        return List.copyOf(suggestions);
    }

    @Override
    public List<String> suggestedAddedKanTiles(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null || !player.getCanKakan()) {
            return List.of();
        }
        java.util.Set<String> suggestions = new java.util.LinkedHashSet<>();
        player.getHands().forEach(tile -> {
            boolean matchesMeld = player.getFuuroList().stream()
                .anyMatch(fuuro -> fuuro.getTileInstances().stream().anyMatch(existing -> existing.getMahjongTile() == tile.getMahjongTile()));
            if (matchesMeld) {
                suggestions.add(tile.getMahjongTile().name().toLowerCase(Locale.ROOT));
            }
        });
        return List.copyOf(suggestions);
    }

    @Override
    public List<String> suggestedDiscardTiles(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null || !this.isCurrentPlayer(playerId)) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        this.suggestedDiscardSuggestions(playerId).forEach(suggestion -> {
            String tileName = suggestion.getTile().name().toLowerCase(Locale.ROOT);
            if (!suggestions.contains(tileName)) {
                suggestions.add(tileName);
            }
        });
        return List.copyOf(suggestions);
    }

    @Override
    public List<RiichiDiscardSuggestion> suggestedDiscardSuggestions(UUID playerId) {
        RiichiPlayerState player = this.seatPlayer(playerId);
        if (player == null || !this.isCurrentPlayer(playerId)) {
            return List.of();
        }
        return List.copyOf(player.discardSuggestions());
    }
    @Override
    public RiichiRoundEngine asRiichiEngine() {
        return this.engine;
    }

    private RiichiPlayerState seatPlayer(UUID playerId) {
        return playerId == null ? null : this.engine.seatPlayer(playerId.toString());
    }
}
