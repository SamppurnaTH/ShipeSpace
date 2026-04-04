package com.shipspace.integration;

import com.shipspace.model.Like;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.LikeRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.EngagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for the like/unlike engagement system.
 *
 * These tests hit a real MongoDB instance (via Testcontainers) to prove
 * that counter integrity holds under concurrent load — not just in theory.
 *
 * Invariants that must hold regardless of execution order:
 *   1. likeCount is always 0 or 1 for a single user/ship pair
 *   2. likeCount is never negative
 *   3. likeCount matches the actual Like document count in the collection
 *   4. Like documents are never duplicated (unique index enforces this)
 *
 * Why @RepeatedTest(5)?
 * Race conditions are probabilistic. A single run may pass by luck.
 * Five runs with 100 concurrent threads each gives meaningful confidence.
 */
class LikeConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EngagementService engagementService;
    @Autowired private ShipRepository shipRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;

    private String shipId;
    private String userId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .githubId("gh-concurrency-test-" + System.nanoTime())
                .username("concurrency-user-" + System.nanoTime())
                .followerCount(0).followingCount(0).projectCount(0)
                .shipScore(0).level(1)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
        userId = user.getId();

        Project project = projectRepository.save(Project.builder()
                .userId(userId)
                .name("Test Project")
                .description("Concurrency test project")
                .shipCount(0)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        Ship ship = shipRepository.save(Ship.builder()
                .userId(userId)
                .projectId(project.getId())
                .content("Concurrency test ship")
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
        shipId = ship.getId();
    }

    @AfterEach
    void tearDown() {
        likeRepository.deleteAll();
        shipRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * 100 concurrent requests — 50 likes, 50 unlikes — random order.
     * Repeated 5 times to surface probabilistic race conditions.
     *
     * Invariants checked after all threads complete:
     *   - likeCount ∈ {0, 1}
     *   - likeCount == actual Like document count
     *   - No negative values
     */
    @RepeatedTest(5)
    void hundredConcurrentLikeUnlike_counterRemainsConsistent() throws InterruptedException {
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads); // all threads start simultaneously
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final boolean shouldLike = (i % 2 == 0); // alternating like/unlike
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await(); // wait until all threads are ready — maximizes contention
                    if (shouldLike) {
                        engagementService.likeShip(userId, shipId);
                    } else {
                        engagementService.unlikeShip(userId, shipId);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }));
        }

        boolean completed = done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("All threads should complete within 30s").isTrue();
        assertThat(errors.get()).as("No unexpected exceptions").isEqualTo(0);

        assertInvariants();
    }

    /**
     * Pure unlike storm — 50 concurrent unlike requests when the ship has no like.
     * Counter must stay at 0, not go negative.
     */
    @RepeatedTest(5)
    void concurrentUnlikeOnUnlikedShip_counterStaysAtZero() throws InterruptedException {
        // Ensure no like exists
        likeRepository.deleteAll();

        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                    engagementService.unlikeShip(userId, shipId);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(errors.get()).as("No unexpected exceptions").isEqualTo(0);

        Ship ship = shipRepository.findById(shipId).orElseThrow();
        long likeDocCount = likeRepository.countByShipId(shipId);

        assertThat(ship.getLikeCount())
                .as("likeCount must be 0 after unlike storm on unliked ship")
                .isEqualTo(0);
        assertThat(likeDocCount)
                .as("No Like documents should exist")
                .isEqualTo(0);
    }

    /**
     * Pure like storm — 50 concurrent like requests.
     * Only one Like document should exist, counter must be exactly 1.
     */
    @RepeatedTest(5)
    void concurrentLikeStorm_exactlyOneLikeDocumentAndCounter() throws InterruptedException {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                    engagementService.likeShip(userId, shipId);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(errors.get()).as("No unexpected exceptions").isEqualTo(0);

        Ship ship = shipRepository.findById(shipId).orElseThrow();
        long likeDocCount = likeRepository.countByShipId(shipId);

        assertThat(likeDocCount)
                .as("Exactly one Like document must exist after like storm")
                .isEqualTo(1);
        assertThat(ship.getLikeCount())
                .as("likeCount must equal Like document count")
                .isEqualTo(likeDocCount);
    }

    /**
     * Random mix — each thread independently decides to like or unlike.
     * Most aggressive scenario: no predictable pattern.
     */
    @RepeatedTest(5)
    void randomLikeUnlikeMix_counterAlwaysMatchesDocuments() throws InterruptedException {
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        engagementService.likeShip(userId, shipId);
                    } else {
                        engagementService.unlikeShip(userId, shipId);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(errors.get()).as("No unexpected exceptions").isEqualTo(0);
        assertInvariants();
    }

    // ── shared invariant check ────────────────────────────────────────────────

    /**
     * The three invariants that must hold after any concurrent operation:
     *   1. likeCount is 0 or 1 (single user/ship pair)
     *   2. likeCount matches actual Like document count
     *   3. likeCount is not negative
     */
    private void assertInvariants() {
        Ship ship = shipRepository.findById(shipId).orElseThrow();
        long likeDocCount = likeRepository.countByShipId(shipId);

        assertThat(ship.getLikeCount())
                .as("likeCount must not be negative")
                .isGreaterThanOrEqualTo(0);

        assertThat(ship.getLikeCount())
                .as("likeCount must not exceed 1 for a single user/ship pair")
                .isLessThanOrEqualTo(1);

        assertThat(ship.getLikeCount())
                .as("likeCount must match actual Like document count — counter drift detected")
                .isEqualTo((int) likeDocCount);
    }
}
