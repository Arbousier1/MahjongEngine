package top.ellan.mahjong.error;

import java.util.logging.Level;

public final class MahjongInfrastructureException extends MahjongException {
    public MahjongInfrastructureException(MahjongErrorCode code, String publicMessage, Throwable cause) {
        super(code, publicMessage, Level.WARNING, cause);
    }
}
