package com.shipspace.exception;

/**
 * Exception thrown when Multibase integration operations fail.
 */
public class MultibaseException extends RuntimeException {

    public MultibaseException(String message) {
        super(message);
    }

    public MultibaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
