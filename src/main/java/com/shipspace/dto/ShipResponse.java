package com.shipspace.dto;

import com.shipspace.model.ExternalLink;
import com.shipspace.model.ImageReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for ship data.
 * Includes ship content, engagement metrics, and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipResponse {

    /**
     * Unique identifier of the ship.
     */
    private String id;

    /**
     * ID of the user who created this ship.
     */
    private String userId;

    /**
     * ID of the project this ship is associated with.
     */
    private String projectId;

    /**
     * Text content of the ship update.
     */
    private String content;

    /**
     * Images attached to this ship.
     */
    private List<ImageReference> images;

    /**
     * External links included in this ship.
     */
    private List<ExternalLink> externalLinks;

    /**
     * Number of likes this ship has received.
     */
    private Integer likeCount;

    /**
     * Number of comments on this ship.
     */
    private Integer commentCount;

    /**
     * Trending score calculated from engagement metrics.
     */
    private Double trendingScore;

    /**
     * Whether the ship is still within the edit window.
     */
    private Boolean editWindowOpen;

    /**
     * Timestamp when the edit window expires.
     */
    private Instant editWindowExpires;

    /**
     * Timestamp when the ship was created.
     */
    private Instant createdAt;

    /**
     * Timestamp when the ship was last updated.
     */
    private Instant updatedAt;
}
