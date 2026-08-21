package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for builder discovery responses on the People page.
 * Contains only publicly safe user information optimized for discovery cards.
 * Deliberately excludes: email, githubId, roles, banned status, internal IDs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuilderDiscoveryResponse {

    private String id;
    private String username;
    private String avatarUrl;
    private String bio;
    private String location;
    private Integer followerCount;
    private Integer projectCount;
    private Integer shipScore;
    private Integer level;
    private String rank;
    private Boolean isFollowing;
    private Instant createdAt;
}
