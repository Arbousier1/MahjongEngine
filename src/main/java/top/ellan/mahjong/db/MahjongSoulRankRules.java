package top.ellan.mahjong.db;

import top.ellan.mahjong.riichi.model.MahjongRule;
import java.util.List;
import java.util.Locale;

public final class MahjongSoulRankRules {
    private static final List<Stage> STAGES = List.of(
        new Stage(Tier.NOVICE, 1, 0, 20, 0, 0),
        new Stage(Tier.NOVICE, 2, 80, 80, 0, 0),
        new Stage(Tier.NOVICE, 3, 200, 200, 0, 0),
        new Stage(Tier.ADEPT, 1, 300, 600, 10, 20),
        new Stage(Tier.ADEPT, 2, 400, 800, 20, 40),
        new Stage(Tier.ADEPT, 3, 500, 1000, 30, 60),
        new Stage(Tier.EXPERT, 1, 600, 1200, 40, 80),
        new Stage(Tier.EXPERT, 2, 700, 1400, 50, 100),
        new Stage(Tier.EXPERT, 3, 1000, 2000, 60, 120),
        new Stage(Tier.MASTER, 1, 1400, 2800, 80, 165),
        new Stage(Tier.MASTER, 2, 1600, 3200, 90, 180),
        new Stage(Tier.MASTER, 3, 1800, 3600, 100, 195),
        new Stage(Tier.SAINT, 1, 2000, 4000, 110, 210),
        new Stage(Tier.SAINT, 2, 3000, 6000, 120, 225),
        new Stage(Tier.SAINT, 3, 4500, 9000, 130, 240)
    );
    private static final int CELESTIAL_START_POINTS = 100;
    private static final int CELESTIAL_UPGRADE_POINTS = 200;

    private MahjongSoulRankRules() {
    }

    public static MatchLength matchLength(MahjongRule.GameLength length) {
        return length == MahjongRule.GameLength.EAST ? MatchLength.EAST : MatchLength.SOUTH;
    }

    public static Room roomFor(MatchLength length, String eastRoom, String southRoom) {
        return Room.parse(length == MatchLength.EAST ? eastRoom : southRoom);
    }

    public static RankedMatchResult applyMatch(
        MahjongSoulRankProfile profile,
        Room room,
        MatchLength length,
        int place,
        int rawScore,
        boolean allPlayersCelestial
    ) {
        if (profile.isCelestial()) {
            return applyCelestial(profile, length, place, allPlayersCelestial);
        }
        Stage stage = stageFor(profile.tier(), profile.level());
        int pointsChange = (int) Math.ceil(((rawScore - 25000) / 1000.0D) + uma(place) + rankPoints(room, stage, length, place));
        MahjongSoulRankProfile updated = applyStandardTransition(profile, stage, pointsChange, place);
        return new RankedMatchResult(profile, updated, pointsChange, room, length, place, rawScore);
    }

    public static int nextThreshold(MahjongSoulRankProfile profile) {
        if (profile.isCelestial()) {
            return CELESTIAL_UPGRADE_POINTS;
        }
        return stageFor(profile.tier(), profile.level()).upgradePoints();
    }

    public static String formatPoints(MahjongSoulRankProfile profile) {
        if (profile.isCelestial()) {
            return String.format(Locale.ROOT, "%.1f/20.0 SP", profile.rankPoints() / 10.0D);
        }
        return profile.rankPoints() + "/" + nextThreshold(profile);
    }

    private static RankedMatchResult applyCelestial(
        MahjongSoulRankProfile profile,
        MatchLength length,
        int place,
        boolean allPlayersCelestial
    ) {
        int delta = switch (length) {
            case EAST -> switch (place) {
                case 1 -> 3;
                case 2 -> 1;
                case 3 -> -1;
                default -> -3;
            };
            case SOUTH -> switch (place) {
                case 1 -> 5;
                case 2 -> 2;
                case 3 -> -2;
                default -> -5;
            };
        };
        if (allPlayersCelestial) {
            delta *= 2;
        }
        int level = Math.max(1, profile.level());
        int points = profile.rankPoints() + delta;
        while (points >= CELESTIAL_UPGRADE_POINTS) {
            points = CELESTIAL_START_POINTS + (points - CELESTIAL_UPGRADE_POINTS);
            level++;
        }
        while (points <= 0 && level > 1) {
            points = CELESTIAL_START_POINTS + points;
            level--;
        }
        MahjongSoulRankProfile updated;
        if (points <= 0 && level == 1) {
            updated = withPlacement(
                new MahjongSoulRankProfile(
                    profile.playerId(),
                    profile.displayName(),
                    Tier.SAINT,
                    3,
                    0,
                    profile.totalMatches(),
                    profile.firstPlaces(),
                    profile.secondPlaces(),
                    profile.thirdPlaces(),
                    profile.fourthPlaces()
                ),
                place
            );
        } else {
            updated = withPlacement(
                new MahjongSoulRankProfile(
                    profile.playerId(),
                    profile.displayName(),
                    Tier.CELESTIAL,
                    level,
                    points,
                    profile.totalMatches(),
                    profile.firstPlaces(),
                    profile.secondPlaces(),
                    profile.thirdPlaces(),
                    profile.fourthPlaces()
                ),
                place
            );
        }
        return new RankedMatchResult(profile, updated, delta, Room.THRONE, length, place, 0);
    }

    private static MahjongSoulRankProfile applyStandardTransition(MahjongSoulRankProfile profile, Stage stage, int pointsChange, int place) {
        int stageIndex = STAGES.indexOf(stage);
        int points = profile.rankPoints() + pointsChange;
        int level = profile.level();
        Tier tier = profile.tier();

        while (true) {
            Stage current = STAGES.get(stageIndex);
            if (points >= current.upgradePoints()) {
                if (stageIndex == STAGES.size() - 1) {
                    return withPlacement(
                        new MahjongSoulRankProfile(
                            profile.playerId(),
                            profile.displayName(),
                            Tier.CELESTIAL,
                            1,
                            CELESTIAL_START_POINTS,
                            profile.totalMatches(),
                            profile.firstPlaces(),
                            profile.secondPlaces(),
                            profile.thirdPlaces(),
                            profile.fourthPlaces()
                        ),
                        place
                    );
                }
                int overflow = points - current.upgradePoints();
                stageIndex++;
                Stage next = STAGES.get(stageIndex);
                tier = next.tier();
                level = next.level();
                points = next.initialPoints() + overflow;
                continue;
            }
            if (points < 0) {
                if (tier == Tier.NOVICE || tier == Tier.ADEPT && level == 1) {
                    points = 0;
                    break;
                }
                stageIndex--;
                Stage previous = STAGES.get(stageIndex);
                tier = previous.tier();
                level = previous.level();
                points = previous.upgradePoints() + points;
                continue;
            }
            break;
        }

        return withPlacement(
            new MahjongSoulRankProfile(
                profile.playerId(),
                profile.displayName(),
                tier,
                level,
                points,
                profile.totalMatches(),
                profile.firstPlaces(),
                profile.secondPlaces(),
                profile.thirdPlaces(),
                profile.fourthPlaces()
            ),
            place
        );
    }

    private static MahjongSoulRankProfile withPlacement(MahjongSoulRankProfile profile, int place) {
        return new MahjongSoulRankProfile(
            profile.playerId(),
            profile.displayName(),
            profile.tier(),
            profile.level(),
            profile.rankPoints(),
            profile.totalMatches() + 1,
            profile.firstPlaces() + (place == 1 ? 1 : 0),
            profile.secondPlaces() + (place == 2 ? 1 : 0),
            profile.thirdPlaces() + (place == 3 ? 1 : 0),
            profile.fourthPlaces() + (place == 4 ? 1 : 0)
        );
    }

    private static int uma(int place) {
        return switch (place) {
            case 1 -> 15;
            case 2 -> 5;
            case 3 -> -5;
            default -> -15;
        };
    }

    private static int rankPoints(Room room, Stage stage, MatchLength length, int place) {
        return switch (place) {
            case 1 -> room.first(length);
            case 2 -> room.second(length);
            case 3 -> 0;
            default -> -stage.penalty(length);
        };
    }

    private static Stage stageFor(Tier tier, int level) {
        return STAGES.stream()
            .filter(stage -> stage.tier() == tier && stage.level() == level)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported rank stage: " + tier + " " + level));
    }

    public enum MatchLength {
        EAST,
        SOUTH
    }

    public enum Tier {
        NOVICE,
        ADEPT,
        EXPERT,
        MASTER,
        SAINT,
        CELESTIAL
    }

    public enum Room {
        BRONZE(10, 20, 5, 10),
        SILVER(20, 40, 10, 20),
        GOLD(40, 80, 20, 40),
        JADE(55, 110, 30, 55),
        THRONE(60, 120, 30, 60);

        private final int eastFirst;
        private final int southFirst;
        private final int eastSecond;
        private final int southSecond;

        Room(int eastFirst, int southFirst, int eastSecond, int southSecond) {
            this.eastFirst = eastFirst;
            this.southFirst = southFirst;
            this.eastSecond = eastSecond;
            this.southSecond = southSecond;
        }

        public int first(MatchLength length) {
            return length == MatchLength.EAST ? this.eastFirst : this.southFirst;
        }

        public int second(MatchLength length) {
            return length == MatchLength.EAST ? this.eastSecond : this.southSecond;
        }

        public static Room parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return SILVER;
            }
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record RankedMatchResult(
        MahjongSoulRankProfile previous,
        MahjongSoulRankProfile updated,
        int rankPointChange,
        Room room,
        MatchLength length,
        int place,
        int rawScore
    ) {
    }

    private record Stage(Tier tier, int level, int initialPoints, int upgradePoints, int eastPenalty, int southPenalty) {
        private int penalty(MatchLength length) {
            return length == MatchLength.EAST ? this.eastPenalty : this.southPenalty;
        }
    }
}

