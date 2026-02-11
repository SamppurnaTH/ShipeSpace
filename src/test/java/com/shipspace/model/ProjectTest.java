package com.shipspace.model;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Project document model.
 * Validates model structure, annotations, and validation constraints.
 */
class ProjectTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    void testProjectCreationWithBuilder() {
        // Given
        Instant now = Instant.now();
        ExternalLink link = ExternalLink.builder()
                .type("github")
                .url("https://github.com/user/repo")
                .title("GitHub Repo")
                .build();

        // When
        Project project = Project.builder()
                .id("project123")
                .userId("user123")
                .name("Test Project")
                .description("A test project description")
                .techTags(Arrays.asList("Java", "Spring Boot", "MongoDB"))
                .externalLinks(Arrays.asList(link))
                .shipCount(5)
                .lastShipAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Then
        assertNotNull(project);
        assertEquals("project123", project.getId());
        assertEquals("user123", project.getUserId());
        assertEquals("Test Project", project.getName());
        assertEquals("A test project description", project.getDescription());
        assertEquals(3, project.getTechTags().size());
        assertEquals(1, project.getExternalLinks().size());
        assertEquals(5, project.getShipCount());
        assertEquals(now, project.getLastShipAt());
        assertEquals(now, project.getCreatedAt());
        assertEquals(now, project.getUpdatedAt());
    }

    @Test
    void testProjectDefaultValues() {
        // When
        Project project = Project.builder()
                .userId("user123")
                .name("Test Project")
                .description("Description")
                .build();

        // Then
        assertNotNull(project.getTechTags());
        assertTrue(project.getTechTags().isEmpty());
        assertNotNull(project.getExternalLinks());
        assertTrue(project.getExternalLinks().isEmpty());
        assertEquals(0, project.getShipCount());
        assertNull(project.getLastShipAt());
        assertNotNull(project.getCreatedAt());
        assertNotNull(project.getUpdatedAt());
    }

    @Test
    void testProjectValidation_ValidProject() {
        // Given
        Project project = Project.builder()
                .userId("user123")
                .name("Test Project")
                .description("A valid project description")
                .build();

        // When
        Set<ConstraintViolation<Project>> violations = validator.validate(project);

        // Then
        assertTrue(violations.isEmpty(), "Valid project should have no violations");
    }

    @Test
    void testProjectValidation_MissingUserId() {
        // Given
        Project project = Project.builder()
                .name("Test Project")
                .description("Description")
                .build();

        // When
        Set<ConstraintViolation<Project>> violations = validator.validate(project);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("userId")));
    }

    @Test
    void testProjectValidation_MissingName() {
        // Given
        Project project = Project.builder()
                .userId("user123")
                .description("Description")
                .build();

        // When
        Set<ConstraintViolation<Project>> violations = validator.validate(project);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    }

    @Test
    void testProjectValidation_MissingDescription() {
        // Given
        Project project = Project.builder()
                .userId("user123")
                .name("Test Project")
                .build();

        // When
        Set<ConstraintViolation<Project>> violations = validator.validate(project);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    @Test
    void testProjectValidation_NameTooLong() {
        // Given
        Project project = Project.builder()
                .userId("user123")
                .name("a".repeat(101))
                .description("Description")
                .build();

        // When
        Set<ConstraintViolation<Project>> violations = validator.validate(project);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("name")));
    }

    @Test
    void testProjectValidation_DescriptionTooLong() {
        // Given
        Project project = Project.builder()
                .userId("user123")
                .name("Test Project")
                .description("a".repeat(2001))
                .build();

        // When
        Set<ConstraintViolation<Project>> violations = validator.validate(project);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }
}
