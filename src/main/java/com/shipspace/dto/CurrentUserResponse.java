package com.shipspace.dto;

import com.shipspace.model.RankTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * DTO for current authenticated user response.
 * Used for GET /api/auth/me and PUT /api/users/me.
 * Excludes sensitive fields like raw githubId from generic profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {

    private String id;
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private String location;
    private String websiteUrl;
    private Integer followerCount;
    private Integer followingCount;
    private Integer projectCount;
    private Integer shipScore;
    private Integer level;
    private RankTier rank;
    private Set<String> roles;
    private boolean banned;
    private Instant createdAt;
    private Instant updatedAt;
}
