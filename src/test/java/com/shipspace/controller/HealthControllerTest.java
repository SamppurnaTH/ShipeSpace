package com.shipspace.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HealthController.
 * Tests health check endpoint for monitoring and deployment verification.
 * 
 * Requirements tested: 9.5, 11.5
 */
@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @InjectMocks
    private HealthController healthController;

    @Test
    void healthCheck_ReturnsSuccessResponse() {
        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void healthCheck_ReturnsStatusUp() {
        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    void healthCheck_ReturnsTimestamp() {
        // Arrange
        Instant beforeCall = Instant.now();

        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        Instant afterCall = Instant.now();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("timestamp")).isNotNull();
        
        String timestampStr = (String) response.getBody().get("timestamp");
        Instant timestamp = Instant.parse(timestampStr);
        
        // Verify timestamp is within reasonable range
        assertThat(timestamp).isBetween(beforeCall.minusSeconds(1), afterCall.plusSeconds(1));
    }

    @Test
    void healthCheck_ReturnsServiceName() {
        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("service")).isEqualTo("ShipSpace MVP API");
    }

    @Test
    void healthCheck_ReturnsVersion() {
        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("version")).isEqualTo("1.0.0");
    }

    @Test
    void healthCheck_ReturnsAllRequiredFields() {
        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("status", "timestamp", "service", "version");
    }

    @Test
    void healthCheck_ConsistentResponseStructure() {
        // Act - Call multiple times
        ResponseEntity<Map<String, Object>> response1 = healthController.healthCheck();
        ResponseEntity<Map<String, Object>> response2 = healthController.healthCheck();

        // Assert - Both responses have same structure
        assertThat(response1.getBody()).isNotNull();
        assertThat(response2.getBody()).isNotNull();
        assertThat(response1.getBody().keySet()).isEqualTo(response2.getBody().keySet());
        
        // Status and service should be identical
        assertThat(response1.getBody().get("status")).isEqualTo(response2.getBody().get("status"));
        assertThat(response1.getBody().get("service")).isEqualTo(response2.getBody().get("service"));
        assertThat(response1.getBody().get("version")).isEqualTo(response2.getBody().get("version"));
    }

    @Test
    void healthCheck_TimestampUpdatesOnEachCall() throws InterruptedException {
        // Act - Call with small delay
        ResponseEntity<Map<String, Object>> response1 = healthController.healthCheck();
        Thread.sleep(10); // Small delay to ensure different timestamps
        ResponseEntity<Map<String, Object>> response2 = healthController.healthCheck();

        // Assert - Timestamps should be different
        assertThat(response1.getBody()).isNotNull();
        assertThat(response2.getBody()).isNotNull();
        
        String timestamp1 = (String) response1.getBody().get("timestamp");
        String timestamp2 = (String) response2.getBody().get("timestamp");
        
        assertThat(timestamp1).isNotEqualTo(timestamp2);
        
        Instant instant1 = Instant.parse(timestamp1);
        Instant instant2 = Instant.parse(timestamp2);
        
        // Second timestamp should be after first
        assertThat(instant2).isAfter(instant1);
    }

    @Test
    void healthCheck_ResponseBodyNotEmpty() {
        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().size()).isEqualTo(4);
    }

    @Test
    void healthCheck_StatusValueIsString() {
        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        assertThat(response.getBody()).isNotNull();
        Object status = response.getBody().get("status");
        assertThat(status).isInstanceOf(String.class);
    }

    @Test
    void healthCheck_TimestampIsValidISOFormat() {
        // Act
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        // Assert
        assertThat(response.getBody()).isNotNull();
        String timestamp = (String) response.getBody().get("timestamp");
        
        // Should be parseable as ISO-8601 instant
        assertThat(timestamp).isNotNull();
        assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z");
        
        // Should parse without exception
        Instant.parse(timestamp);
    }
}
