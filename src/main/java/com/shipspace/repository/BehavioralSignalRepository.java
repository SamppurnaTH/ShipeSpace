package com.shipspace.repository;

import com.shipspace.model.BehavioralSignal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository interface for BehavioralSignal document operations.
 * Provides CRUD operations and custom query methods for behavioral signal tracking.
 * 
 * BehavioralSignal records individual user actions (likes, comments, views, follows, skips)
 * and is used by the personalization algorithm to build user interest profiles
 * and generate personalized content recommendations.
 * 
 * Key operations:
 * - Query user's signal history ordered by timestamp
 * - Count total signals for cold-start detection
 * - Query recent signals for time-based analysis
 */
@Repository
public interface BehavioralSignalRepository extends MongoRepository<BehavioralSignal, String> {

    /**
     * Find all behavioral signals for a specific user, ordered by timestamp descending.
     * Used to retrieve a user's recent activity history for interest profile updates
     * and behavioral analysis.
     * 
     * The signals are ordered from most recent to oldest, allowing efficient
     * retrieval of recent user behavior patterns.
     * 
     * @param userId the ID of the user
     * @param pageable pagination parameters (optional, can be Pageable.unpaged())
     * @return List of behavioral signals ordered by timestamp descending
     */
    List<BehavioralSignal> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    /**
     * Count the total number of behavioral signals for a specific user.
     * Used for cold-start detection - users with fewer than 10 signals
     * are classified as cold-start users and receive trending content
     * instead of personalized recommendations.
     * 
     * @param userId the ID of the user
     * @return the count of behavioral signals for the user
     */
    long countByUserId(String userId);

    /**
     * Find all behavioral signals for a specific user after a given timestamp.
     * Used for time-based analysis, such as:
     * - Excluding recently viewed content (past 7 days)
     * - Calculating recent engagement patterns
     * - Time-windowed interest profile updates
     * 
     * @param userId the ID of the user
     * @param after the timestamp threshold (exclusive)
     * @return List of behavioral signals after the specified timestamp
     */
    List<BehavioralSignal> findByUserIdAndTimestampAfter(String userId, Instant after);


    /**
     * Count the number of VIEW signals for a specific content item.
     * Used for calculating quality scores and engagement metrics.
     *
     * @param contentId the ID of the content
     * @param signalType the signal type (VIEW)
     * @return the count of VIEW signals for the content
     */
    long countByContentIdAndSignalType(String contentId, BehavioralSignal.SignalType signalType);

}
