package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for comment data with commenter profile information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    /**
     * Unique identifier of the comment.
     */
    private String id;

    /**
     * ID of the ship this comment is on.
     */
    private String shipId;

    /**
     * Text content of the comment.
     */
    private String content;

    /**
     * Profile information of the user who created the comment.
     */
    private UserProfileSummary commenter;

    /**
     * Timestamp when the comment was created.
     */
    private Instant createdAt;

    /**
     * Timestamp when the comment was last updated.
     */
    private Instant updatedAt;

    /**
     * Nested class for commenter profile summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileSummary {
        private String id;
        private String username;
        private String avatarUrl;
        private String bio;
    }
}
