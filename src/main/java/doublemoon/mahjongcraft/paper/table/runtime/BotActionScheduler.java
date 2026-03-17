package doublemoon.mahjongcraft.paper.table.runtime;

import doublemoon.mahjongcraft.paper.riichi.ReactionOptions;
import doublemoon.mahjongcraft.paper.riichi.ReactionResponse;
import doublemoon.mahjongcraft.paper.riichi.ReactionType;
import doublemoon.mahjongcraft.paper.riichi.RiichiPlayerState;
import doublemoon.mahjongcraft.paper.riichi.RiichiRoundEngine;
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile;
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance;
import doublemoon.mahjongcraft.paper.table.core.MahjongVariant;
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession;
import java.util.Objects;
import java.util.UUID;
import kotlin.Pair;
import org.bukkit.Bukkit;

public final class BotActionScheduler {
    private BotActionScheduler() {
    }

    public static void schedule(MahjongTableSession session) {
        if (session.currentVariant() == MahjongVariant.GB) {
            scheduleGb(session);
            return;
        }
        RiichiRoundEngine engine = session.engine();
        if (engine == null || !engine.getStarted()) {
            return;
        }

        UUID pendingBot = findPendingBotReaction(session, engine);
        if (pendingBot != null) {
            session.setBotTask(Bukkit.getScheduler().runTaskLater(session.plugin(), () -> handleBotReaction(session, pendingBot), 20L));
            return;
        }

        UUID current = UUID.fromString(engine.getCurrentPlayer().getUuid());
        if (session.isBot(current)) {
            session.setBotTask(Bukkit.getScheduler().runTaskLater(session.plugin(), () -> handleBotTurn(session, current), 20L));
        }
    }

    private static void scheduleGb(MahjongTableSession session) {
        if (!session.isStarted()) {
            return;
        }
        for (UUID playerId : session.players()) {
            if (!session.isBot(playerId) || session.availableReactions(playerId) == null) {
                continue;
            }
            session.setBotTask(Bukkit.getScheduler().runTaskLater(session.plugin(), () -> handleGbReaction(session, playerId), 20L));
            return;
        }
        UUID current = session.playerAt(session.currentSeat());
        if (current != null && session.isBot(current)) {
            session.setBotTask(Bukkit.getScheduler().runTaskLater(session.plugin(), () -> handleGbTurn(session, current), 20L));
        }
    }

    private static UUID findPendingBotReaction(MahjongTableSession session, RiichiRoundEngine engine) {
        if (engine.getPendingReaction() == null) {
            return null;
        }
        for (String uuid : engine.getPendingReaction().getOptions().keySet()) {
            UUID playerId = UUID.fromString(uuid);
            if (session.isBot(playerId) && !engine.getPendingReaction().getResponses().containsKey(uuid)) {
                return playerId;
            }
        }
        return null;
    }

    private static void handleBotReaction(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        RiichiRoundEngine engine = session.engine();
        if (engine == null) {
            return;
        }
        ReactionOptions options = engine.availableReactions(playerId.toString());
        if (options == null) {
            return;
        }
        if (options.getCanRon()) {
            session.react(playerId, new ReactionResponse(ReactionType.RON, null));
            return;
        }
        if (options.getCanPon()) {
            session.react(playerId, new ReactionResponse(ReactionType.PON, null));
            return;
        }
        if (options.getCanMinkan()) {
            session.react(playerId, new ReactionResponse(ReactionType.MINKAN, null));
            return;
        }
        if (!options.getChiiPairs().isEmpty()) {
            Pair<MahjongTile, MahjongTile> pair = options.getChiiPairs().getFirst();
            session.react(playerId, new ReactionResponse(ReactionType.CHII, pair));
            return;
        }
        session.react(playerId, new ReactionResponse(ReactionType.SKIP, null));
    }

    private static void handleGbReaction(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        var options = session.availableReactions(playerId);
        if (options == null) {
            return;
        }
        if (options.getCanRon()) {
            session.react(playerId, new ReactionResponse(ReactionType.RON, null));
            return;
        }
        if (options.getCanMinkan()) {
            session.react(playerId, new ReactionResponse(ReactionType.MINKAN, null));
            return;
        }
        if (options.getCanPon()) {
            session.react(playerId, new ReactionResponse(ReactionType.PON, null));
            return;
        }
        if (!options.getChiiPairs().isEmpty()) {
            Pair<MahjongTile, MahjongTile> pair = options.getChiiPairs().getFirst();
            session.react(playerId, new ReactionResponse(ReactionType.CHII, pair));
            return;
        }
        session.react(playerId, new ReactionResponse(ReactionType.SKIP, null));
    }

    private static void handleBotTurn(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        RiichiRoundEngine engine = session.engine();
        if (engine == null || !engine.getStarted() || engine.getPendingReaction() != null) {
            return;
        }
        if (!Objects.equals(engine.getCurrentPlayer().getUuid(), playerId.toString())) {
            return;
        }
        RiichiPlayerState player = engine.seatPlayer(playerId.toString());
        if (player == null) {
            return;
        }

        if (session.declareTsumo(playerId)) {
            return;
        }
        if (player.getCanAnkan() && !player.getTilesCanAnkan().isEmpty()) {
            if (session.declareKan(playerId, player.getTilesCanAnkan().iterator().next().getMahjongTile().name())) {
                return;
            }
        }
        if (player.getCanKakan()) {
            for (TileInstance tile : player.getHands()) {
                boolean matchFuuro = player.getFuuroList().stream()
                    .anyMatch(fuuro -> fuuro.getTileInstances().stream().anyMatch(existing -> existing.getCode() == tile.getCode()));
                if (matchFuuro && session.declareKan(playerId, tile.getMahjongTile().name())) {
                    return;
                }
            }
        }
        if (engine.canKyuushuKyuuhai(playerId.toString()) && player.getNumbersOfYaochuuhaiTypes() >= 11) {
            if (session.declareKyuushuKyuuhai(playerId)) {
                return;
            }
        }
        if (player.isRiichiable() && !player.getTilePairsForRiichi().isEmpty()) {
            MahjongTile discardTile = player.getTilePairsForRiichi().getFirst().getFirst();
            int discardIndex = findDiscardIndex(player, discardTile);
            if (discardIndex >= 0 && session.declareRiichi(playerId, discardIndex)) {
                return;
            }
        }
        session.discard(playerId, chooseBotDiscardIndex(player));
    }

    private static void handleGbTurn(MahjongTableSession session, UUID playerId) {
        session.setBotTask(null);
        if (!session.isStarted() || session.hasPendingReaction() || !Objects.equals(session.playerAt(session.currentSeat()), playerId)) {
            return;
        }
        if (session.gbCanWinByTsumo(playerId) && session.declareTsumo(playerId)) {
            return;
        }
        if (session.canDeclareKan(playerId)) {
            for (String suggestion : session.suggestedKanTiles(playerId)) {
                if (session.declareKan(playerId, suggestion)) {
                    return;
                }
            }
        }
        var hand = session.hand(playerId);
        if (hand.isEmpty()) {
            return;
        }
        session.discard(playerId, chooseGbDiscardIndex(hand));
    }

    private static int chooseGbDiscardIndex(java.util.List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand) {
        int bestIndex = hand.size() - 1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < hand.size(); i++) {
            int score = gbDiscardScore(hand, hand.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int gbDiscardScore(java.util.List<doublemoon.mahjongcraft.paper.model.MahjongTile> hand, doublemoon.mahjongcraft.paper.model.MahjongTile tile) {
        int duplicates = 0;
        boolean hasPrev = false;
        boolean hasNext = false;
        boolean hasPrevPrev = false;
        boolean hasNextNext = false;
        for (doublemoon.mahjongcraft.paper.model.MahjongTile candidate : hand) {
            if (candidate == tile) {
                duplicates++;
                continue;
            }
            if (isHonor(tile) || isHonor(candidate) || candidate.name().charAt(0) != tile.name().charAt(0)) {
                continue;
            }
            int delta = tileNumber(candidate) - tileNumber(tile);
            if (delta == -2) hasPrevPrev = true;
            if (delta == -1) hasPrev = true;
            if (delta == 1) hasNext = true;
            if (delta == 2) hasNextNext = true;
        }
        int score = 0;
        if (isHonor(tile) || tileNumber(tile) == 1 || tileNumber(tile) == 9) score += 3;
        if (duplicates >= 2) score -= 4;
        if (hasPrev) score -= 2;
        if (hasNext) score -= 2;
        if (hasPrevPrev) score -= 1;
        if (hasNextNext) score -= 1;
        return score;
    }

    private static boolean isHonor(doublemoon.mahjongcraft.paper.model.MahjongTile tile) {
        return tile.ordinal() >= doublemoon.mahjongcraft.paper.model.MahjongTile.EAST.ordinal()
            && tile.ordinal() <= doublemoon.mahjongcraft.paper.model.MahjongTile.RED_DRAGON.ordinal();
    }

    private static int tileNumber(doublemoon.mahjongcraft.paper.model.MahjongTile tile) {
        return isHonor(tile) ? 0 : Integer.parseInt(tile.name().substring(1, 2));
    }

    private static int findDiscardIndex(RiichiPlayerState player, MahjongTile tile) {
        for (int i = player.getHands().size() - 1; i >= 0; i--) {
            if (player.getHands().get(i).getMahjongTile() == tile) {
                return i;
            }
        }
        return -1;
    }

    private static int chooseBotDiscardIndex(RiichiPlayerState player) {
        if (player.getRiichi() || player.getDoubleRiichi()) {
            return player.getHands().size() - 1;
        }

        int bestIndex = player.getHands().size() - 1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < player.getHands().size(); i++) {
            MahjongTile tile = player.getHands().get(i).getMahjongTile();
            int score = discardScore(player, tile);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int discardScore(RiichiPlayerState player, MahjongTile tile) {
        int duplicates = 0;
        boolean hasPrev = false;
        boolean hasNext = false;
        boolean hasPrevPrev = false;
        boolean hasNextNext = false;
        for (TileInstance handTile : player.getHands()) {
            MahjongTile other = handTile.getMahjongTile();
            if (other == tile) {
                duplicates++;
                continue;
            }
            if (isHonorTile(tile)) {
                continue;
            }
            if (tileSuit(other) != tileSuit(tile)) {
                continue;
            }
            if (other == tile.getPreviousTile()) hasPrev = true;
            if (other == tile.getNextTile()) hasNext = true;
            if (other == tile.getPreviousTile().getPreviousTile()) hasPrevPrev = true;
            if (other == tile.getNextTile().getNextTile()) hasNextNext = true;
        }
        int score = 0;
        if (tile.isRed()) score -= 5;
        if (isHonorTile(tile) || tileNumber(tile) == 1 || tileNumber(tile) == 9) score += 3;
        if (duplicates >= 2) score -= 4;
        if (hasPrev) score -= 2;
        if (hasNext) score -= 2;
        if (hasPrevPrev) score -= 1;
        if (hasNextNext) score -= 1;
        return score;
    }

    private static boolean isHonorTile(MahjongTile tile) {
        MahjongTile base = tile.getBaseTile();
        return base.ordinal() >= MahjongTile.EAST.ordinal() && base.ordinal() <= MahjongTile.RED_DRAGON.ordinal();
    }

    private static char tileSuit(MahjongTile tile) {
        return tile.getBaseTile().name().charAt(0);
    }

    private static int tileNumber(MahjongTile tile) {
        if (isHonorTile(tile)) {
            return 0;
        }
        String name = tile.getBaseTile().name();
        return Character.digit(name.charAt(1), 10);
    }
}

