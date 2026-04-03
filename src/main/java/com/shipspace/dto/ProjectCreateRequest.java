package com.shipspace.dto;

import com.shipspace.model.ExternalLink;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for creating a new project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {

    @NotBlank(message = "Project name is required")
    @Size(min = 1, max = 100, message = "Project name must be between 1 and 100 characters")
    @Pattern(
        regexp = "^(?!.*<\\s*script).*$",
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "Project name contains invalid content"
    )
    private String name;

    @NotBlank(message = "Project description is required")
    @Size(min = 1, max = 2000, message = "Project description must be between 1 and 2000 characters")
    @Pattern(
        regexp = "^(?!.*<\\s*script).*$",
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "Project description contains invalid content"
    )
    private String description;

    @Builder.Default
    private List<String> techTags = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<ExternalLink> externalLinks = new ArrayList<>();
}
