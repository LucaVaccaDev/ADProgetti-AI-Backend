package it.adprogetti.zio.edge.docs;

/** A PDF that cannot be turned into text. The code is short and never contains a file name or content. */
class ExtractionException extends Exception {

    private final String code;

    ExtractionException(String code, Throwable cause) {
        super(code, cause, false, false);
        this.code = code;
    }

    String code() {
        return code;
    }
}
