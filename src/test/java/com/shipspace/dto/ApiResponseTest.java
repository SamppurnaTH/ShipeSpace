package com.shipspace.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApiResponse.
 * Validates the structure and behavior of successful API response wrapper.
 * 
 * Tests Requirement 9.2:
 * - Consistent JSON response format for successful responses
 */
@DisplayName("ApiResponse Tests")
class ApiResponseTest {

    @Test
    @DisplayName("Should create successful response with data")
    void createSuccessResponse_WithData_Success() {
        // Arrange
        String data = "test data";

        // Act
        ApiResponse<String> response = ApiResponse.success(data);

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("test data");
        assertThat(response.getMessage()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create successful response with data and message")
    void createSuccessResponse_WithDataAndMessage_Success() {
        // Arrange
        String data = "test data";
        String message = "Operation completed successfully";

        // Act
        ApiResponse<String> response = ApiResponse.success(data, message);

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("test data");
        assertThat(response.getMessage()).isEqualTo("Operation completed successfully");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create successful response with message only")
    void createSuccessResponse_WithMessageOnly_Success() {
        // Arrange
        String message = "Operation completed";

        // Act
        ApiResponse<String> response = ApiResponse.successWithMessage(message);

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Operation completed");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create response with complex data type")
    void createSuccessResponse_WithComplexData_Success() {
        // Arrange
        TestDto data = new TestDto("test", 123);

        // Act
        ApiResponse<TestDto> response = ApiResponse.success(data);

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getName()).isEqualTo("test");
        assertThat(response.getData().getValue()).isEqualTo(123);
    }

    @Test
    @DisplayName("Should create response using builder")
    void createResponse_UsingBuilder_Success() {
        // Arrange
        Instant timestamp = Instant.now();

        // Act
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Custom message")
                .data("custom data")
                .timestamp(timestamp)
                .build();

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Custom message");
        assertThat(response.getData()).isEqualTo("custom data");
        assertThat(response.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("Should set timestamp automatically")
    void createResponse_SetsTimestampAutomatically() {
        // Arrange
        Instant before = Instant.now();

        // Act
        ApiResponse<String> response = ApiResponse.success("data");

        // Assert
        Instant after = Instant.now();
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should handle null data")
    void createResponse_WithNullData_Success() {
        // Act
        ApiResponse<String> response = ApiResponse.success(null, "Message only");

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Message only");
    }

    @Test
    @DisplayName("Should create response using no-args constructor")
    void createResponse_UsingNoArgsConstructor_Success() {
        // Act
        ApiResponse<String> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData("test");
        response.setMessage("test message");

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("test");
        assertThat(response.getMessage()).isEqualTo("test message");
    }

    @Test
    @DisplayName("Should support equals and hashCode")
    void apiResponse_SupportsEqualsAndHashCode() {
        // Arrange
        Instant timestamp = Instant.now();
        ApiResponse<String> response1 = ApiResponse.<String>builder()
                .success(true)
                .data("test")
                .message("message")
                .timestamp(timestamp)
                .build();
        
        ApiResponse<String> response2 = ApiResponse.<String>builder()
                .success(true)
                .data("test")
                .message("message")
                .timestamp(timestamp)
                .build();

        // Act & Assert
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("Should support toString")
    void apiResponse_SupportsToString() {
        // Arrange
        ApiResponse<String> response = ApiResponse.success("test data", "Success message");

        // Act
        String toString = response.toString();

        // Assert
        assertThat(toString).contains("success=true");
        assertThat(toString).contains("test data");
        assertThat(toString).contains("Success message");
    }

    // Helper class for testing complex data types
    private static class TestDto {
        private final String name;
        private final int value;

        public TestDto(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }
}
