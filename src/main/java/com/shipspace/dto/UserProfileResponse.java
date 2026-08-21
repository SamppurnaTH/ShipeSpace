package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for public user profile responses.
 * Contains user information that can be publicly viewed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String id;
    private String username;
    private String avatarUrl;
    private String bio;
    private String location;
    private String websiteUrl;
    private Integer followerCount;
    private Integer followingCount;
    private Integer projectCount;
    private Integer shipScore;
    private Integer level;
    private String rank;
    private Boolean isFollowing;
    private Instant createdAt;
}
