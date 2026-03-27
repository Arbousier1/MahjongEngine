package top.ellan.mahjong.table.core;

import java.util.Locale;

public enum MahjongVariant {
    RIICHI,
    GB,
    SICHUAN;

    public String translationKey() {
        return "rule.variant." + this.name().toLowerCase(Locale.ROOT);
    }
}

