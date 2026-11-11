package it.adprogetti.zio.edge.tenders;

/** TED did not answer usefully. Only a short code is kept: no query, no response, no URL. */
class TedUnavailableException extends Exception {

    private final String code;
    private final boolean retryable;

    TedUnavailableException(String code, boolean retryable) {
        super(code);
        this.code = code;
        this.retryable = retryable;
    }

    String code() {
        return code;
    }

    boolean retryable() {
        return retryable;
    }
}
