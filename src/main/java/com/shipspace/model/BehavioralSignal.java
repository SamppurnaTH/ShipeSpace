package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * BehavioralSignal document model representing individual user actions
 * for behavioral tracking and personalization.
 * 
 * This model records:
 * - User interactions (likes, comments, views, follows, skips)
 * - Content context (tech tags, builder ID)
 * - Temporal information for time-based analysis
 * 
 * Signals are used to build UserInterestProfile and drive personalized recommendations.
 */
@Document(collection = "behavioral_signals")
@CompoundIndex(name = "user_timestamp_idx", def = "{'userId': 1, 'timestamp': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralSignal {

    @Id
    private String id;

    /**
     * ID of the user who performed the action.
     * Indexed for efficient user-based queries.
     */
    @Indexed
    private String userId;

    /**
     * Type of behavioral signal.
     * Indexed for analytics and signal-type filtering.
     */
    @Indexed
    private SignalType signalType;

    /**
     * ID of the content (ship or project) the signal relates to.
     * Indexed for content-based queries.
     */
    @Indexed
    private String contentId;

    /**
     * Type of content (SHIP or PROJECT).
     * Determines which collection to query for content details.
     */
    private ContentType contentType;

    /**
     * Tech tags associated with the content at the time of signal.
     * Captured at signal time to preserve historical context even if content changes.
     */
    @Builder.Default
    private List<String> techTags = new ArrayList<>();

    /**
     * Builder ID (user ID of content creator).
     * Used for FOLLOW signals and builder-based recommendations.
     */
    private String builderId;

    /**
     * Duration in seconds for VIEW signals.
     * Only populated for VIEW signal type.
     * Views are only recorded if duration exceeds 10 seconds.
     */
    private Integer durationSeconds;

    /**
     * Timestamp when the signal occurred.
     * Indexed for temporal queries and time-based analysis.
     * TTL of 90 days — signals older than 90 days are automatically deleted by MongoDB.
     */
    @Indexed(expireAfterSeconds = 7776000) // 90 days
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * SignalType enum representing different user actions.
     * Each type has different weight in interest profile calculations:
     * - LIKE: 1.0 weight
     * - COMMENT: 2.0 weight (stronger signal of interest)
     * - VIEW: 0.5 weight (passive engagement)
     * - FOLLOW: 3.0 weight (strongest signal)
     * - SKIP: -0.1 weight (negative signal for future ML)
     */
    public enum SignalType {
        /**
         * User liked content.
         * Indicates positive engagement and interest.
         */
        LIKE,

        /**
         * User commented on content.
         * Strong signal of engagement and interest.
         */
        COMMENT,

        /**
         * User viewed content for more than 10 seconds.
         * Passive but meaningful engagement signal.
         */
        VIEW,

        /**
         * User followed builder after viewing their content.
         * Strongest signal of interest in builder's work.
         */
        FOLLOW,

        /**
         * User scrolled past content without interaction.
         * Negative signal for future ML training (Phase 2).
         */
        SKIP
    }

    /**
     * ContentType enum representing the type of content.
     * Determines which collection to query for content details.
     */
    public enum ContentType {
        /**
         * Content is a Ship (project update post).
         */
        SHIP,

        /**
         * Content is a Project (full project profile).
         */
        PROJECT
    }
}
