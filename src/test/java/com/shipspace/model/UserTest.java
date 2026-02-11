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
 * Unit tests for User document model.
 * Validates model structure, annotations, and validation constraints.
 */
class UserTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    void testUserCreationWithBuilder() {
        // Given
        Instant now = Instant.now();
        
        // When
        User user = User.builder()
                .id("user123")
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .avatarUrl("https://example.com/avatar.jpg")
                .bio("Test bio")
                .location("San Francisco")
                .websiteUrl("https://example.com")
                .followerCount(10)
                .followingCount(5)
                .projectCount(3)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(user);
        assertEquals("user123", user.getId());
        assertEquals("github123", user.getGithubId());
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("https://example.com/avatar.jpg", user.getAvatarUrl());
        assertEquals("Test bio", user.getBio());
        assertEquals("San Francisco", user.getLocation());
        assertEquals("https://example.com", user.getWebsiteUrl());
        assertEquals(10, user.getFollowerCount());
        assertEquals(5, user.getFollowingCount());
        assertEquals(3, user.getProjectCount());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());
    }

    @Test
    void testUserCreationWithProgressionFields() {
        // Given
        Instant now = Instant.now();
        Instant activityTime = now.minusSeconds(3600);
        Instant rankUpdateTime = now.minusSeconds(1800);
        
        // When
        User user = User.builder()
                .id("user123")
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .shipScore(250)
                .level(7)
                .rank(RankTier.LAUNCHER)
                .lastShipActivityAt(activityTime)
                .rankUpdatedAt(rankUpdateTime)
                .build();

        // Then
        assertNotNull(user);
        assertEquals(250, user.getShipScore());
        assertEquals(7, user.getLevel());
        assertEquals(RankTier.LAUNCHER, user.getRank());
        assertEquals(activityTime, user.getLastShipActivityAt());
        assertEquals(rankUpdateTime, user.getRankUpdatedAt());
    }

    @Test
    void testUserDefaultValues() {
        // When
        User user = User.builder()
                .githubId("github123")
                .username("testuser")
                .build();

        // Then
        assertEquals(0, user.getFollowerCount());
        assertEquals(0, user.getFollowingCount());
        assertEquals(0, user.getProjectCount());
        assertEquals(0, user.getShipScore());
        assertEquals(1, user.getLevel());
        assertEquals(RankTier.DOCKED, user.getRank());
        assertNull(user.getLastShipActivityAt());
        assertNull(user.getRankUpdatedAt());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
    }

    @Test
    void testUserValidation_ValidUser() {
        // Given
        User user = User.builder()
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .build();

        // When
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        // Then
        assertTrue(violations.isEmpty(), "Valid user should have no violations");
    }

    @Test
    void testUserValidation_MissingGithubId() {
        // Given
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .build();

        // When
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("githubId")));
    }

    @Test
    void testUserValidation_MissingUsername() {
        // Given
        User user = User.builder()
                .githubId("github123")
                .email("test@example.com")
                .build();

        // When
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void testUserValidation_InvalidEmail() {
        // Given
        User user = User.builder()
                .githubId("github123")
                .username("testuser")
                .email("invalid-email")
                .build();

        // When
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void testUserValidation_UsernameTooShort() {
        // Given
        User user = User.builder()
                .githubId("github123")
                .username("ab")
                .email("test@example.com")
                .build();

        // When
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void testUserValidation_UsernameTooLong() {
        // Given
        User user = User.builder()
                .githubId("github123")
                .username("a".repeat(31))
                .email("test@example.com")
                .build();

        // When
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void testUserValidation_BioTooLong() {
        // Given
        User user = User.builder()
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .bio("a".repeat(501))
                .build();

        // When
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("bio")));
    }

    @Test
    void testUserValidation_LocationTooLong() {
        // Given
        User user = User.builder()
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .location("a".repeat(101))
                .build();

        // When
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("location")));
    }
}
