package com.shipspace.exception;

/**
 * Exception thrown when a user attempts to access or modify a resource
 * they don't have permission for.
 * Results in a 403 Forbidden HTTP response.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String operation, String resourceType) {
        super(String.format("You don't have permission to %s this %s", operation, resourceType));
    }
}
