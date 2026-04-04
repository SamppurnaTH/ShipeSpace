package com.shipspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user profile update requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Email(message = "Email must be valid")
    private String email;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    @Pattern(
        regexp = "^(?!.*<\\s*script).*$",
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "Bio contains invalid content"
    )
    private String bio;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    @Pattern(
        regexp = "^(?!.*<\\s*script).*$",
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "Location contains invalid content"
    )
    private String location;

    @Pattern(
        regexp = "^(https://.*)?$",
        message = "Website URL must use HTTPS"
    )
    @Size(max = 200, message = "Website URL must not exceed 200 characters")
    private String websiteUrl;

    @Pattern(
        regexp = "^(https://.*)?$",
        message = "Avatar URL must use HTTPS"
    )
    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avatarUrl;
}
