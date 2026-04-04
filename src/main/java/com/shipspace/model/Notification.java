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
 * Notification document for user activity alerts.
 * Supports ship likes, comments, new followers, and mentions.
 */
@Document(collection = "notifications")
@CompoundIndex(name = "userId_read_created", def = "{'userId': 1, 'read': 1, 'createdAt': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    /**
     * ID of the user who receives this notification.
     */
    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * Type of notification: SHIP_LIKE, SHIP_COMMENT, NEW_FOLLOWER, MENTION
     */
    @NotBlank(message = "Type is required")
    private String type;

    /**
     * Human-readable notification text.
     */
    @NotBlank(message = "Text is required")
    private String text;

    /**
     * ID of the actor (user who performed the action).
     */
    private String actorId;

    /**
     * Avatar URL of the actor for display.
     */
    private String actorAvatarUrl;

    /**
     * Related entity ID (ship, comment, etc.) for navigation.
     */
    private String targetId;

    /**
     * View/target to navigate to when notification is clicked.
     */
    private String targetView;

    /**
     * Whether the notification has been read.
     */
    @Builder.Default
    private boolean read = false;

    /** TTL: auto-delete after 90 days. */
    @Indexed(expireAfterSeconds = 7776000)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
