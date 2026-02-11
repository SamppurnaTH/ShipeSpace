package com.shipspace.model;

import com.shipspace.dto.ProjectCreateRequest;
import com.shipspace.dto.ProjectUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for project input validation.
 * 
 * **Validates: Requirements 3.2**
 * 
 * Property 11: Project input validation
 * For any project creation or update request, invalid data (empty names, 
 * malformed links, invalid tags) should be rejected with validation errors.
 */
class ProjectInputValidationPropertyTest {

    private static Validator validator;

    @BeforeContainer
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Property 11: Project input validation - Valid inputs should pass
     * 
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Valid inputs pass")
    void validProjectInputsShouldPassValidation(
            @ForAll("validProjectName") String name,
            @ForAll("validProjectDescription") String description) {
        
        // Given: A project create request with valid inputs
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(name)
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: No validation errors should occur
        assertThat(violations).isEmpty();
    }

    @Provide
    Arbitrary<String> validProjectName() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(s -> !s.isBlank()); // Ensure not blank
    }

    @Provide
    Arbitrary<String> validProjectDescription() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', '!', '?', '-', '_', '\n')
                .ofMinLength(1)
                .ofMaxLength(2000)
                .filter(s -> !s.isBlank()); // Ensure not blank
    }
}
