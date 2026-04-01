package top.ellan.mahjong.error;

import java.util.logging.Level;

public final class MahjongBusinessException extends MahjongException {
    public MahjongBusinessException(MahjongErrorCode code, String publicMessage, Throwable cause) {
        super(code, publicMessage, Level.FINE, cause);
    }
}
