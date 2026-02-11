package com.shipspace.dto;

import com.shipspace.model.ExternalLink;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for updating an existing ship.
 * All fields are optional - only provided fields will be updated.
 * Updates are only allowed within the 10-minute edit window.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipUpdateRequest {

    /**
     * Updated text content of the ship.
     * Optional - if null, content will not be updated.
     */
    @Size(min = 1, max = 5000, message = "Content must be between 1 and 5000 characters")
    private String content;

    /**
     * Updated external links for the ship.
     * Optional - if null, links will not be updated.
     */
    private List<ExternalLink> externalLinks;
}
