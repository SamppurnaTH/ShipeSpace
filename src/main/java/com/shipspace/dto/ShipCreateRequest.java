package com.shipspace.dto;

import com.shipspace.model.ExternalLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for creating a new ship.
 * Contains text content, external links, and project association.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipCreateRequest {

    /**
     * ID of the project this ship is associated with.
     * Required - ships must be associated with a project.
     */
    @NotBlank(message = "Project ID is required")
    private String projectId;

    /**
     * Text content of the ship update.
     * Required - ships must have content.
     */
    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 5000, message = "Content must be between 1 and 5000 characters")
    private String content;

    /**
     * External links included in this ship.
     * Optional - can be empty list.
     */
    @Builder.Default
    private List<ExternalLink> externalLinks = new ArrayList<>();
}
