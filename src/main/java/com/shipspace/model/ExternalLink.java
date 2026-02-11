package com.shipspace.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded document representing an external link.
 * Used in Project and Ship documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalLink {

    /**
     * Type of the link (e.g., "github", "website", "demo").
     */
    @Size(max = 50, message = "Link type must not exceed 50 characters")
    private String type;

    /**
     * URL of the external resource.
     */
    @NotBlank(message = "URL is required")
    private String url;

    /**
     * Display title for the link.
     */
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    /**
     * Optional description for the link.
     */
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
