package it.adprogetti.zio.edge.jobs;

import java.util.regex.Pattern;

/** A job failure with a short code safe to store and show (never free text). Permanent ones are not retried. */
public class JobFailure extends Exception {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z_]{2,31}");
    private final String code;
    private final boolean retryable;

    public JobFailure(String code, boolean retryable) {
        super(code);
        this.code = CODE.matcher(code).matches() ? code : "EXECUTION_ERROR";
        this.retryable = retryable;
    }

    public static JobFailure transientFailure(String code) {
        return new JobFailure(code, true);
    }

    public static JobFailure permanent(String code) {
        return new JobFailure(code, false);
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
