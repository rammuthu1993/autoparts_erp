package com.autoparts.erp.common.exception;

/**
 * Dedicated business validation exception representing a client-caused request error.
 * Handled by {@link GlobalExceptionHandler} to return HTTP 400 Bad Request with
 * a client-safe error message.
 *
 * <p>Unlike generic {@link IllegalArgumentException}, this exception explicitly indicates
 * an invalid input from the client rather than an unexpected internal invariant failure.</p>
 */
public class ClientValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ClientValidationException(String message) {
        super(message);
    }

    public ClientValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClientValidationException(Throwable cause) {
        super(cause);
    }
}
