package com.shipspace.dto;

import com.shipspace.model.ExternalLink;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for updating an existing project.
 * All fields are optional - only provided fields will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUpdateRequest {

    /**
     * Updated name of the project.
     */
    @Size(min = 1, max = 100, message = "Project name must be between 1 and 100 characters")
    private String name;

    /**
     * Updated description of the project.
     */
    @Size(min = 1, max = 2000, message = "Project description must be between 1 and 2000 characters")
    private String description;

    /**
     * Updated technology tags.
     */
    private List<String> techTags;

    /**
     * Updated external links.
     */
    @Valid
    private List<ExternalLink> externalLinks;
}
