package top.ellan.mahjong.table.runtime;

import top.ellan.mahjong.table.core.MahjongTableSession;

public final class BotActionScheduler {
    private BotActionScheduler() {
    }

    public static void schedule(MahjongTableSession session) {
        BotStrategyFactory.forVariant(session.currentVariant()).schedule(session);
    }
}
