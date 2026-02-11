package com.shipspace.model;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Ship document model.
 * Validates model structure, annotations, validation constraints, and edit window logic.
 */
class ShipTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    void testShipCreationWithBuilder() {
        // Given
        Instant now = Instant.now();
        Instant editWindowExpires = now.plus(10, ChronoUnit.MINUTES);
        
        ImageReference image = ImageReference.builder()
                .multibaseHash("QmHash123")
                .filename("image.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .build();

        ExternalLink link = ExternalLink.builder()
                .url("https://example.com")
                .title("Example Link")
                .build();

        // When
        Ship ship = Ship.builder()
                .id("ship123")
                .userId("user123")
                .projectId("project123")
                .content("This is a ship update")
                .images(Arrays.asList(image))
                .externalLinks(Arrays.asList(link))
                .likeCount(10)
                .commentCount(5)
                .trendingScore(42.5)
                .editWindowExpires(editWindowExpires)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(ship);
        assertEquals("ship123", ship.getId());
        assertEquals("user123", ship.getUserId());
        assertEquals("project123", ship.getProjectId());
        assertEquals("This is a ship update", ship.getContent());
        assertEquals(1, ship.getImages().size());
        assertEquals(1, ship.getExternalLinks().size());
        assertEquals(10, ship.getLikeCount());
        assertEquals(5, ship.getCommentCount());
        assertEquals(42.5, ship.getTrendingScore());
        assertEquals(editWindowExpires, ship.getEditWindowExpires());
        assertEquals(now, ship.getCreatedAt());
        assertEquals(now, ship.getUpdatedAt());
    }

    @Test
    void testShipDefaultValues() {
        // When
        Ship ship = Ship.builder()
                .userId("user123")
                .projectId("project123")
                .content("Content")
                .build();

        // Then
        assertNotNull(ship.getImages());
        assertTrue(ship.getImages().isEmpty());
        assertNotNull(ship.getExternalLinks());
        assertTrue(ship.getExternalLinks().isEmpty());
        assertEquals(0, ship.getLikeCount());
        assertEquals(0, ship.getCommentCount());
        assertEquals(0.0, ship.getTrendingScore());
        assertNotNull(ship.getCreatedAt());
        assertNotNull(ship.getUpdatedAt());
    }

    @Test
    void testShipValidation_ValidShip() {
        // Given
        Ship ship = Ship.builder()
                .userId("user123")
                .projectId("project123")
                .content("This is valid content")
                .build();

        // When
        Set<ConstraintViolation<Ship>> violations = validator.validate(ship);

        // Then
        assertTrue(violations.isEmpty(), "Valid ship should have no violations");
    }

    @Test
    void testShipValidation_MissingUserId() {
        // Given
        Ship ship = Ship.builder()
                .projectId("project123")
                .content("Content")
                .build();

        // When
        Set<ConstraintViolation<Ship>> violations = validator.validate(ship);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("userId")));
    }

    @Test
    void testShipValidation_MissingProjectId() {
        // Given
        Ship ship = Ship.builder()
                .userId("user123")
                .content("Content")
                .build();

        // When
        Set<ConstraintViolation<Ship>> violations = validator.validate(ship);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("projectId")));
    }

    @Test
    void testShipValidation_MissingContent() {
        // Given
        Ship ship = Ship.builder()
                .userId("user123")
                .projectId("project123")
                .build();

        // When
        Set<ConstraintViolation<Ship>> violations = validator.validate(ship);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    @Test
    void testShipValidation_ContentTooLong() {
        // Given
        Ship ship = Ship.builder()
                .userId("user123")
                .projectId("project123")
                .content("a".repeat(5001))
                .build();

        // When
        Set<ConstraintViolation<Ship>> violations = validator.validate(ship);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    @Test
    void testEditWindowOpen_WithinWindow() {
        // Given
        Instant futureExpiry = Instant.now().plus(5, ChronoUnit.MINUTES);
        Ship ship = Ship.builder()
                .userId("user123")
                .projectId("project123")
                .content("Content")
                .editWindowExpires(futureExpiry)
                .build();

        // When
        boolean isOpen = ship.isEditWindowOpen();

        // Then
        assertTrue(isOpen, "Edit window should be open when expiry is in the future");
    }

    @Test
    void testEditWindowOpen_AfterWindow() {
        // Given
        Instant pastExpiry = Instant.now().minus(5, ChronoUnit.MINUTES);
        Ship ship = Ship.builder()
                .userId("user123")
                .projectId("project123")
                .content("Content")
                .editWindowExpires(pastExpiry)
                .build();

        // When
        boolean isOpen = ship.isEditWindowOpen();

        // Then
        assertFalse(isOpen, "Edit window should be closed when expiry is in the past");
    }

    @Test
    void testEditWindowOpen_NullExpiry() {
        // Given
        Ship ship = Ship.builder()
                .userId("user123")
                .projectId("project123")
                .content("Content")
                .editWindowExpires(null)
                .build();

        // When
        boolean isOpen = ship.isEditWindowOpen();

        // Then
        assertFalse(isOpen, "Edit window should be closed when expiry is null");
    }
}
