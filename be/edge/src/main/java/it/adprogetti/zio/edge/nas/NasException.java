package it.adprogetti.zio.edge.nas;

/** A refused or failed NAS operation. The code is short and never contains a path or file name. */
public class NasException extends RuntimeException {

    public enum Code {
        NOT_CONFIGURED, UNAVAILABLE, INVALID_PATH, MODE_FORBIDDEN, NO_PERMISSION, INSUFFICIENT_SPACE, COLLISION,
        HISTORICAL_TENDER, NOT_FOUND, IS_DIRECTORY, TOO_MANY_ENTRIES, WRITE_FAILED, INTEGRITY_FAILED
    }

    private final Code code;

    public NasException(Code code) {
        super(code.name(), null, false, false);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
