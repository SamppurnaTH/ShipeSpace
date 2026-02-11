package com.shipspace.repository;

import com.shipspace.model.BehavioralSignal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for BehavioralSignalRepository.
 * Tests custom query methods for behavioral signal tracking and retrieval.
 * 
 * Validates:
 * - findByUserIdOrderByTimestampDesc() for retrieving user signal history
 * - countByUserId() for cold-start detection
 * - findByUserIdAndTimestampAfter() for time-based queries
 */
@DataMongoTest
@ActiveProfiles("test")
class BehavioralSignalRepositoryTest {

    @Autowired
    private BehavioralSignalRepository repository;

    private String testUserId;
    private String testContentId;
    private Instant now;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        testContentId = "ship456";
        now = Instant.now();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void findByUserIdOrderByTimestampDesc_WhenSignalsExist_ShouldReturnOrderedByTimestamp() {
        // Given: Multiple signals for a user at different times
        BehavioralSignal signal1 = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, 
                now.minus(3, ChronoUnit.HOURS));
        BehavioralSignal signal2 = createSignal(testUserId, BehavioralSignal.SignalType.COMMENT, 
                now.minus(1, ChronoUnit.HOURS));
        BehavioralSignal signal3 = createSignal(testUserId, BehavioralSignal.SignalType.VIEW, 
                now.minus(2, ChronoUnit.HOURS));

        repository.saveAll(Arrays.asList(signal1, signal2, signal3));

        // When: Finding signals ordered by timestamp descending
        List<BehavioralSignal> results = repository.findByUserIdOrderByTimestampDesc(
                testUserId, Pageable.unpaged());

        // Then: Signals should be ordered from most recent to oldest
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSignalType()).isEqualTo(BehavioralSignal.SignalType.COMMENT);
        assertThat(results.get(1).getSignalType()).isEqualTo(BehavioralSignal.SignalType.VIEW);
        assertThat(results.get(2).getSignalType()).isEqualTo(BehavioralSignal.SignalType.LIKE);
        
        // Verify timestamps are in descending order
        assertThat(results.get(0).getTimestamp()).isAfter(results.get(1).getTimestamp());
        assertThat(results.get(1).getTimestamp()).isAfter(results.get(2).getTimestamp());
    }

    @Test
    void findByUserIdOrderByTimestampDesc_WithPagination_ShouldReturnLimitedResults() {
        // Given: Multiple signals for a user
        for (int i = 0; i < 10; i++) {
            BehavioralSignal signal = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, 
                    now.minus(i, ChronoUnit.HOURS));
            repository.save(signal);
        }

        // When: Finding signals with pagination (page size 5)
        Pageable pageable = PageRequest.of(0, 5);
        List<BehavioralSignal> results = repository.findByUserIdOrderByTimestampDesc(
                testUserId, pageable);

        // Then: Should return only 5 most recent signals
        assertThat(results).hasSize(5);
    }

    @Test
    void findByUserIdOrderByTimestampDesc_WhenNoSignalsExist_ShouldReturnEmptyList() {
        // Given: No signals exist for the user
        // (database is empty after setUp)

        // When: Finding signals
        List<BehavioralSignal> results = repository.findByUserIdOrderByTimestampDesc(
                testUserId, Pageable.unpaged());

        // Then: Should return empty list
        assertThat(results).isEmpty();
    }

    @Test
    void findByUserIdOrderByTimestampDesc_WhenMultipleUsersExist_ShouldReturnOnlyUserSignals() {
        // Given: Signals for multiple users
        BehavioralSignal userSignal1 = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, now);
        BehavioralSignal userSignal2 = createSignal(testUserId, BehavioralSignal.SignalType.COMMENT, 
                now.minus(1, ChronoUnit.HOURS));
        BehavioralSignal otherUserSignal = createSignal("otherUser", BehavioralSignal.SignalType.VIEW, now);

        repository.saveAll(Arrays.asList(userSignal1, userSignal2, otherUserSignal));

        // When: Finding signals for specific user
        List<BehavioralSignal> results = repository.findByUserIdOrderByTimestampDesc(
                testUserId, Pageable.unpaged());

        // Then: Should return only signals for the specified user
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(signal -> signal.getUserId().equals(testUserId));
    }

    @Test
    void countByUserId_WhenSignalsExist_ShouldReturnCorrectCount() {
        // Given: Multiple signals for a user
        BehavioralSignal signal1 = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, now);
        BehavioralSignal signal2 = createSignal(testUserId, BehavioralSignal.SignalType.COMMENT, now);
        BehavioralSignal signal3 = createSignal(testUserId, BehavioralSignal.SignalType.VIEW, now);

        repository.saveAll(Arrays.asList(signal1, signal2, signal3));

        // When: Counting signals for the user
        long count = repository.countByUserId(testUserId);

        // Then: Should return correct count
        assertThat(count).isEqualTo(3);
    }

    @Test
    void countByUserId_WhenNoSignalsExist_ShouldReturnZero() {
        // Given: No signals exist for the user
        // (database is empty after setUp)

        // When: Counting signals
        long count = repository.countByUserId(testUserId);

        // Then: Should return zero
        assertThat(count).isEqualTo(0);
    }

    @Test
    void countByUserId_ForColdStartDetection_ShouldIdentifyNewUsers() {
        // Given: A user with fewer than 10 signals (cold-start threshold)
        for (int i = 0; i < 5; i++) {
            BehavioralSignal signal = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, now);
            repository.save(signal);
        }

        // When: Counting signals
        long count = repository.countByUserId(testUserId);

        // Then: Count should be less than 10 (cold-start user)
        assertThat(count).isLessThan(10);
    }

    @Test
    void countByUserId_WhenMultipleUsersExist_ShouldCountOnlyUserSignals() {
        // Given: Signals for multiple users
        repository.save(createSignal(testUserId, BehavioralSignal.SignalType.LIKE, now));
        repository.save(createSignal(testUserId, BehavioralSignal.SignalType.COMMENT, now));
        repository.save(createSignal("otherUser", BehavioralSignal.SignalType.VIEW, now));
        repository.save(createSignal("otherUser", BehavioralSignal.SignalType.LIKE, now));

        // When: Counting signals for specific user
        long count = repository.countByUserId(testUserId);

        // Then: Should count only signals for the specified user
        assertThat(count).isEqualTo(2);
    }

    @Test
    void findByUserIdAndTimestampAfter_WhenRecentSignalsExist_ShouldReturnOnlyAfterTimestamp() {
        // Given: Signals at different times
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        BehavioralSignal oldSignal = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, sevenDaysAgo);
        BehavioralSignal recentSignal1 = createSignal(testUserId, BehavioralSignal.SignalType.COMMENT, threeDaysAgo);
        BehavioralSignal recentSignal2 = createSignal(testUserId, BehavioralSignal.SignalType.VIEW, oneDayAgo);

        repository.saveAll(Arrays.asList(oldSignal, recentSignal1, recentSignal2));

        // When: Finding signals after 5 days ago
        Instant fiveDaysAgo = now.minus(5, ChronoUnit.DAYS);
        List<BehavioralSignal> results = repository.findByUserIdAndTimestampAfter(
                testUserId, fiveDaysAgo);

        // Then: Should return only signals after the threshold
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(signal -> signal.getTimestamp().isAfter(fiveDaysAgo));
    }

    @Test
    void findByUserIdAndTimestampAfter_ForViewedContentExclusion_ShouldFindRecentViews() {
        // Given: VIEW signals at different times (for excluding recently viewed content)
        Instant eightDaysAgo = now.minus(8, ChronoUnit.DAYS);
        Instant fiveDaysAgo = now.minus(5, ChronoUnit.DAYS);

        BehavioralSignal oldView = BehavioralSignal.builder()
                .userId(testUserId)
                .signalType(BehavioralSignal.SignalType.VIEW)
                .contentId("ship1")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .timestamp(eightDaysAgo)
                .build();

        BehavioralSignal recentView = BehavioralSignal.builder()
                .userId(testUserId)
                .signalType(BehavioralSignal.SignalType.VIEW)
                .contentId("ship2")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .timestamp(fiveDaysAgo)
                .build();

        repository.saveAll(Arrays.asList(oldView, recentView));

        // When: Finding views in the past 7 days
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        List<BehavioralSignal> results = repository.findByUserIdAndTimestampAfter(
                testUserId, sevenDaysAgo);

        // Then: Should return only recent views (for content exclusion)
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getContentId()).isEqualTo("ship2");
    }

    @Test
    void findByUserIdAndTimestampAfter_WhenNoRecentSignals_ShouldReturnEmptyList() {
        // Given: Only old signals exist
        Instant tenDaysAgo = now.minus(10, ChronoUnit.DAYS);
        BehavioralSignal oldSignal = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, tenDaysAgo);
        repository.save(oldSignal);

        // When: Finding signals after 5 days ago
        Instant fiveDaysAgo = now.minus(5, ChronoUnit.DAYS);
        List<BehavioralSignal> results = repository.findByUserIdAndTimestampAfter(
                testUserId, fiveDaysAgo);

        // Then: Should return empty list
        assertThat(results).isEmpty();
    }

    @Test
    void findByUserIdAndTimestampAfter_WhenMultipleUsersExist_ShouldReturnOnlyUserSignals() {
        // Given: Recent signals for multiple users
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        
        BehavioralSignal userSignal = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, oneDayAgo);
        BehavioralSignal otherUserSignal = createSignal("otherUser", BehavioralSignal.SignalType.COMMENT, oneDayAgo);

        repository.saveAll(Arrays.asList(userSignal, otherUserSignal));

        // When: Finding signals for specific user after 2 days ago
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        List<BehavioralSignal> results = repository.findByUserIdAndTimestampAfter(
                testUserId, twoDaysAgo);

        // Then: Should return only signals for the specified user
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUserId()).isEqualTo(testUserId);
    }

    @Test
    void save_ShouldPersistSignalWithAllFields() {
        // Given: A complete behavioral signal
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId(testUserId)
                .signalType(BehavioralSignal.SignalType.VIEW)
                .contentId(testContentId)
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(Arrays.asList("React", "TypeScript", "AI"))
                .builderId("builder789")
                .durationSeconds(45)
                .timestamp(now)
                .build();

        // When: Saving the signal
        BehavioralSignal saved = repository.save(signal);

        // Then: All fields should be persisted correctly
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(testUserId);
        assertThat(saved.getSignalType()).isEqualTo(BehavioralSignal.SignalType.VIEW);
        assertThat(saved.getContentId()).isEqualTo(testContentId);
        assertThat(saved.getContentType()).isEqualTo(BehavioralSignal.ContentType.SHIP);
        assertThat(saved.getTechTags()).containsExactly("React", "TypeScript", "AI");
        assertThat(saved.getBuilderId()).isEqualTo("builder789");
        assertThat(saved.getDurationSeconds()).isEqualTo(45);
        assertThat(saved.getTimestamp()).isEqualTo(now);
    }

    @Test
    void save_WithDifferentSignalTypes_ShouldPersistCorrectly() {
        // Given: Signals of different types
        BehavioralSignal likeSignal = createSignal(testUserId, BehavioralSignal.SignalType.LIKE, now);
        BehavioralSignal commentSignal = createSignal(testUserId, BehavioralSignal.SignalType.COMMENT, now);
        BehavioralSignal viewSignal = createSignal(testUserId, BehavioralSignal.SignalType.VIEW, now);
        BehavioralSignal followSignal = createSignal(testUserId, BehavioralSignal.SignalType.FOLLOW, now);
        BehavioralSignal skipSignal = createSignal(testUserId, BehavioralSignal.SignalType.SKIP, now);

        // When: Saving all signal types
        repository.saveAll(Arrays.asList(likeSignal, commentSignal, viewSignal, followSignal, skipSignal));

        // Then: All signal types should be persisted
        List<BehavioralSignal> results = repository.findByUserIdOrderByTimestampDesc(
                testUserId, Pageable.unpaged());
        assertThat(results).hasSize(5);
        assertThat(results).extracting(BehavioralSignal::getSignalType)
                .containsExactlyInAnyOrder(
                        BehavioralSignal.SignalType.LIKE,
                        BehavioralSignal.SignalType.COMMENT,
                        BehavioralSignal.SignalType.VIEW,
                        BehavioralSignal.SignalType.FOLLOW,
                        BehavioralSignal.SignalType.SKIP
                );
    }

    // Helper method to create a test signal
    private BehavioralSignal createSignal(String userId, BehavioralSignal.SignalType signalType, Instant timestamp) {
        return BehavioralSignal.builder()
                .userId(userId)
                .signalType(signalType)
                .contentId(testContentId)
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(Arrays.asList("React", "Python"))
                .timestamp(timestamp)
                .build();
    }
}
