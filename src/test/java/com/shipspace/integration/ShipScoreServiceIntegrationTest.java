package com.shipspace.integration;

import com.shipspace.model.RankTier;
import com.shipspace.model.ScoreEventType;
import com.shipspace.model.User;
import com.shipspace.repository.ScoreEventLogRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.ShipScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ShipScoreService against a real MongoDB container.
 *
 * Phase 6: Tests idempotency, daily caps, and level/rank transitions.
 * These require a real database because they depend on the unique compound
 * index on score_event_logs (userId, eventType, entityId).
 */
class ShipScoreServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShipScoreService shipScoreService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScoreEventLogRepository scoreEventLogRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        scoreEventLogRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .username("test-scorer")
                .githubId("gh-123")
                .shipScore(0)
                .level(1)
                .rank(RankTier.DOCKED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    void applyScoreEvent_incrementsScore() {
        boolean applied = shipScoreService.applyScoreEvent(
                testUser.getId(), ScoreEventType.PROJECT_CREATED, "project-1");

        assertThat(applied).isTrue();
        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.getShipScore()).isEqualTo(ScoreEventType.PROJECT_CREATED.getPoints());
    }

    @Test
    void applyScoreEvent_idempotent_rejectsDuplicate() {
        shipScoreService.applyScoreEvent(testUser.getId(), ScoreEventType.PROJECT_CREATED, "project-1");
        boolean second = shipScoreService.applyScoreEvent(
                testUser.getId(), ScoreEventType.PROJECT_CREATED, "project-1");

        assertThat(second).isFalse();
        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        // Score should only be applied once
        assertThat(updated.getShipScore()).isEqualTo(ScoreEventType.PROJECT_CREATED.getPoints());
    }

    @Test
    void applyScoreEvent_levelUpOccursAtThreshold() {
        // Level 2 requires 40 points. PROJECT_CREATED gives 10 points.
        // Apply 4 times with different entityIds to reach level 2.
        for (int i = 1; i <= 4; i++) {
            shipScoreService.applyScoreEvent(
                    testUser.getId(), ScoreEventType.PROJECT_CREATED, "project-" + i);
        }

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.getShipScore()).isEqualTo(40);
        assertThat(updated.getLevel()).isEqualTo(2);
    }

    @Test
    void applyScoreEvent_dailyCapEnforced() {
        int cap = ScoreEventType.PROJECT_UPDATED.getDailyCap(); // 5
        int applied = 0;

        for (int i = 0; i < cap + 3; i++) {
            // PROJECT_UPDATED uses date-suffixed entityId for idempotency
            boolean result = shipScoreService.applyScoreEvent(
                    testUser.getId(), ScoreEventType.PROJECT_UPDATED, "project-1");
            if (result) applied++;
        }

        assertThat(applied).isEqualTo(cap);
    }

    @Test
    void applyScoreEvent_rankChangesWithLevel() {
        // LAUNCHER starts at level 6 (240 points). Apply enough events.
        // Use COLLAB_ACCEPTED (20 pts) × 12 = 240 pts → level 7 → LAUNCHER
        for (int i = 1; i <= 12; i++) {
            shipScoreService.applyScoreEvent(
                    testUser.getId(), ScoreEventType.COLLAB_ACCEPTED, "collab-" + i);
        }

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.getLevel()).isGreaterThanOrEqualTo(6);
        assertThat(updated.getRank()).isEqualTo(RankTier.LAUNCHER);
    }
}
