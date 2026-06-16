package top.ellan.mahjong.table.core;

import java.util.UUID;

public final class SichuanSessionAccess {
    private SichuanSessionAccess() {
    }

    public static boolean isExchangePhase(MahjongTableSession session, UUID playerId) {
        return session != null && session.isSichuanExchangePhase(playerId);
    }

    public static boolean chooseMissingSuit(MahjongTableSession session, UUID playerId, String suitToken) {
        return session != null && session.chooseSichuanMissingSuit(playerId, suitToken);
    }
}
