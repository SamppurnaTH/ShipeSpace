package com.shipspace.integration;

import com.shipspace.model.Comment;
import com.shipspace.model.Like;
import com.shipspace.model.Ship;
import com.shipspace.repository.CommentRepository;
import com.shipspace.repository.LikeRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.service.CounterReconciliationJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CounterReconciliationJob.
 *
 * Phase 6: Verifies that the aggregation pipeline correctly identifies and
 * fixes drifted counters. Tests the Phase 2 rewrite (aggregation vs N×2 queries).
 */
class CounterReconciliationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CounterReconciliationJob reconciliationJob;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private CommentRepository commentRepository;

    @BeforeEach
    void setUp() {
        shipRepository.deleteAll();
        likeRepository.deleteAll();
        commentRepository.deleteAll();
    }

    @Test
    void reconciliation_fixesDriftedLikeCount() {
        Ship ship = shipRepository.save(Ship.builder()
                .userId("user-1")
                .projectId("project-1")
                .content("test ship")
                .likeCount(99) // wrong — actual is 2
                .commentCount(0)
                .trendingScore(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        likeRepository.save(Like.builder().userId("u1").shipId(ship.getId()).createdAt(Instant.now()).build());
        likeRepository.save(Like.builder().userId("u2").shipId(ship.getId()).createdAt(Instant.now()).build());

        reconciliationJob.reconcileCounters();

        Ship fixed = shipRepository.findById(ship.getId()).orElseThrow();
        assertThat(fixed.getLikeCount()).isEqualTo(2);
    }

    @Test
    void reconciliation_fixesDriftedCommentCount() {
        Ship ship = shipRepository.save(Ship.builder()
                .userId("user-1")
                .projectId("project-1")
                .content("test ship")
                .likeCount(0)
                .commentCount(50) // wrong — actual is 1
                .trendingScore(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        commentRepository.save(Comment.builder()
                .userId("u1").shipId(ship.getId()).content("nice!")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        reconciliationJob.reconcileCounters();

        Ship fixed = shipRepository.findById(ship.getId()).orElseThrow();
        assertThat(fixed.getCommentCount()).isEqualTo(1);
    }

    @Test
    void reconciliation_doesNotUpdateCorrectCounts() {
        Ship ship = shipRepository.save(Ship.builder()
                .userId("user-1")
                .projectId("project-1")
                .content("test ship")
                .likeCount(1)
                .commentCount(0)
                .trendingScore(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        likeRepository.save(Like.builder().userId("u1").shipId(ship.getId()).createdAt(Instant.now()).build());

        reconciliationJob.reconcileCounters();

        Ship unchanged = shipRepository.findById(ship.getId()).orElseThrow();
        assertThat(unchanged.getLikeCount()).isEqualTo(1);
    }

    @Test
    void reconciliation_setsZeroWhenAllLikesDeleted() {
        Ship ship = shipRepository.save(Ship.builder()
                .userId("user-1")
                .projectId("project-1")
                .content("test ship")
                .likeCount(5) // stale — all likes were deleted
                .commentCount(0)
                .trendingScore(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // No likes in the likes collection

        reconciliationJob.reconcileCounters();

        Ship fixed = shipRepository.findById(ship.getId()).orElseThrow();
        assertThat(fixed.getLikeCount()).isEqualTo(0);
    }
}
