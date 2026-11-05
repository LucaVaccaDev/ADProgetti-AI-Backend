package it.adprogetti.zio.edge.ai;

/** The AI service refused the request (for example a model that is not installed). */
public class AiRequestException extends RuntimeException {

    private final String code;

    public AiRequestException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
