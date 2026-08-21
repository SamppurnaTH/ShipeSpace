package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ScoreEventLog document model representing a logged scoring event in the progression system.
 * This collection ensures idempotency by preventing duplicate scoring for the same event.
 * 
 * <p>Each log entry records a unique combination of userId, eventType, and entityId,
 * ensuring that the same action (e.g., creating a specific project) can only award
 * points once.
 * 
 * <p>The compound indexes support:
 * <ul>
 *   <li>Idempotency checks: unique constraint on (userId, eventType, entityId)</li>
 *   <li>Daily cap queries: efficient lookups on (userId, createdAt, eventType)</li>
 * </ul>
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 6.1, 6.2</a>
 */
@Document(collection = "score_event_log")
@CompoundIndexes({
    @CompoundIndex(
        name = "unique_event",
        def = "{'userId': 1, 'eventType': 1, 'entityId': 1}",
        unique = true
    ),
    @CompoundIndex(
        name = "user_date_type",
        def = "{'userId': 1, 'createdAt': -1, 'eventType': 1}"
    )
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreEventLog {

    /**
     * Unique identifier for this log entry.
     */
    @Id
    private String id;

    /**
     * ID of the user who triggered the scoring event.
     * This is the user whose ShipScore was (or would have been) affected.
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Type of scoring event that occurred.
     * Determines the point value and whether daily caps apply.
     */
    @NotNull(message = "Event type is required")
    private ScoreEventType eventType;

    /**
     * Unique identifier for the entity that triggered the event.
     * Examples: projectId for PROJECT_CREATED/PROJECT_UPDATED,
     * collaborationId for COLLAB_ACCEPTED, commentId for QUALITY_COMMENT.
     * 
     * <p>This field, combined with userId and eventType, ensures idempotency
     * by preventing the same action from being scored multiple times.
     */
    @NotBlank(message = "Entity ID is required")
    private String entityId;

    /**
     * Number of ShipScore points awarded for this event at the time it was applied.
     * Stored for auditability even if future point economics change.
     */
    private Integer points;

    /**
     * Timestamp when the scoring event was logged.
     * TTL: auto-delete after 90 days — idempotency only needs to cover realistic replay windows.
     */
    @NotNull(message = "Created at timestamp is required")
    @Indexed(expireAfterSeconds = 7776000)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
