package com.shipspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard wrapper for successful API responses.
 * Provides consistent response format across all endpoints.
 * 
 * This is an optional wrapper that can be used for standardization.
 * Currently, most endpoints return DTOs directly, which is also acceptable.
 * 
 * Implements Requirement 9.2:
 * - Consistent JSON response formats with standard HTTP status codes
 * 
 * @param <T> the type of data being returned
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Indicates whether the request was successful.
     */
    private boolean success;

    /**
     * Human-readable message about the response.
     */
    private String message;

    /**
     * The actual response data.
     */
    private T data;

    /**
     * Timestamp of the response (ISO-8601 format).
     */
    private Instant timestamp;

    /**
     * Creates a successful response with data.
     * 
     * @param data the response data
     * @param <T> the type of data
     * @return ApiResponse with success=true and the provided data
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setTimestamp(Instant.now());
        return response;
    }

    /**
     * Creates a successful response with data and message.
     * 
     * @param data the response data
     * @param message a descriptive message
     * @param <T> the type of data
     * @return ApiResponse with success=true, data, and message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        response.setTimestamp(Instant.now());
        return response;
    }

    /**
     * Creates a successful response with just a message (no data).
     * 
     * @param message a descriptive message
     * @param <T> the type of data
     * @return ApiResponse with success=true and message
     */
    public static <T> ApiResponse<T> successWithMessage(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setTimestamp(Instant.now());
        return response;
    }
}
