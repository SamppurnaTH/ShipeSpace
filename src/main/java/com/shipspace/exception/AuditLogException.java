package com.shipspace.exception;

public class AuditLogException extends RuntimeException {
    public AuditLogException(String message, Throwable cause) {
        super(message, cause);
    }
}
