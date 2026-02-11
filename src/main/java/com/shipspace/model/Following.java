package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Following document model representing a social following relationship.
 * Tracks which users follow which other users.
 */
@Document(collection = "following")
@CompoundIndex(name = "follower_followee_idx", def = "{'followerId': 1, 'followeeId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Following {

    @Id
    private String id;

    /**
     * ID of the user who is following.
     */
    @NotBlank(message = "Follower ID is required")
    @Indexed
    private String followerId;

    /**
     * ID of the user being followed.
     */
    @NotBlank(message = "Followee ID is required")
    @Indexed
    private String followeeId;

    /**
     * Timestamp when the following relationship was created.
     */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
