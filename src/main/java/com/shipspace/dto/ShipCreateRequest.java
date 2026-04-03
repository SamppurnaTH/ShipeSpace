package com.shipspace.dto;

import com.shipspace.model.ExternalLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for creating a new ship.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipCreateRequest {

    @NotBlank(message = "Project ID is required")
    private String projectId;

    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 5000, message = "Content must be between 1 and 5000 characters")
    @Pattern(
        regexp = "^(?!.*<\\s*script).*$",
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "Content contains invalid content"
    )
    private String content;

    @Builder.Default
    private List<ExternalLink> externalLinks = new ArrayList<>();
}
