package it.adprogetti.zio.contracts.payload;

/** A command payload that is malformed, has unknown fields or fails validation. */
public class InvalidPayloadException extends RuntimeException {

    public InvalidPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
