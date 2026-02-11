package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Comment document model representing a user's comment on a ship.
 * Tracks engagement and conversation on ship updates.
 */
@Document(collection = "comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    private String id;

    /**
     * ID of the user who created the comment.
     */
    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * ID of the ship this comment is on.
     */
    @NotBlank(message = "Ship ID is required")
    @Indexed
    private String shipId;

    /**
     * Text content of the comment.
     */
    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 1000, message = "Content must be between 1 and 1000 characters")
    private String content;

    /**
     * Timestamp when the comment was created.
     */
    @Indexed
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Timestamp when the comment was last updated.
     */
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Number of upvotes this comment has received.
     * Used for quality comment qualification.
     */
    @Builder.Default
    private Integer upvotes = 0;
}
