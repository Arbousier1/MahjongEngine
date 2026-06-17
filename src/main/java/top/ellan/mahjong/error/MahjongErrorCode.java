package top.ellan.mahjong.error;

public enum MahjongErrorCode {
    SHANTEN_ANALYSIS_FAILED("Shanten analysis failed."),
    SHANTEN_STRATEGY_UNAVAILABLE("Shanten strategy is unavailable."),
    GB_NATIVE_BRIDGE_UNAVAILABLE("GB Mahjong native bridge is unavailable."),
    GB_NATIVE_FAN_EVALUATION_FAILED("GB Mahjong native fan evaluation failed."),
    GB_NATIVE_TING_EVALUATION_FAILED("GB Mahjong native ting evaluation failed."),
    GB_NATIVE_WIN_EVALUATION_FAILED("GB Mahjong native win evaluation failed."),
    DATABASE_OPERATION_FAILED("Database operation failed.");

    private final String publicMessage;

    MahjongErrorCode(String publicMessage) {
        this.publicMessage = publicMessage;
    }

    public String publicMessage() {
        return this.publicMessage;
    }
}
