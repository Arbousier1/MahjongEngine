package top.ellan.mahjong.model;

import java.util.Locale;

public enum MahjongVariant {
    RIICHI,
    GB,
    SICHUAN;

    public String translationKey() {
        return "rule.variant." + this.name().toLowerCase(Locale.ROOT);
    }
}
