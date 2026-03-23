package com.shipspace.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard error response format for the ShipSpace API.
 * Provides consistent structure for all error responses across endpoints.
 * 
 * Implements Requirements 9.2 and 9.4:
 * - Consistent JSON response format with standard HTTP status codes
 * - Structured error responses with descriptive messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Always false for error responses. Enables consistent client-side checking.
     */
    @Builder.Default
    private boolean success = false;

    /**
     * Error code identifying the type of error.
     * Examples: VALIDATION_ERROR, RESOURCE_NOT_FOUND, AUTHENTICATION_ERROR
     */
    private String code;

    /**
     * Human-readable error message describing what went wrong.
     */
    private String message;

    /**
     * Timestamp when the error occurred (ISO-8601 format).
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Request path where the error occurred.
     */
    private String path;

    /**
     * Unique identifier for this error instance, useful for tracking and debugging.
     */
    @Builder.Default
    private String requestId = UUID.randomUUID().toString();

    /**
     * Additional error details (e.g., field-specific validation errors).
     * Only included when relevant (e.g., validation failures).
     */
    private Map<String, String> details;
}
