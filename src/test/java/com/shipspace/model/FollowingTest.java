package com.shipspace.model;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Following document model.
 * Validates model structure, annotations, and validation constraints.
 */
class FollowingTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    void testFollowingCreationWithBuilder() {
        // Given
        Instant now = Instant.now();

        // When
        Following following = Following.builder()
                .id("following123")
                .followerId("user123")
                .followeeId("user456")
                .createdAt(now)
                .build();

        // Then
        assertNotNull(following);
        assertEquals("following123", following.getId());
        assertEquals("user123", following.getFollowerId());
        assertEquals("user456", following.getFolloweeId());
        assertEquals(now, following.getCreatedAt());
    }

    @Test
    void testFollowingDefaultValues() {
        // When
        Following following = Following.builder()
                .followerId("user123")
                .followeeId("user456")
                .build();

        // Then
        assertNotNull(following.getCreatedAt());
    }

    @Test
    void testFollowingValidation_ValidFollowing() {
        // Given
        Following following = Following.builder()
                .followerId("user123")
                .followeeId("user456")
                .build();

        // When
        Set<ConstraintViolation<Following>> violations = validator.validate(following);

        // Then
        assertTrue(violations.isEmpty(), "Valid following should have no violations");
    }

    @Test
    void testFollowingValidation_MissingFollowerId() {
        // Given
        Following following = Following.builder()
                .followeeId("user456")
                .build();

        // When
        Set<ConstraintViolation<Following>> violations = validator.validate(following);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("followerId")));
    }

    @Test
    void testFollowingValidation_MissingFolloweeId() {
        // Given
        Following following = Following.builder()
                .followerId("user123")
                .build();

        // When
        Set<ConstraintViolation<Following>> violations = validator.validate(following);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("followeeId")));
    }
}
