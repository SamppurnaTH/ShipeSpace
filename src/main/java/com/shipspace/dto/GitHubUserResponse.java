package com.shipspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for GitHub user data response.
 * Represents the user information retrieved from GitHub API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubUserResponse {

    /**
     * GitHub user ID (unique identifier).
     */
    private Long id;

    /**
     * GitHub username (login).
     */
    private String login;

    /**
     * User's email address.
     */
    private String email;

    /**
     * User's display name.
     */
    private String name;

    /**
     * URL to user's avatar image.
     */
    @JsonProperty("avatar_url")
    private String avatarUrl;

    /**
     * User's bio/description.
     */
    private String bio;

    /**
     * User's location.
     */
    private String location;

    /**
     * User's blog/website URL.
     */
    private String blog;

    /**
     * User's company.
     */
    private String company;

    /**
     * Number of public repositories.
     */
    @JsonProperty("public_repos")
    private Integer publicRepos;

    /**
     * Number of followers on GitHub.
     */
    private Integer followers;

    /**
     * Number of users following on GitHub.
     */
    private Integer following;
}
