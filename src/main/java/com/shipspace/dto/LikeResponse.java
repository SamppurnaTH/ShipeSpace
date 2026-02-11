package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for like data with user profile information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeResponse {

    /**
     * Unique identifier of the like.
     */
    private String id;

    /**
     * ID of the ship that was liked.
     */
    private String shipId;

    /**
     * Profile information of the user who liked the ship.
     */
    private UserProfileSummary user;

    /**
     * Timestamp when the like was created.
     */
    private Instant createdAt;

    /**
     * Nested class for user profile summary.
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
