package top.ellan.mahjong.riichi;

import top.ellan.mahjong.riichi.model.MahjongTile;
import kotlin.Pair;

public final class ReactionResponses {
    public static final ReactionResponse RON = new ReactionResponse(ReactionType.RON, null);
    public static final ReactionResponse PON = new ReactionResponse(ReactionType.PON, null);
    public static final ReactionResponse MINKAN = new ReactionResponse(ReactionType.MINKAN, null);
    public static final ReactionResponse SKIP = new ReactionResponse(ReactionType.SKIP, null);

    private ReactionResponses() {
    }

    public static ReactionResponse chii(MahjongTile first, MahjongTile second) {
        return new ReactionResponse(ReactionType.CHII, new Pair<>(first, second));
    }

    public static ReactionResponse chii(Pair<MahjongTile, MahjongTile> pair) {
        return new ReactionResponse(ReactionType.CHII, pair);
    }

    public static ReactionResponse of(ReactionType type) {
        return switch (type) {
            case RON -> RON;
            case PON -> PON;
            case MINKAN -> MINKAN;
            case SKIP -> SKIP;
            case CHII -> new ReactionResponse(ReactionType.CHII, null);
        };
    }

    public static ReactionResponse of(ReactionType type, Pair<MahjongTile, MahjongTile> pair) {
        return type == ReactionType.CHII ? chii(pair) : of(type);
    }
}
