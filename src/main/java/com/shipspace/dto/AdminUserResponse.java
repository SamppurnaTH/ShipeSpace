package com.shipspace.dto;

import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * DTO for admin user list/detail responses.
 * Keeps the admin API on an explicit allowlist instead of returning the User entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private String id;
    private String githubId;
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

    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .githubId(user.getGithubId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .location(user.getLocation())
                .websiteUrl(user.getWebsiteUrl())
                .followerCount(user.getFollowerCount())
                .followingCount(user.getFollowingCount())
                .projectCount(user.getProjectCount())
                .shipScore(user.getShipScore())
                .level(user.getLevel())
                .rank(user.getRank())
                .roles(user.getRoles())
                .banned(user.isBanned())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
