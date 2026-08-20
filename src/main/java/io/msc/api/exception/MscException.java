package io.msc.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown by controllers to signal a known HTTP error to the global
 * exception handler. Keeps controllers free of {@code @ExceptionHandler}
 * boilerplate.
 */
public class MscException extends RuntimeException {

    private final HttpStatus status;

    public MscException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}