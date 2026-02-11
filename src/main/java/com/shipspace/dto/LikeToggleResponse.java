package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for like toggle operations.
 * Indicates whether the like was added or removed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeToggleResponse {

    /**
     * Whether the ship is now liked by the user.
     */
    private boolean liked;

    /**
     * Updated like count for the ship.
     */
    private Integer likeCount;

    /**
     * Message describing the action taken.
     */
    private String message;
}
