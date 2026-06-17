package top.ellan.mahjong.error;

import java.util.Objects;
import java.util.logging.Level;

public abstract class MahjongException extends RuntimeException {
    private final MahjongErrorCode code;
    private final String publicMessage;
    private final Level logLevel;

    protected MahjongException(MahjongErrorCode code, String publicMessage, Level logLevel, Throwable cause) {
        super(Objects.requireNonNull(publicMessage, "publicMessage"), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.publicMessage = publicMessage;
        this.logLevel = Objects.requireNonNull(logLevel, "logLevel");
    }

    public MahjongErrorCode code() {
        return this.code;
    }

    public String publicMessage() {
        return this.publicMessage;
    }

    public Level logLevel() {
        return this.logLevel;
    }
}
