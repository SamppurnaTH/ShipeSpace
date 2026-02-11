package com.shipspace.repository;

import com.shipspace.model.ScoreEventLog;
import com.shipspace.model.ScoreEventType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Repository interface for ScoreEventLog document operations.
 * Provides CRUD operations and custom query methods for score event logging.
 * 
 * <p>ScoreEventLog tracks all scoring events to ensure idempotency and enforce
 * daily caps on repeatable actions. The repository supports:
 * <ul>
 *   <li>Idempotency checks: verify if an event has already been logged</li>
 *   <li>Daily cap enforcement: count events within a time window</li>
 * </ul>
 * 
 * <p>Key operations:
 * <ul>
 *   <li>Check if a specific event (userId, eventType, entityId) has been logged</li>
 *   <li>Count events of a specific type for a user since a given timestamp</li>
 * </ul>
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 6.3, 15.1</a>
 */
@Repository
public interface ScoreEventLogRepository extends MongoRepository<ScoreEventLog, String> {

    /**
     * Check if a score event has already been logged for the given combination
     * of userId, eventType, and entityId.
     * 
     * <p>This method is used to enforce idempotency by preventing duplicate scoring
     * for the same event. For example, creating the same project twice should only
     * award points once.
     * 
     * <p>The unique compound index on (userId, eventType, entityId) ensures efficient
     * lookups and prevents duplicate entries at the database level.
     * 
     * @param userId the ID of the user who triggered the event
     * @param eventType the type of scoring event
     * @param entityId the unique identifier for the entity (project, comment, etc.)
     * @return true if an event with this combination has been logged, false otherwise
     */
    boolean existsByUserIdAndEventTypeAndEntityId(
        String userId,
        ScoreEventType eventType,
        String entityId
    );

    /**
     * Count the number of score events of a specific type for a user that occurred
     * after a given timestamp.
     * 
     * <p>This method is used to enforce daily caps on repeatable actions. For example,
     * PROJECT_UPDATED events are limited to 5 per user per calendar day. By passing
     * the start of the current day as the timestamp, we can count how many events
     * have occurred today.
     * 
     * <p>The compound index on (userId, createdAt, eventType) ensures efficient
     * time-range queries.
     * 
     * @param userId the ID of the user
     * @param eventType the type of scoring event to count
     * @param startOfDay the timestamp threshold (typically start of current day in UTC)
     * @return the count of events matching the criteria
     */
    long countByUserIdAndEventTypeAndCreatedAtAfter(
        String userId,
        ScoreEventType eventType,
        Instant startOfDay
    );
}
