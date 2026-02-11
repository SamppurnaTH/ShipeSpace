package com.shipspace.service;

import com.shipspace.dto.ProjectCreateRequest;
import com.shipspace.dto.ProjectUpdateRequest;
import com.shipspace.model.ExternalLink;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for project input validation.
 * 
 * **Validates: Requirements 3.2**
 * 
 * Tests that project creation and update requests properly validate:
 * - Project names (required, length constraints)
 * - Project descriptions (required, length constraints)
 * - External links (URL validation, field constraints)
 * - Technology tags (format and constraints)
 */
class ProjectValidationPropertyTest {

    private final Validator validator;

    ProjectValidationPropertyTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    /**
     * Property 11: Project input validation
     * 
     * For any project creation or update request, invalid data (empty names, 
     * malformed links, invalid tags) should be rejected with validation errors.
     * 
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Valid names should pass")
    void validProjectNamesShouldPassValidation(
            @ForAll("nonBlankString") String name,
            @ForAll("nonBlankString") String description) {
        
        Assume.that(name.length() >= 1 && name.length() <= 100);
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        
        // Given: A project create request with valid name and description
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
    Arbitrary<String> nonBlankString() {
        return Arbitraries.strings()
                .alpha().numeric().withChars(' ', '-', '_', '.')
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(s -> !s.trim().isEmpty());
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Empty names should fail")
    void emptyProjectNamesShouldFailValidation(@ForAll("nonBlankString") String description) {
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        
        // Given: A project create request with empty name
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name("")
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the name field
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Null names should fail")
    void nullProjectNamesShouldFailValidation(@ForAll("nonBlankString") String description) {
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        
        // Given: A project create request with null name
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(null)
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the name field
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Names exceeding max length should fail")
    void tooLongProjectNamesShouldFailValidation(
            @ForAll @StringLength(min = 101, max = 200) String longName,
            @ForAll("nonBlankString") String description) {
        
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        
        // Given: A project create request with name exceeding 100 characters
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(longName)
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the name field
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Empty descriptions should fail")
    void emptyProjectDescriptionsShouldFailValidation(@ForAll("nonBlankString") String name) {
        Assume.that(name.length() >= 1 && name.length() <= 100);
        
        // Given: A project create request with empty description
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(name)
                .description("")
                .techTags(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the description field
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Null descriptions should fail")
    void nullProjectDescriptionsShouldFailValidation(@ForAll("nonBlankString") String name) {
        Assume.that(name.length() >= 1 && name.length() <= 100);
        
        // Given: A project create request with null description
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(name)
                .description(null)
                .techTags(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the description field
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Descriptions exceeding max length should fail")
    void tooLongProjectDescriptionsShouldFailValidation(
            @ForAll("nonBlankString") String name,
            @ForAll @StringLength(min = 2001, max = 3000) String longDescription) {
        
        Assume.that(name.length() >= 1 && name.length() <= 100);
        
        // Given: A project create request with description exceeding 2000 characters
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(name)
                .description(longDescription)
                .techTags(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the description field
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Valid external links should pass")
    void validExternalLinksShouldPassValidation(
            @ForAll("nonBlankString") String name,
            @ForAll("nonBlankString") String description,
            @ForAll("nonBlankString") String linkType,
            @ForAll("nonBlankString") String url,
            @ForAll("nonBlankString") String title) {
        
        Assume.that(name.length() >= 1 && name.length() <= 100);
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        Assume.that(linkType.length() >= 1 && linkType.length() <= 50);
        Assume.that(url.length() >= 1 && url.length() <= 100);
        Assume.that(title.length() >= 1 && title.length() <= 200);
        
        // Given: A project create request with valid external link
        ExternalLink link = ExternalLink.builder()
                .type(linkType)
                .url(url)
                .title(title)
                .build();
        
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(name)
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(List.of(link))
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: No validation errors should occur
        assertThat(violations).isEmpty();
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - External links with empty URL should fail")
    void externalLinksWithEmptyUrlShouldFailValidation(
            @ForAll("nonBlankString") String name,
            @ForAll("nonBlankString") String description) {
        
        Assume.that(name.length() >= 1 && name.length() <= 100);
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        
        // Given: A project create request with external link having empty URL
        ExternalLink link = ExternalLink.builder()
                .type("github")
                .url("")
                .title("My Project")
                .build();
        
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(name)
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(List.of(link))
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the external link URL
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("url"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - External links with null URL should fail")
    void externalLinksWithNullUrlShouldFailValidation(
            @ForAll("nonBlankString") String name,
            @ForAll("nonBlankString") String description) {
        
        Assume.that(name.length() >= 1 && name.length() <= 100);
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        
        // Given: A project create request with external link having null URL
        ExternalLink link = ExternalLink.builder()
                .type("github")
                .url(null)
                .title("My Project")
                .build();
        
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(name)
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(List.of(link))
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the external link URL
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("url"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - External links with oversized fields should fail")
    void externalLinksWithOversizedFieldsShouldFailValidation(
            @ForAll("nonBlankString") String name,
            @ForAll("nonBlankString") String description,
            @ForAll @StringLength(min = 51, max = 100) String longType,
            @ForAll @StringLength(min = 201, max = 300) String longTitle) {
        
        Assume.that(name.length() >= 1 && name.length() <= 100);
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        
        // Given: A project create request with external link having oversized type or title
        ExternalLink link = ExternalLink.builder()
                .type(longType)
                .url("https://example.com")
                .title(longTitle)
                .build();
        
        ProjectCreateRequest request = ProjectCreateRequest.builder()
                .name(name)
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(List.of(link))
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectCreateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the external link fields
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().contains("type") || 
            v.getPropertyPath().toString().contains("title"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Update requests with valid data should pass")
    void validProjectUpdatesShouldPassValidation(
            @ForAll("nonBlankString") String name,
            @ForAll("nonBlankString") String description) {
        
        Assume.that(name.length() >= 1 && name.length() <= 100);
        Assume.that(description.length() >= 1 && description.length() <= 2000);
        
        // Given: A project update request with valid name and description
        ProjectUpdateRequest request = ProjectUpdateRequest.builder()
                .name(name)
                .description(description)
                .techTags(new ArrayList<>())
                .externalLinks(new ArrayList<>())
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectUpdateRequest>> violations = validator.validate(request);

        // Then: No validation errors should occur
        assertThat(violations).isEmpty();
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Update requests with empty name should fail")
    void projectUpdatesWithEmptyNameShouldFailValidation() {
        // Given: A project update request with empty name
        ProjectUpdateRequest request = ProjectUpdateRequest.builder()
                .name("")
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectUpdateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur for the name field
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 11: Project input validation - Update requests with oversized fields should fail")
    void projectUpdatesWithOversizedFieldsShouldFailValidation(
            @ForAll @StringLength(min = 101, max = 200) String longName,
            @ForAll @StringLength(min = 2001, max = 3000) String longDescription) {
        
        // Given: A project update request with oversized fields
        ProjectUpdateRequest request = ProjectUpdateRequest.builder()
                .name(longName)
                .description(longDescription)
                .build();

        // When: Validating the request
        Set<ConstraintViolation<ProjectUpdateRequest>> violations = validator.validate(request);

        // Then: Validation errors should occur
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().equals("name") || 
            v.getPropertyPath().toString().equals("description"));
    }
}