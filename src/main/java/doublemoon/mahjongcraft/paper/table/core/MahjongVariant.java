package doublemoon.mahjongcraft.paper.table.core;

import java.util.Locale;

public enum MahjongVariant {
    RIICHI,
    GB;

    public String translationKey() {
        return "rule.variant." + this.name().toLowerCase(Locale.ROOT);
    }
}
