package it.adprogetti.zio.edge.command;

import java.util.regex.Pattern;

/** A handler failure with a short code that is safe to send to the Hub (never free text). */
public class CommandFailure extends Exception {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z_]{2,31}");
    private final String code;

    public CommandFailure(String code) {
        super(code);
        this.code = CODE.matcher(code).matches() ? code : "EXECUTION_ERROR";
    }

    public String code() {
        return code;
    }
}
