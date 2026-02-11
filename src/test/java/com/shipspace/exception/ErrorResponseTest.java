package com.shipspace.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorResponse.
 * Validates the structure and behavior of error response objects.
 * 
 * Tests Requirements 9.2 and 9.4:
 * - Consistent JSON response format structure
 * - Structured error responses with all required fields
 */
@DisplayName("ErrorResponse Tests")
class ErrorResponseTest {

    @Test
    @DisplayName("Should create ErrorResponse with all fields")
    void createErrorResponse_WithAllFields_Success() {
        // Arrange
        Map<String, String> details = new HashMap<>();
        details.put("field1", "error1");
        details.put("field2", "error2");
        
        Instant timestamp = Instant.now();
        String requestId = "test-request-id";

        // Act
        ErrorResponse response = ErrorResponse.builder()
                .code("TEST_ERROR")
                .message("Test error message")
                .timestamp(timestamp)
                .path("/api/test")
                .requestId(requestId)
                .details(details)
                .build();

        // Assert
        assertThat(response.getCode()).isEqualTo("TEST_ERROR");
        assertThat(response.getMessage()).isEqualTo("Test error message");
        assertThat(response.getTimestamp()).isEqualTo(timestamp);
        assertThat(response.getPath()).isEqualTo("/api/test");
        assertThat(response.getRequestId()).isEqualTo(requestId);
        assertThat(response.getDetails()).hasSize(2);
        assertThat(response.getDetails().get("field1")).isEqualTo("error1");
    }

    @Test
    @DisplayName("Should create ErrorResponse with minimal fields")
    void createErrorResponse_WithMinimalFields_Success() {
        // Act
        ErrorResponse response = ErrorResponse.builder()
                .code("MINIMAL_ERROR")
                .message("Minimal error")
                .build();

        // Assert
        assertThat(response.getCode()).isEqualTo("MINIMAL_ERROR");
        assertThat(response.getMessage()).isEqualTo("Minimal error");
        assertThat(response.getTimestamp()).isNotNull(); // Default value
        assertThat(response.getRequestId()).isNotNull(); // Default value
    }

    @Test
    @DisplayName("Should generate unique requestId by default")
    void createErrorResponse_GeneratesUniqueRequestId() {
        // Act
        ErrorResponse response1 = ErrorResponse.builder()
                .code("ERROR1")
                .message("Error 1")
                .build();
        
        ErrorResponse response2 = ErrorResponse.builder()
                .code("ERROR2")
                .message("Error 2")
                .build();

        // Assert
        assertThat(response1.getRequestId()).isNotNull();
        assertThat(response2.getRequestId()).isNotNull();
        assertThat(response1.getRequestId()).isNotEqualTo(response2.getRequestId());
    }

    @Test
    @DisplayName("Should set timestamp by default")
    void createErrorResponse_SetsTimestampByDefault() {
        // Arrange
        Instant before = Instant.now();

        // Act
        ErrorResponse response = ErrorResponse.builder()
                .code("TEST_ERROR")
                .message("Test")
                .build();

        // Assert
        Instant after = Instant.now();
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should support null details field")
    void createErrorResponse_WithNullDetails_Success() {
        // Act
        ErrorResponse response = ErrorResponse.builder()
                .code("NO_DETAILS_ERROR")
                .message("Error without details")
                .details(null)
                .build();

        // Assert
        assertThat(response.getDetails()).isNull();
    }

    @Test
    @DisplayName("Should support empty details map")
    void createErrorResponse_WithEmptyDetails_Success() {
        // Act
        ErrorResponse response = ErrorResponse.builder()
                .code("EMPTY_DETAILS_ERROR")
                .message("Error with empty details")
                .details(new HashMap<>())
                .build();

        // Assert
        assertThat(response.getDetails()).isNotNull();
        assertThat(response.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("Should create ErrorResponse using no-args constructor")
    void createErrorResponse_UsingNoArgsConstructor_Success() {
        // Act
        ErrorResponse response = new ErrorResponse();
        response.setCode("CONSTRUCTOR_ERROR");
        response.setMessage("Error via constructor");

        // Assert
        assertThat(response.getCode()).isEqualTo("CONSTRUCTOR_ERROR");
        assertThat(response.getMessage()).isEqualTo("Error via constructor");
    }

    @Test
    @DisplayName("Should create ErrorResponse using all-args constructor")
    void createErrorResponse_UsingAllArgsConstructor_Success() {
        // Arrange
        Map<String, String> details = new HashMap<>();
        details.put("field", "error");
        Instant timestamp = Instant.now();

        // Act
        ErrorResponse response = new ErrorResponse(
                "ALL_ARGS_ERROR",
                "Error via all-args constructor",
                timestamp,
                "/api/test",
                "request-123",
                details
        );

        // Assert
        assertThat(response.getCode()).isEqualTo("ALL_ARGS_ERROR");
        assertThat(response.getMessage()).isEqualTo("Error via all-args constructor");
        assertThat(response.getTimestamp()).isEqualTo(timestamp);
        assertThat(response.getPath()).isEqualTo("/api/test");
        assertThat(response.getRequestId()).isEqualTo("request-123");
        assertThat(response.getDetails()).hasSize(1);
    }

    @Test
    @DisplayName("Should support equals and hashCode")
    void errorResponse_SupportsEqualsAndHashCode() {
        // Arrange
        ErrorResponse response1 = ErrorResponse.builder()
                .code("TEST_ERROR")
                .message("Test")
                .requestId("same-id")
                .build();
        
        ErrorResponse response2 = ErrorResponse.builder()
                .code("TEST_ERROR")
                .message("Test")
                .requestId("same-id")
                .build();

        // Act & Assert
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("Should support toString")
    void errorResponse_SupportsToString() {
        // Arrange
        ErrorResponse response = ErrorResponse.builder()
                .code("TEST_ERROR")
                .message("Test message")
                .build();

        // Act
        String toString = response.toString();

        // Assert
        assertThat(toString).contains("TEST_ERROR");
        assertThat(toString).contains("Test message");
    }
}
