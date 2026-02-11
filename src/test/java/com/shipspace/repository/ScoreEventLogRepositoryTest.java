package com.shipspace.repository;

import com.shipspace.model.ScoreEventLog;
import com.shipspace.model.ScoreEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ScoreEventLogRepository.
 * Tests custom query methods for score event logging and idempotency checks.
 * 
 * Validates:
 * - existsByUserIdAndEventTypeAndEntityId() for idempotency protection
 * - countByUserIdAndEventTypeAndCreatedAtAfter() for daily cap enforcement
 * - Unique constraint on (userId, eventType, entityId)
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 6.3, 15.1</a>
 */
@DataMongoTest
@ActiveProfiles("test")
class ScoreEventLogRepositoryTest {

    @Autowired
    private ScoreEventLogRepository repository;

    private String testUserId;
    private String testEntityId;
    private Instant now;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        testEntityId = "project456";
        now = Instant.now();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void existsByUserIdAndEventTypeAndEntityId_WhenEventExists_ShouldReturnTrue() {
        // Given: A score event has been logged
        ScoreEventLog event = createScoreEvent(testUserId, ScoreEventType.PROJECT_CREATED, testEntityId, now);
        repository.save(event);

        // When: Checking if the event exists
        boolean exists = repository.existsByUserIdAndEventTypeAndEntityId(
                testUserId, ScoreEventType.PROJECT_CREATED, testEntityId);

        // Then: Should return true
        assertThat(exists).isTrue();
    }

    @Test
    void existsByUserIdAndEventTypeAndEntityId_WhenEventDoesNotExist_ShouldReturnFalse() {
        // Given: No events have been logged
        // (database is empty after setUp)

        // When: Checking if an event exists
        boolean exists = repository.existsByUserIdAndEventTypeAndEntityId(
                testUserId, ScoreEventType.PROJECT_CREATED, testEntityId);

        // Then: Should return false
        assertThat(exists).isFalse();
    }

    @Test
    void existsByUserIdAndEventTypeAndEntityId_WhenDifferentUserId_ShouldReturnFalse() {
        // Given: An event exists for a different user
        ScoreEventLog event = createScoreEvent("otherUser", ScoreEventType.PROJECT_CREATED, testEntityId, now);
        repository.save(event);

        // When: Checking for the same event with different userId
        boolean exists = repository.existsByUserIdAndEventTypeAndEntityId(
                testUserId, ScoreEventType.PROJECT_CREATED, testEntityId);

        // Then: Should return false (different user)
        assertThat(exists).isFalse();
    }

    @Test
    void existsByUserIdAndEventTypeAndEntityId_WhenDifferentEventType_ShouldReturnFalse() {
        // Given: An event exists with a different event type
        ScoreEventLog event = createScoreEvent(testUserId, ScoreEventType.PROJECT_CREATED, testEntityId, now);
        repository.save(event);

        // When: Checking for the same event with different event type
        boolean exists = repository.existsByUserIdAndEventTypeAndEntityId(
                testUserId, ScoreEventType.PROJECT_UPDATED, testEntityId);

        // Then: Should return false (different event type)
        assertThat(exists).isFalse();
    }

    @Test
    void existsByUserIdAndEventTypeAndEntityId_WhenDifferentEntityId_ShouldReturnFalse() {
        // Given: An event exists with a different entity ID
        ScoreEventLog event = createScoreEvent(testUserId, ScoreEventType.PROJECT_CREATED, testEntityId, now);
        repository.save(event);

        // When: Checking for the same event with different entity ID
        boolean exists = repository.existsByUserIdAndEventTypeAndEntityId(
                testUserId, ScoreEventType.PROJECT_CREATED, "differentEntity");

        // Then: Should return false (different entity)
        assertThat(exists).isFalse();
    }

    @Test
    void save_WithDuplicateEvent_ShouldThrowDuplicateKeyException() {
        // Given: An event has already been logged
        ScoreEventLog event1 = createScoreEvent(testUserId, ScoreEventType.PROJECT_CREATED, testEntityId, now);
        repository.save(event1);

        // When: Attempting to save a duplicate event (same userId, eventType, entityId)
        ScoreEventLog event2 = createScoreEvent(testUserId, ScoreEventType.PROJECT_CREATED, testEntityId, 
                now.plus(1, ChronoUnit.HOURS));

        // Then: Should throw DuplicateKeyException due to unique constraint
        assertThatThrownBy(() -> repository.save(event2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void save_WithSameUserAndEntityButDifferentEventType_ShouldSucceed() {
        // Given: An event has been logged
        ScoreEventLog event1 = createScoreEvent(testUserId, ScoreEventType.PROJECT_CREATED, testEntityId, now);
        repository.save(event1);

        // When: Saving an event with same user and entity but different event type
        ScoreEventLog event2 = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, testEntityId, now);
        ScoreEventLog saved = repository.save(event2);

        // Then: Should succeed (different event type makes it unique)
        assertThat(saved.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void countByUserIdAndEventTypeAndCreatedAtAfter_WhenEventsExist_ShouldReturnCorrectCount() {
        // Given: Multiple PROJECT_UPDATED events for a user today
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        
        ScoreEventLog event1 = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "entity1", 
                startOfDay.plus(1, ChronoUnit.HOURS));
        ScoreEventLog event2 = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "entity2", 
                startOfDay.plus(2, ChronoUnit.HOURS));
        ScoreEventLog event3 = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "entity3", 
                startOfDay.plus(3, ChronoUnit.HOURS));

        repository.save(event1);
        repository.save(event2);
        repository.save(event3);

        // When: Counting events since start of day
        long count = repository.countByUserIdAndEventTypeAndCreatedAtAfter(
                testUserId, ScoreEventType.PROJECT_UPDATED, startOfDay);

        // Then: Should return correct count
        assertThat(count).isEqualTo(3);
    }

    @Test
    void countByUserIdAndEventTypeAndCreatedAtAfter_WhenNoEventsExist_ShouldReturnZero() {
        // Given: No events have been logged
        // (database is empty after setUp)

        // When: Counting events
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        long count = repository.countByUserIdAndEventTypeAndCreatedAtAfter(
                testUserId, ScoreEventType.PROJECT_UPDATED, startOfDay);

        // Then: Should return zero
        assertThat(count).isEqualTo(0);
    }

    @Test
    void countByUserIdAndEventTypeAndCreatedAtAfter_WhenEventsBeforeThreshold_ShouldReturnZero() {
        // Given: Events exist but are before the threshold
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        ScoreEventLog oldEvent = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, testEntityId, yesterday);
        repository.save(oldEvent);

        // When: Counting events after today's start
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        long count = repository.countByUserIdAndEventTypeAndCreatedAtAfter(
                testUserId, ScoreEventType.PROJECT_UPDATED, startOfDay);

        // Then: Should return zero (old event not counted)
        assertThat(count).isEqualTo(0);
    }

    @Test
    void countByUserIdAndEventTypeAndCreatedAtAfter_WhenDifferentEventType_ShouldNotCount() {
        // Given: Events of different types exist
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        
        ScoreEventLog projectCreated = createScoreEvent(testUserId, ScoreEventType.PROJECT_CREATED, "entity1", 
                startOfDay.plus(1, ChronoUnit.HOURS));
        ScoreEventLog projectUpdated = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "entity2", 
                startOfDay.plus(2, ChronoUnit.HOURS));

        repository.save(projectCreated);
        repository.save(projectUpdated);

        // When: Counting only PROJECT_UPDATED events
        long count = repository.countByUserIdAndEventTypeAndCreatedAtAfter(
                testUserId, ScoreEventType.PROJECT_UPDATED, startOfDay);

        // Then: Should count only PROJECT_UPDATED events
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countByUserIdAndEventTypeAndCreatedAtAfter_WhenDifferentUser_ShouldNotCount() {
        // Given: Events for different users exist
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        
        ScoreEventLog userEvent = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "entity1", 
                startOfDay.plus(1, ChronoUnit.HOURS));
        ScoreEventLog otherUserEvent = createScoreEvent("otherUser", ScoreEventType.PROJECT_UPDATED, "entity2", 
                startOfDay.plus(2, ChronoUnit.HOURS));

        repository.save(userEvent);
        repository.save(otherUserEvent);

        // When: Counting events for specific user
        long count = repository.countByUserIdAndEventTypeAndCreatedAtAfter(
                testUserId, ScoreEventType.PROJECT_UPDATED, startOfDay);

        // Then: Should count only events for the specified user
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countByUserIdAndEventTypeAndCreatedAtAfter_ForDailyCapEnforcement_ShouldDetectCapReached() {
        // Given: User has reached the daily cap of 5 PROJECT_UPDATED events
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        
        // Create events after the start of day (not at exact start time)
        for (int i = 0; i < 5; i++) {
            ScoreEventLog event = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "entity" + i, 
                    startOfDay.plus(i + 1, ChronoUnit.HOURS));
            repository.save(event);
        }

        // When: Counting events to check daily cap (using startOfDay as threshold)
        long count = repository.countByUserIdAndEventTypeAndCreatedAtAfter(
                testUserId, ScoreEventType.PROJECT_UPDATED, startOfDay);

        // Then: Should detect that cap (5) has been reached
        assertThat(count).isEqualTo(5);
        assertThat(count).isGreaterThanOrEqualTo(ScoreEventType.PROJECT_UPDATED.getDailyCap());
    }

    @Test
    void countByUserIdAndEventTypeAndCreatedAtAfter_AcrossDayBoundary_ShouldCountOnlyAfterThreshold() {
        // Given: Events spanning multiple days
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant startOfToday = now.truncatedTo(ChronoUnit.DAYS);
        
        // 3 events yesterday
        for (int i = 0; i < 3; i++) {
            ScoreEventLog event = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "yesterday" + i, 
                    yesterday.plus(i, ChronoUnit.HOURS));
            repository.save(event);
        }
        
        // 2 events today (after start of day, not at exact start time)
        for (int i = 0; i < 2; i++) {
            ScoreEventLog event = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "today" + i, 
                    startOfToday.plus(i + 1, ChronoUnit.HOURS));
            repository.save(event);
        }

        // When: Counting events since start of today
        long count = repository.countByUserIdAndEventTypeAndCreatedAtAfter(
                testUserId, ScoreEventType.PROJECT_UPDATED, startOfToday);

        // Then: Should count only today's events (daily cap resets)
        assertThat(count).isEqualTo(2);
    }

    @Test
    void save_ShouldPersistEventWithAllFields() {
        // Given: A complete score event log
        ScoreEventLog event = ScoreEventLog.builder()
                .userId(testUserId)
                .eventType(ScoreEventType.PROJECT_CREATED)
                .entityId(testEntityId)
                .createdAt(now)
                .build();

        // When: Saving the event
        ScoreEventLog saved = repository.save(event);

        // Then: All fields should be persisted correctly
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(testUserId);
        assertThat(saved.getEventType()).isEqualTo(ScoreEventType.PROJECT_CREATED);
        assertThat(saved.getEntityId()).isEqualTo(testEntityId);
        assertThat(saved.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void save_WithAllEventTypes_ShouldPersistCorrectly() {
        // Given: Events of all types
        ScoreEventLog projectCreated = createScoreEvent(testUserId, ScoreEventType.PROJECT_CREATED, "entity1", now);
        ScoreEventLog projectUpdated = createScoreEvent(testUserId, ScoreEventType.PROJECT_UPDATED, "entity2", now);
        ScoreEventLog collabAccepted = createScoreEvent(testUserId, ScoreEventType.COLLAB_ACCEPTED, "entity3", now);
        ScoreEventLog qualityComment = createScoreEvent(testUserId, ScoreEventType.QUALITY_COMMENT, "entity4", now);

        // When: Saving all event types
        repository.save(projectCreated);
        repository.save(projectUpdated);
        repository.save(collabAccepted);
        repository.save(qualityComment);

        // Then: All event types should be persisted
        assertThat(repository.count()).isEqualTo(4);
    }

    // Helper method to create a test score event log
    private ScoreEventLog createScoreEvent(String userId, ScoreEventType eventType, String entityId, Instant createdAt) {
        return ScoreEventLog.builder()
                .userId(userId)
                .eventType(eventType)
                .entityId(entityId)
                .createdAt(createdAt)
                .build();
    }
}
