package com.shipspace.dto;

import com.shipspace.model.ExternalLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for project data.
 * Includes project metadata and aggregated information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    /**
     * Unique identifier of the project.
     */
    private String id;

    /**
     * ID of the user who owns this project.
     */
    private String userId;

    /**
     * Name of the project.
     */
    private String name;

    /**
     * Description of the project.
     */
    private String description;

    /**
     * Technology tags associated with the project.
     */
    private List<String> techTags;

    /**
     * External links related to the project.
     */
    private List<ExternalLink> externalLinks;

    /**
     * Number of ships (updates) posted for this project.
     */
    private Integer shipCount;

    /**
     * Timestamp of the last ship posted for this project.
     */
    private Instant lastShipAt;

    /**
     * Timestamp when the project was created.
     */
    private Instant createdAt;

    /**
     * Timestamp when the project was last updated.
     */
    private Instant updatedAt;
}
