package io.linjq.exceptions;

/**
 * Exception thrown when a LINJQ query operation fails.
 */
public class LinjqException extends RuntimeException {

    public LinjqException(String message) {
        super(message);
    }

    public LinjqException(String message, Throwable cause) {
        super(message, cause);
    }

    public LinjqException(Throwable cause) {
        super(cause);
    }
}
