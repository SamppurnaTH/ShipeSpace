package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User document model representing a technical builder on the platform.
 * Stores user profile information, social metrics, and authentication data.
 */
@Document(collection = "users")
@CompoundIndex(name = "shipScore_desc", def = "{'shipScore': -1}")
@CompoundIndex(name = "rank_asc", def = "{'rank': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    /**
     * GitHub user ID for OAuth authentication.
     * Unique identifier from GitHub.
     */
    @NotBlank(message = "GitHub ID is required")
    @Indexed(unique = true)
    private String githubId;

    /**
     * Username for the platform.
     * Must be unique across all users.
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Indexed(unique = true)
    private String username;

    /**
     * User's email address from GitHub.
     */
    @Email(message = "Email must be valid")
    private String email;

    /**
     * URL to user's avatar image.
     */
    private String avatarUrl;

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
     * Number of users following this user.
     */
    @Builder.Default
    private Integer followerCount = 0;

    /**
     * Number of users this user is following.
     */
    @Builder.Default
    private Integer followingCount = 0;

    /**
     * Number of projects created by this user.
     */
    @Builder.Default
    private Integer projectCount = 0;

    /**
     * ShipScore points accumulated from shipping activities.
     * Points are earned through project creation, updates, collaborations, and quality comments.
     */
    @Builder.Default
    private Integer shipScore = 0;

    /**
     * User's level calculated from ShipScore.
     * Level ranges from 1 to 30, calculated using the formula: min(30, floor(shipScore / 40) + 1).
     */
    @Builder.Default
    private Integer level = 1;

    /**
     * User's rank tier based on their level.
     * Rank tiers: DOCKED (1-5), LAUNCHER (6-10), ORBITER (11-15),
     * NAVIGATOR (16-20), COMMANDER (21-25), FLEET_ADMIRAL (26-30).
     */
    @Builder.Default
    private RankTier rank = RankTier.DOCKED;

    /**
     * Timestamp of the user's last shipping activity that earned ShipScore.
     * Indexed for the score decay job query (findByLastShipActivityAtBefore).
     */
    @Indexed
    private Instant lastShipActivityAt;

    /**
     * Timestamp when the user's rank was last updated.
     * Updated whenever the user's rank tier changes.
     */
    private Instant rankUpdatedAt;

    /**
     * Timestamp when the user account was created.
     */
    @Indexed
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Timestamp when the user account was last updated.
     */
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
