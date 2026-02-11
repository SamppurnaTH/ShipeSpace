package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for authentication responses.
 * Contains JWT tokens and user information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * JWT access token for API authentication.
     */
    private String accessToken;

    /**
     * JWT refresh token for obtaining new access tokens.
     */
    private String refreshToken;

    /**
     * Token type (always "Bearer").
     */
    private String tokenType;

    /**
     * Access token expiration time in milliseconds.
     */
    private Long expiresIn;

    /**
     * User information.
     */
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String avatarUrl;
    }
}
