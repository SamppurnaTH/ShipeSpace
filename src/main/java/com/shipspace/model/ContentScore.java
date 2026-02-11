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

/**
 * ContentScore document model representing cached scoring data for content items.
 * 
 * This model stores:
 * - Quality scores based on engagement metrics (likes, comments, views, follows)
 * - Performance scores comparing engagement to age cohort medians
 * - Age cohort classification for fair performance comparison
 * - Engagement rate metrics
 * 
 * Scores are cached with 1-hour TTL to optimize feed generation performance.
 * Updated by scheduled jobs and on-demand when new engagement occurs.
 */
@Document(collection = "content_scores")
@CompoundIndex(name = "content_updated_idx", def = "{'contentId': 1, 'updatedAt': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentScore {

    @Id
    private String id;

    /**
     * ID of the content (ship or project) this score relates to.
     * Unique identifier for fast lookups.
     */
    @Indexed(unique = true)
    private String contentId;

    /**
     * Type of content (SHIP or PROJECT).
     * Determines which collection the content belongs to.
     */
    private BehavioralSignal.ContentType contentType;

    /**
     * Quality score (0.0 to 1.0) based on engagement metrics.
     * 
     * Calculated using formula:
     * raw_quality = (likes * 1.0) + (comments * 2.0) + (views * 0.5) + (follows * 3.0)
     * age_factor = max(1.0, hours_since_creation)
     * normalized_quality = raw_quality / sqrt(age_factor)
     * quality_score = min(1.0, normalized_quality / 100.0)
     * 
     * Higher scores indicate better content quality.
     * Normalized by age to prevent bias toward older content.
     */
    private Double qualityScore;

    /**
     * Performance score (-1.0 to 1.0) based on engagement rate comparison.
     * 
     * Calculated using formula:
     * z_score = (content.engagement_rate - cohort_median) / cohort_stddev
     * performance_score = clamp(z_score, -1.0, 1.0)
     * 
     * Positive scores indicate above-average performance for the age cohort.
     * Negative scores indicate below-average performance.
     * Used to reward content that drives engagement.
     */
    private Double performanceScore;

    /**
     * Age cohort for performance comparison.
     * 
     * Cohorts:
     * - "0-24h": Content less than 24 hours old (cold-start content)
     * - "1-7d": Content between 1 and 7 days old
     * - "7-30d": Content between 7 and 30 days old
     * - "30d+": Content older than 30 days
     * 
     * Indexed for efficient cohort-based queries.
     */
    @Indexed
    private String ageCohort;

    /**
     * Engagement rate (interactions / impressions).
     * 
     * Calculated as:
     * engagement_rate = (likes + comments + follows) / views
     * 
     * Used for performance score calculation and cohort comparison.
     * Higher rates indicate more engaging content.
     */
    private Double engagementRate;

    /**
     * Timestamp when scores were last calculated.
     * 
     * Used for:
     * - Cache invalidation (1-hour TTL)
     * - Determining when recalculation is needed
     * - Tracking score freshness
     * 
     * Part of compound index for efficient cache validation queries.
     */
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
