package com.shipspace.dto;

import com.shipspace.model.ExternalLink;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for creating a new project.
 * Contains all required and optional fields for project creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {

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
    private List<String> techTags = new ArrayList<>();

    /**
     * External links related to the project (GitHub, website, demo, etc.).
     */
    @Valid
    @Builder.Default
    private List<ExternalLink> externalLinks = new ArrayList<>();
}
