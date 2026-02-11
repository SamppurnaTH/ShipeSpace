package com.shipspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user profile update requests.
 * Contains optional fields that users can update in their profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    /**
     * User's email address.
     */
    @Email(message = "Email must be valid")
    private String email;

    /**
     * User's bio/description.
     */
    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;

    /**
     * User's location.
     */
    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;

    /**
     * User's personal website URL.
     */
    private String websiteUrl;

    /**
     * URL to user's avatar image.
     */
    private String avatarUrl;
}
