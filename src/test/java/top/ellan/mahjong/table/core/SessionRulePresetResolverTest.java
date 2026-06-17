package top.ellan.mahjong.table.core;

import top.ellan.mahjong.model.MahjongVariant;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.riichi.model.MahjongRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRulePresetResolverTest {
    @Test
    void majsoulHanchanPresetMatchesPrimaryGameplay() {
        SessionRulePresetResolver.Preset preset = SessionRulePresetResolver.resolve("MAJSOUL_HANCHAN");

        assertNotNull(preset);
        assertEquals(MahjongVariant.RIICHI, preset.variant());
        MahjongRule rule = preset.rule();
        assertEquals(MahjongRule.GameLength.TWO_WIND, rule.getLength());
        assertEquals(25000, rule.getStartingPoints());
        assertEquals(30000, rule.getMinPointsToWin());
        assertEquals(MahjongRule.MinimumHan.ONE, rule.getMinimumHan());
        assertEquals(MahjongRule.RedFive.THREE, rule.getRedFive());
        assertTrue(rule.getOpenTanyao());
        assertEquals(MahjongRule.RonMode.MULTI_RON, rule.getRonMode());
        assertEquals(MahjongRule.RiichiProfile.MAJSOUL, rule.getRiichiProfile());
    }

    @Test
    void majsoulTonpuuPresetUsesEastGameWithSameMajsoulRules() {
        SessionRulePresetResolver.Preset preset = SessionRulePresetResolver.resolve("MAJSOUL_TONPUU");

        assertNotNull(preset);
        assertEquals(MahjongVariant.RIICHI, preset.variant());
        MahjongRule rule = preset.rule();
        assertEquals(MahjongRule.GameLength.EAST, rule.getLength());
        assertEquals(MahjongRule.RedFive.THREE, rule.getRedFive());
        assertTrue(rule.getOpenTanyao());
        assertEquals(MahjongRule.RonMode.MULTI_RON, rule.getRonMode());
        assertEquals(MahjongRule.RiichiProfile.MAJSOUL, rule.getRiichiProfile());
    }

    @Test
    void defaultRiichiRuleUsesMajsoulHanchan() {
        MahjongRule rule = SessionRulePresetResolver.defaultRuleFor(MahjongVariant.RIICHI);

        assertEquals(MahjongRule.GameLength.TWO_WIND, rule.getLength());
        assertEquals(MahjongRule.RedFive.THREE, rule.getRedFive());
        assertTrue(rule.getOpenTanyao());
        assertEquals(MahjongRule.RonMode.MULTI_RON, rule.getRonMode());
        assertEquals(MahjongRule.RiichiProfile.MAJSOUL, rule.getRiichiProfile());
    }
}