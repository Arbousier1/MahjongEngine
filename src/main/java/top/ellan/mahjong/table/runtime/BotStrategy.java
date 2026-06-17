package top.ellan.mahjong.table.runtime;

import top.ellan.mahjong.table.core.MahjongTableSession;

interface BotStrategy {
    void schedule(MahjongTableSession session);
}
