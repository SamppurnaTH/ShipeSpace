package com.shipspace.repository;

import com.shipspace.model.ContentScore;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ContentScore document operations.
 * Provides CRUD operations and custom query methods for content score management.
 * 
 * ContentScore stores cached scoring data for content items (ships and projects)
 * including quality scores, performance scores, and engagement metrics.
 * Used by the personalization algorithm to rank content in personalized feeds.
 * 
 * Key operations:
 * - Query content scores by content ID for feed ranking
 * - Query scores by age cohort for performance comparison
 * - Cache validation and updates (1-hour TTL)
 */
@Repository
public interface ContentScoreRepository extends MongoRepository<ContentScore, String> {

    /**
     * Find a content score by content ID.
     * Used to retrieve cached scoring data for a specific piece of content
     * during personalized feed generation.
     * 
     * The content score includes:
     * - Quality score (0.0 to 1.0) based on engagement metrics
     * - Performance score (-1.0 to 1.0) comparing to age cohort median
     * - Age cohort classification ("0-24h", "1-7d", "7-30d", "30d+")
     * - Engagement rate (interactions / impressions)
     * - Last update timestamp for cache validation
     * 
     * Scores are cached with 1-hour TTL to optimize performance.
     * If not found or stale, scores should be recalculated.
     * 
     * @param contentId the ID of the content (ship or project)
     * @return Optional containing the content score if found
     */
    Optional<ContentScore> findByContentId(String contentId);

    /**
     * Find all content scores for a specific age cohort.
     * Used for performance score calculation by comparing a content's
     * engagement rate to the median and standard deviation of its cohort.
     * 
     * Age cohorts ensure fair comparison:
     * - "0-24h": New content (cold-start, receives visibility boost)
     * - "1-7d": Recent content (active engagement period)
     * - "7-30d": Established content (stable engagement)
     * - "30d+": Older content (declining engagement)
     * 
     * Performance score formula:
     * z_score = (content.engagement_rate - cohort_median) / cohort_stddev
     * performance_score = clamp(z_score, -1.0, 1.0)
     * 
     * This allows content that performs well relative to its age
     * to receive increased visibility in personalized feeds.
     * 
     * @param ageCohort the age cohort ("0-24h", "1-7d", "7-30d", "30d+")
     * @return List of content scores in the specified age cohort
     */
    List<ContentScore> findByAgeCohort(String ageCohort);
}
