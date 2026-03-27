package top.ellan.mahjong.table.core;

import top.ellan.mahjong.riichi.model.MahjongRule;
import java.util.List;
import java.util.Locale;

final class SessionRuleCoordinator {
    private final MahjongTableSession session;

    SessionRuleCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    boolean applyRulePreset(String rawValue) {
        SessionRulePresetResolver.Preset preset = SessionRulePresetResolver.resolve(rawValue);
        if (preset == null) {
            return false;
        }
        this.session.setConfiguredVariantInternal(preset.variant());
        this.session.setConfiguredRuleInternal(copyRule(preset.rule()));
        this.session.persistRoomMetadataIfNeededInternal();
        return true;
    }

    boolean setRuleOption(String key, String rawValue) {
        if (this.session.isStarted()) {
            return false;
        }
        try {
            switch (key.toLowerCase(Locale.ROOT)) {
                case "preset", "mode" -> {
                    if (!this.applyRulePreset(rawValue)) {
                        return false;
                    }
                }
                case "variant", "ruleset" -> {
                    MahjongVariant variant = MahjongVariant.valueOf(rawValue.toUpperCase(Locale.ROOT));
                    this.session.setConfiguredVariantInternal(variant);
                    this.session.setConfiguredRuleInternal(SessionRulePresetResolver.defaultRuleFor(variant));
                }
                case "length" -> this.session.configuredRuleInternal().setLength(MahjongRule.GameLength.valueOf(rawValue.toUpperCase(Locale.ROOT)));
                case "thinkingtime", "thinking" -> this.session.configuredRuleInternal().setThinkingTime(MahjongRule.ThinkingTime.valueOf(rawValue.toUpperCase(Locale.ROOT)));
                case "minimumhan", "minhan" -> this.session.configuredRuleInternal().setMinimumHan(MahjongRule.MinimumHan.valueOf(rawValue.toUpperCase(Locale.ROOT)));
                case "spectate" -> this.session.configuredRuleInternal().setSpectate(Boolean.parseBoolean(rawValue));
                case "redfive" -> this.session.configuredRuleInternal().setRedFive(MahjongRule.RedFive.valueOf(rawValue.toUpperCase(Locale.ROOT)));
                case "opentanyao" -> this.session.configuredRuleInternal().setOpenTanyao(Boolean.parseBoolean(rawValue));
                case "localyaku" -> this.session.configuredRuleInternal().setLocalYaku(Boolean.parseBoolean(rawValue));
                case "startingpoints", "startpoints" -> this.session.configuredRuleInternal().setStartingPoints(Integer.parseInt(rawValue));
                case "minpointstowin", "goal" -> this.session.configuredRuleInternal().setMinPointsToWin(Integer.parseInt(rawValue));
                default -> {
                    return false;
                }
            }
            this.session.render();
            this.session.persistRoomMetadataIfNeededInternal();
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    List<String> ruleKeys() {
        return List.of("preset", "mode", "variant", "ruleset", "length", "thinkingTime", "minimumHan", "spectate", "redFive", "openTanyao", "localYaku", "startingPoints", "minPointsToWin");
    }

    List<String> ruleValues(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "preset", "mode" -> List.of("MAJSOUL_TONPUU", "MAJSOUL_HANCHAN", "GB", "SICHUAN");
            case "variant", "ruleset" -> List.of("RIICHI", "GB", "SICHUAN");
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
}

