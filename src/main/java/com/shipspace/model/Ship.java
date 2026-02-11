package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Ship document model representing a project update post.
 * Contains text content, images, links, and engagement metrics.
 */
@Document(collection = "ships")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ship {

    @Id
    private String id;

    /**
     * ID of the user who created this ship.
     */
    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * ID of the project this ship is associated with.
     */
    @NotBlank(message = "Project ID is required")
    @Indexed
    private String projectId;

    /**
     * Text content of the ship update.
     */
    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 5000, message = "Content must be between 1 and 5000 characters")
    private String content;

    /**
     * Images attached to this ship.
     */
    @Builder.Default
    private List<ImageReference> images = new ArrayList<>();

    /**
     * External links included in this ship.
     */
    @Builder.Default
    private List<ExternalLink> externalLinks = new ArrayList<>();

    /**
     * Number of likes this ship has received.
     */
    @Builder.Default
    private Integer likeCount = 0;

    /**
     * Number of comments on this ship.
     */
    @Builder.Default
    private Integer commentCount = 0;

    /**
     * Trending score calculated from engagement metrics.
     * Used for ranking in trending feeds.
     */
    @Builder.Default
    @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING)
    private Double trendingScore = 0.0;

    /**
     * Timestamp when the edit window expires (10 minutes after creation).
     * After this time, the ship cannot be edited.
     */
    @Indexed
    private Instant editWindowExpires;

    /**
     * Timestamp when the ship was created.
     */
    @Indexed
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Timestamp when the ship was last updated.
     */
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Check if the ship is still within the edit window.
     * 
     * @return true if edits are allowed, false otherwise
     */
    public boolean isEditWindowOpen() {
        return editWindowExpires != null && Instant.now().isBefore(editWindowExpires);
    }
}
