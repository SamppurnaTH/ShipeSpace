package com.shipspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for GitHub OAuth access token response.
 * Represents the response from GitHub's token exchange endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubAccessTokenResponse {

    /**
     * The OAuth access token for GitHub API requests.
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * The type of token (typically "bearer").
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * The scope of access granted by the token.
     */
    private String scope;
}
