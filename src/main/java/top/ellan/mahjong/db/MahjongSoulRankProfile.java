package top.ellan.mahjong.db;

import java.util.UUID;

public record MahjongSoulRankProfile(
    UUID playerId,
    String displayName,
    MahjongSoulRankRules.Tier tier,
    int level,
    int rankPoints,
    int totalMatches,
    int firstPlaces,
    int secondPlaces,
    int thirdPlaces,
    int fourthPlaces
) {
    public static MahjongSoulRankProfile defaultProfile(UUID playerId, String displayName) {
        return new MahjongSoulRankProfile(
            playerId,
            displayName,
            MahjongSoulRankRules.Tier.NOVICE,
            1,
            0,
            0,
            0,
            0,
            0,
            0
        );
    }

    public boolean isCelestial() {
        return this.tier == MahjongSoulRankRules.Tier.CELESTIAL;
    }
}

