package top.ellan.mahjong.table.runtime;

import top.ellan.mahjong.table.core.MahjongTableSession;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BotActionScheduler {
    private static final Logger LOGGER = Logger.getLogger(BotActionScheduler.class.getName());

    private BotActionScheduler() {
    }

    public static void schedule(MahjongTableSession session) {
        // Bot scheduling is the only thing keeping a 4-bot match advancing.
        // If a strategy throws (e.g. an unexpected null in the round engine,
        // a transient mahjongutils analysis failure that bubbles past the
        // shanten fallbacks, or a Folia scheduler edge case), swallowing the
        // exception here is far better than letting the entire table freeze
        // because the surrounding completeRenderFlush() callsite is downstream
        // of the throw.
        try {
            BotStrategyFactory.forVariant(session.currentVariant()).schedule(session);
        } catch (RuntimeException exception) {
            LOGGER.log(
                Level.WARNING,
                "Bot strategy scheduling failed for table " + session.id() + "; the table may need /mahjong reload to recover",
                exception
            );
        }
    }
}
