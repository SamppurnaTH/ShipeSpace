package com.shipspace.service;

import com.shipspace.model.Comment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CommentQualificationService.
 * 
 * <p>Tests the qualification logic for QUALITY_COMMENT scoring:
 * <ul>
 *   <li>Comment length >= 50 characters</li>
 *   <li>Comment upvotes >= 2</li>
 * </ul>
 * 
 * <p>Requirements: 5.1, 5.2, 5.3, 5.4
 */
@DisplayName("CommentQualificationService Tests")
class CommentQualificationServiceTest {

    private CommentQualificationService commentQualificationService;

    @BeforeEach
    void setUp() {
        commentQualificationService = new CommentQualificationService();
    }

    // ========== Qualification Tests ==========

    @Test
    @DisplayName("isQualified: comment with 50 chars and 2 upvotes should qualify")
    void isQualified_ExactlyFiftyCharsAndTwoUpvotes_ReturnsTrue() {
        // Arrange - exactly 50 characters
        String content = "This comment has exactly fifty characters here!!!!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(2)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(content.length()).isEqualTo(50);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isQualified: comment with 49 chars and 2 upvotes should not qualify")
    void isQualified_FortyNineCharsAndTwoUpvotes_ReturnsFalse() {
        // Arrange - exactly 49 characters
        String content = "This comment has exactly forty nine characters!!!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(2)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(content.length()).isEqualTo(49);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: comment with 50 chars and 1 upvote should not qualify")
    void isQualified_FiftyCharsAndOneUpvote_ReturnsFalse() {
        // Arrange - exactly 50 characters
        String content = "This comment has exactly fifty characters here!!!!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(1)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(content.length()).isEqualTo(50);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: comment with 100 chars and 5 upvotes should qualify")
    void isQualified_HundredCharsAndFiveUpvotes_ReturnsTrue() {
        // Arrange - 100 characters
        String content = "This is a much longer comment that definitely exceeds the minimum length requirement of 50 chars!!!!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(5)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(content.length()).isEqualTo(100);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isQualified: comment with 51 chars and 2 upvotes should qualify")
    void isQualified_FiftyOneCharsAndTwoUpvotes_ReturnsTrue() {
        // Arrange - 51 characters (just above boundary)
        String content = "This comment has exactly fifty one characters now!!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(2)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(content.length()).isEqualTo(51);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isQualified: comment with 50 chars and 3 upvotes should qualify")
    void isQualified_FiftyCharsAndThreeUpvotes_ReturnsTrue() {
        // Arrange - exactly 50 characters, 3 upvotes (above boundary)
        String content = "This comment has exactly fifty characters here!!!!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(3)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(content.length()).isEqualTo(50);
        assertThat(result).isTrue();
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("isQualified: comment with null content should not qualify")
    void isQualified_NullContent_ReturnsFalse() {
        // Arrange
        Comment comment = Comment.builder()
            .content(null)
            .upvotes(2)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: comment with empty content should not qualify")
    void isQualified_EmptyContent_ReturnsFalse() {
        // Arrange
        Comment comment = Comment.builder()
            .content("")
            .upvotes(2)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: comment with null upvotes should not qualify")
    void isQualified_NullUpvotes_ReturnsFalse() {
        // Arrange
        String content = "This is a comment with exactly fifty characters!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(null)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: comment with zero upvotes should not qualify")
    void isQualified_ZeroUpvotes_ReturnsFalse() {
        // Arrange
        String content = "This is a comment with exactly fifty characters!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(0)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: comment with negative upvotes should not qualify")
    void isQualified_NegativeUpvotes_ReturnsFalse() {
        // Arrange
        String content = "This is a comment with exactly fifty characters!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(-1)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: null comment should throw IllegalArgumentException")
    void isQualified_NullComment_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> commentQualificationService.isQualified(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Comment cannot be null");
    }

    // ========== Both Criteria Failed ==========

    @Test
    @DisplayName("isQualified: comment with 49 chars and 1 upvote should not qualify")
    void isQualified_BothCriteriaFailed_ReturnsFalse() {
        // Arrange - fails both criteria
        String content = "This comment has exactly forty nine characters!!!";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(1)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(content.length()).isEqualTo(49);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: comment with 10 chars and 10 upvotes should not qualify")
    void isQualified_ShortContentHighUpvotes_ReturnsFalse() {
        // Arrange - high upvotes but too short
        Comment comment = Comment.builder()
            .content("Too short")
            .upvotes(10)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isQualified: comment with 200 chars and 0 upvotes should not qualify")
    void isQualified_LongContentNoUpvotes_ReturnsFalse() {
        // Arrange - long content but no upvotes
        String content = "This is a very long comment that definitely exceeds the minimum length requirement. " +
                        "It has plenty of characters to meet the length criterion, but unfortunately it has " +
                        "no upvotes at all.";
        Comment comment = Comment.builder()
            .content(content)
            .upvotes(0)
            .build();

        // Act
        boolean result = commentQualificationService.isQualified(comment);

        // Assert
        assertThat(content.length()).isGreaterThan(50);
        assertThat(result).isFalse();
    }
}
