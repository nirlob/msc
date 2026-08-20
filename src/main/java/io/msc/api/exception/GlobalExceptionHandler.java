package io.msc.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps {@link MscException} to the corresponding HTTP status with a small
 * JSON body. Other exceptions bubble up as 500s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MscException.class)
    public ResponseEntity<Map<String, Object>> handle(MscException e) {
        return ResponseEntity.status(e.status()).body(
            Map.of("error", e.status().getReasonPhrase(),
                   "message", e.getMessage()));
    }
}