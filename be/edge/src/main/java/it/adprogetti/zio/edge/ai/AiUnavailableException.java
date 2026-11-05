package it.adprogetti.zio.edge.ai;

/** The local AI service cannot be reached or failed on its side (network, timeout, 5xx). */
public class AiUnavailableException extends RuntimeException {

    private final String code;

    public AiUnavailableException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
