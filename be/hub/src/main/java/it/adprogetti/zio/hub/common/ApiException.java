package it.adprogetti.zio.hub.common;

import org.springframework.http.HttpStatus;

/** Expected failure mapped to an RFC 9457 problem. The message is safe to show to clients. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
