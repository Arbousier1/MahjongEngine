package top.ellan.mahjong.table.presentation;

import java.util.Objects;

public final class SettlementFeedbackGate {
    private SettlementFeedbackGate() {
    }

    public static boolean isNewSettlement(String settlementFingerprint, String lastSettlementFingerprint) {
        return !Objects.toString(settlementFingerprint, "").isBlank()
            && !Objects.equals(settlementFingerprint, lastSettlementFingerprint);
    }
}


