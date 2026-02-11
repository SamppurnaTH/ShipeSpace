package com.shipspace.service;

import com.shipspace.model.Comment;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CommentQualificationService.
 * 
 * These tests verify the correctness properties of comment qualification logic
 * across a wide range of inputs using property-based testing with jqwik.
 * 
 * A comment qualifies for QUALITY_COMMENT scoring if and only if:
 * - Content length >= 50 characters
 * - Upvotes >= 2
 * 
 * **Validates: Requirements 5.1, 5.2, 5.3, 5.4**
 */
class CommentQualificationServicePropertyTest {

    private final CommentQualificationService commentQualificationService = 
        new CommentQualificationService();

    /**
     * Property 8: Comment Qualification Requirements
     * 
     * For any comment, it should only qualify for QUALITY_COMMENT scoring if and only if
     * both conditions are met: length >= 50 characters AND upvotes >= 2. Comments failing
     * either criterion should be rejected.
     * 
     * This property verifies that:
     * 1. Comments with content length >= 50 AND upvotes >= 2 qualify
     * 2. Comments with content length < 50 do NOT qualify (regardless of upvotes)
     * 3. Comments with upvotes < 2 do NOT qualify (regardless of content length)
     * 4. Both criteria must be satisfied simultaneously
     * 
     * **Validates: Requirements 5.1, 5.2, 5.3, 5.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 8: Comment Qualification Requirements")
    void commentQualificationRequiresBothCriteria(
            @ForAll("comments") Comment comment
    ) {
        // Act - Check if comment qualifies
        boolean isQualified = commentQualificationService.isQualified(comment);

        // Determine expected qualification based on both criteria
        String content = comment.getContent();
        Integer upvotes = comment.getUpvotes();
        
        boolean meetsLengthCriterion = content != null && content.length() >= 50;
        boolean meetsUpvotesCriterion = upvotes != null && upvotes >= 2;
        boolean shouldQualify = meetsLengthCriterion && meetsUpvotesCriterion;

        // Assert - Qualification matches expected result
        assertEquals(shouldQualify, isQualified,
                String.format("Comment with content length %d and %d upvotes should %s qualify",
                        content != null ? content.length() : 0,
                        upvotes != null ? upvotes : 0,
                        shouldQualify ? "" : "NOT"));

        // Property 1: If qualified, both criteria must be met
        if (isQualified) {
            assertTrue(meetsLengthCriterion,
                    String.format("Qualified comment must have content length >= 50, but was %d",
                            content != null ? content.length() : 0));
            assertTrue(meetsUpvotesCriterion,
                    String.format("Qualified comment must have upvotes >= 2, but was %d",
                            upvotes != null ? upvotes : 0));
        }

        // Property 2: If not qualified, at least one criterion must be failed
        if (!isQualified) {
            assertTrue(!meetsLengthCriterion || !meetsUpvotesCriterion,
                    String.format("Non-qualified comment must fail at least one criterion, " +
                            "but had content length %d and %d upvotes",
                            content != null ? content.length() : 0,
                            upvotes != null ? upvotes : 0));
        }
    }

    /**
     * Property 8: Comment Qualification - Length Boundary Testing
     * 
     * Specifically tests the boundary condition at 50 characters.
     * Comments with exactly 50 characters should qualify (if upvotes >= 2),
     * while comments with 49 characters should not qualify.
     * 
     * **Validates: Requirements 5.1, 5.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 8: Comment Qualification Length Boundary")
    void commentQualificationLengthBoundary(
            @ForAll @IntRange(min = 2, max = 10) int upvotes
    ) {
        // Arrange - Create comments at the boundary (49 and 50 characters)
        String content49 = "a".repeat(49);
        String content50 = "a".repeat(50);
        
        Comment comment49 = Comment.builder()
                .content(content49)
                .upvotes(upvotes)
                .build();
        
        Comment comment50 = Comment.builder()
                .content(content50)
                .upvotes(upvotes)
                .build();

        // Act
        boolean qualified49 = commentQualificationService.isQualified(comment49);
        boolean qualified50 = commentQualificationService.isQualified(comment50);

        // Assert - 49 chars should NOT qualify, 50 chars should qualify
        assertFalse(qualified49,
                String.format("Comment with 49 characters and %d upvotes should NOT qualify", upvotes));
        assertTrue(qualified50,
                String.format("Comment with 50 characters and %d upvotes should qualify", upvotes));
    }

    /**
     * Property 8: Comment Qualification - Upvotes Boundary Testing
     * 
     * Specifically tests the boundary condition at 2 upvotes.
     * Comments with exactly 2 upvotes should qualify (if length >= 50),
     * while comments with 1 upvote should not qualify.
     * 
     * **Validates: Requirements 5.2, 5.3**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 8: Comment Qualification Upvotes Boundary")
    void commentQualificationUpvotesBoundary(
            @ForAll @IntRange(min = 50, max = 200) int contentLength
    ) {
        // Arrange - Create comments at the boundary (1 and 2 upvotes)
        String content = "a".repeat(contentLength);
        
        Comment comment1Upvote = Comment.builder()
                .content(content)
                .upvotes(1)
                .build();
        
        Comment comment2Upvotes = Comment.builder()
                .content(content)
                .upvotes(2)
                .build();

        // Act
        boolean qualified1 = commentQualificationService.isQualified(comment1Upvote);
        boolean qualified2 = commentQualificationService.isQualified(comment2Upvotes);

        // Assert - 1 upvote should NOT qualify, 2 upvotes should qualify
        assertFalse(qualified1,
                String.format("Comment with %d characters and 1 upvote should NOT qualify", contentLength));
        assertTrue(qualified2,
                String.format("Comment with %d characters and 2 upvotes should qualify", contentLength));
    }

    /**
     * Property 8: Comment Qualification - Failing One Criterion
     * 
     * Verifies that failing either criterion (length OR upvotes) results in
     * disqualification, even if the other criterion is met.
     * 
     * **Validates: Requirements 5.3, 5.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 8: Comment Qualification Fails with One Criterion")
    void commentQualificationFailsWithOneCriterion(
            @ForAll @IntRange(min = 0, max = 200) int contentLength,
            @ForAll @IntRange(min = 0, max = 10) int upvotes
    ) {
        // Arrange - Create comment with given parameters
        String content = "a".repeat(contentLength);
        Comment comment = Comment.builder()
                .content(content)
                .upvotes(upvotes)
                .build();

        // Act
        boolean isQualified = commentQualificationService.isQualified(comment);

        // Assert - If either criterion fails, comment should not qualify
        boolean meetsLengthCriterion = contentLength >= 50;
        boolean meetsUpvotesCriterion = upvotes >= 2;

        if (!meetsLengthCriterion || !meetsUpvotesCriterion) {
            assertFalse(isQualified,
                    String.format("Comment with %d characters and %d upvotes should NOT qualify " +
                            "(length criterion: %s, upvotes criterion: %s)",
                            contentLength, upvotes,
                            meetsLengthCriterion ? "PASS" : "FAIL",
                            meetsUpvotesCriterion ? "PASS" : "FAIL"));
        }
    }

    /**
     * Property 8: Comment Qualification - Null Handling
     * 
     * Verifies that comments with null content or null upvotes do not qualify.
     * 
     * **Validates: Requirements 5.1, 5.2, 5.3**
     */
    @Property(tries = 50)
    @Label("Feature: rank-level-progression, Property 8: Comment Qualification Null Handling")
    void commentQualificationHandlesNullFields(
            @ForAll("nullableComments") Comment comment
    ) {
        // Act
        boolean isQualified = commentQualificationService.isQualified(comment);

        // Assert - Comments with null content or null upvotes should not qualify
        String content = comment.getContent();
        Integer upvotes = comment.getUpvotes();

        if (content == null || upvotes == null) {
            assertFalse(isQualified,
                    String.format("Comment with null content (%s) or null upvotes (%s) should NOT qualify",
                            content == null ? "NULL" : "present",
                            upvotes == null ? "NULL" : "present"));
        }
    }

    /**
     * Property 8: Comment Qualification - High Values Always Qualify
     * 
     * Verifies that comments with content length and upvotes well above the thresholds
     * always qualify.
     * 
     * **Validates: Requirements 5.1, 5.2, 5.4**
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 8: Comment Qualification High Values")
    void commentQualificationHighValuesAlwaysQualify(
            @ForAll @IntRange(min = 100, max = 1000) int contentLength,
            @ForAll @IntRange(min = 10, max = 100) int upvotes
    ) {
        // Arrange - Create comment with high values
        String content = "a".repeat(contentLength);
        Comment comment = Comment.builder()
                .content(content)
                .upvotes(upvotes)
                .build();

        // Act
        boolean isQualified = commentQualificationService.isQualified(comment);

        // Assert - Should always qualify
        assertTrue(isQualified,
                String.format("Comment with %d characters and %d upvotes should qualify",
                        contentLength, upvotes));
    }

    // ========== Arbitrary Providers ==========

    /**
     * Provides arbitrary Comment instances with various content lengths and upvote counts.
     * Covers the full range of possible values including edge cases.
     */
    @Provide
    Arbitrary<Comment> comments() {
        Arbitrary<String> content = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(0)
                .ofMaxLength(200);
        
        Arbitrary<Integer> upvotes = Arbitraries.integers()
                .between(0, 20);

        return Combinators.combine(content, upvotes)
                .as((c, u) -> Comment.builder()
                        .content(c)
                        .upvotes(u)
                        .build());
    }

    /**
     * Provides arbitrary Comment instances that may have null content or null upvotes.
     * Used for testing null handling.
     */
    @Provide
    Arbitrary<Comment> nullableComments() {
        Arbitrary<String> content = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(0)
                .ofMaxLength(200)
                .injectNull(0.3); // 30% chance of null
        
        Arbitrary<Integer> upvotes = Arbitraries.integers()
                .between(0, 20)
                .injectNull(0.3); // 30% chance of null

        return Combinators.combine(content, upvotes)
                .as((c, u) -> Comment.builder()
                        .content(c)
                        .upvotes(u)
                        .build());
    }
}
