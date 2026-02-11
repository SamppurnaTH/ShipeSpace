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
 * Project document model representing a persistent project entity.
 * Contains project metadata, links, tech tags, and progress tracking.
 */
@Document(collection = "projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    private String id;

    /**
     * ID of the user who owns this project.
     */
    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * Name of the project.
     */
    @NotBlank(message = "Project name is required")
    @Size(min = 1, max = 100, message = "Project name must be between 1 and 100 characters")
    private String name;

    /**
     * Description of the project.
     */
    @NotBlank(message = "Project description is required")
    @Size(min = 1, max = 2000, message = "Project description must be between 1 and 2000 characters")
    private String description;

    /**
     * Technology tags associated with the project.
     */
    @Builder.Default
    @Indexed
    private List<String> techTags = new ArrayList<>();

    /**
     * External links related to the project (GitHub, website, demo, etc.).
     */
    @Builder.Default
    private List<ExternalLink> externalLinks = new ArrayList<>();

    /**
     * Number of ships (updates) posted for this project.
     */
    @Builder.Default
    private Integer shipCount = 0;

    /**
     * Timestamp of the last ship posted for this project.
     */
    private Instant lastShipAt;

    /**
     * Timestamp when the project was created.
     */
    @Indexed
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Timestamp when the project was last updated.
     */
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
