package top.ellan.mahjong.table.core;

import top.ellan.mahjong.riichi.model.MahjongRule;
import java.util.Locale;

final class SessionRulePresetResolver {
    private SessionRulePresetResolver() {
    }

    static Preset resolve(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (rawValue.toUpperCase(Locale.ROOT)) {
            case "MAJSOUL_TONPUU", "TONPUU", "TONPUSEN", "EAST" ->
                new Preset(MahjongVariant.RIICHI, majsoulRule(MahjongRule.GameLength.EAST));
            case "MAJSOUL_HANCHAN", "HANCHAN", "TWO_WIND", "SOUTH" ->
                new Preset(MahjongVariant.RIICHI, majsoulRule(MahjongRule.GameLength.TWO_WIND));
            case "GB", "GUOBIAO", "ZHONGGUO", "CHINESE_OFFICIAL" ->
                new Preset(MahjongVariant.GB, gbRule());
            default -> null;
        };
    }

    static MahjongRule defaultRuleFor(MahjongVariant variant) {
        return variant == MahjongVariant.GB ? gbRule() : majsoulRule(MahjongRule.GameLength.TWO_WIND);
    }

    static MahjongRule majsoulRule(MahjongRule.GameLength length) {
        return new MahjongRule(
            length,
            MahjongRule.ThinkingTime.NORMAL,
            25000,
            30000,
            MahjongRule.MinimumHan.ONE,
            true,
            MahjongRule.RedFive.THREE,
            true,
            false,
            MahjongRule.RonMode.MULTI_RON,
            MahjongRule.RiichiProfile.MAJSOUL
        );
    }

    static MahjongRule gbRule() {
        return new MahjongRule(
            MahjongRule.GameLength.TWO_WIND,
            MahjongRule.ThinkingTime.NORMAL,
            25000,
            30000,
            MahjongRule.MinimumHan.ONE,
            true,
            MahjongRule.RedFive.NONE,
            false,
            false,
            MahjongRule.RonMode.MULTI_RON,
            MahjongRule.RiichiProfile.MAJSOUL
        );
    }

    record Preset(MahjongVariant variant, MahjongRule rule) {
    }
}

