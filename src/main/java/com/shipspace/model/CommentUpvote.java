package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * CommentUpvote document model representing a user's upvote on a comment.
 * Tracks engagement and prevents duplicate upvotes.
 */
@Document(collection = "comment_upvotes")
@CompoundIndex(name = "user_comment_idx", def = "{'userId': 1, 'commentId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentUpvote {

    @Id
    private String id;

    /**
     * ID of the user who upvoted the comment.
     */
    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * ID of the comment that was upvoted.
     */
    @NotBlank(message = "Comment ID is required")
    @Indexed
    private String commentId;

    /**
     * Timestamp when the upvote was created.
     */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
