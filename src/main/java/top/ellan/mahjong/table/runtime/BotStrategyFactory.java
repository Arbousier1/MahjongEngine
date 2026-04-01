package top.ellan.mahjong.table.runtime;

import top.ellan.mahjong.table.core.MahjongVariant;
import java.util.EnumMap;
import java.util.Map;

final class BotStrategyFactory {
    private static final BotStrategy DEFAULT_STRATEGY = new RiichiBotStrategy();
    private static final Map<MahjongVariant, BotStrategy> STRATEGIES = strategies();

    private BotStrategyFactory() {
    }

    static BotStrategy forVariant(MahjongVariant variant) {
        return STRATEGIES.getOrDefault(variant, DEFAULT_STRATEGY);
    }

    private static Map<MahjongVariant, BotStrategy> strategies() {
        EnumMap<MahjongVariant, BotStrategy> map = new EnumMap<>(MahjongVariant.class);
        map.put(MahjongVariant.RIICHI, DEFAULT_STRATEGY);
        BotStrategy gbStrategy = new GbBotStrategy();
        map.put(MahjongVariant.GB, gbStrategy);
        map.put(MahjongVariant.SICHUAN, gbStrategy);
        return Map.copyOf(map);
    }
}
