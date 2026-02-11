package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for comment upvote toggle operations.
 * Indicates whether the upvote was added or removed and the new upvote count.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentUpvoteToggleResponse {

    /**
     * Whether the comment is now upvoted by the user.
     * true if upvote was added, false if upvote was removed.
     */
    private boolean upvoted;

    /**
     * The new total upvote count for the comment.
     */
    private int upvoteCount;

    /**
     * Whether the comment qualified for QUALITY_COMMENT scoring after this upvote.
     * Only relevant when upvoted is true and qualification thresholds are met.
     */
    private boolean qualifiedForScoring;
}
