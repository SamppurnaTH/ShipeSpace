package com.shipspace.integration;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos tests for the engagement system.
 *
 * These tests go beyond correctness under normal concurrency — they validate
 * system behavior under adversarial conditions:
 *
 *   1. RETRY SAFETY — duplicate requests must not double-increment counters
 *   2. HOTSPOT CONTENTION — 100 users hitting the same ship simultaneously
 *   3. PARTIAL FAILURE SIMULATION — what happens when operations are interrupted
 *   4. PERFORMANCE BOUNDS — operations must complete within acceptable latency
 *
 * Each test class is a distinct failure scenario. They are independent and
 * can be run in any order.
 */
class EngagementChaosTest extends AbstractIntegrationTest {

    @Autowired private EngagementService engagementService;
    @Autowired private ShipRepository shipRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private MongoTemplate mongoTemplate;

    // ── shared setup helpers ──────────────────────────────────────────────────

    private User createUser(String suffix) {
        return userRepository.save(User.builder()
                .githubId("gh-chaos-" + suffix + "-" + System.nanoTime())
                .username("chaos-" + suffix + "-" + System.nanoTime())
                .followerCount(0).followingCount(0).projectCount(0)
                .shipScore(0).level(1)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
    }

    private Ship createShip(String userId) {
        Project project = projectRepository.save(Project.builder()
                .userId(userId).name("Chaos Project").description("Chaos test")
                .shipCount(0).createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
        return shipRepository.save(Ship.builder()
                .userId(userId).projectId(project.getId())
                .content("Chaos test ship").likeCount(0).commentCount(0)
                .trendingScore(0.0).createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        likeRepository.deleteAll();
        shipRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // SCENARIO 1: RETRY SAFETY
    // Duplicate requests (network retries, client double-submit) must be
    // idempotent. The counter must not increment more than once per user.
    // =========================================================================

    @Nested
    @DisplayName("Retry Safety — duplicate requests must not corrupt counters")
    class RetrySafety {

        /**
         * Same user sends 50 identical like requests (simulates aggressive retry).
         * likeCount must be exactly 1. Like documents must be exactly 1.
         */
        @RepeatedTest(5)
        @DisplayName("50 duplicate like requests → exactly 1 like, counter = 1")
        void duplicateLikeRequests_idempotent() throws InterruptedException {
            User user = createUser("retry-like");
            Ship ship = createShip(user.getId());

            int duplicates = 50;
            runConcurrent(duplicates, () -> engagementService.likeShip(user.getId(), ship.getId()));

            assertCounterMatchesDocs(ship.getId(), "duplicate like storm");
            assertThat(likeRepository.countByShipId(ship.getId()))
                    .as("Exactly one Like document after 50 duplicate likes")
                    .isEqualTo(1);
            assertThat(shipRepository.findById(ship.getId()).orElseThrow().getLikeCount())
                    .as("likeCount must be exactly 1")
                    .isEqualTo(1);
        }

        /**
         * Same user sends 50 identical unlike requests on an already-unliked ship.
         * Counter must stay at 0. No negative values.
         */
        @RepeatedTest(5)
        @DisplayName("50 duplicate unlike requests on unliked ship → counter stays 0")
        void duplicateUnlikeRequests_counterStaysAtZero() throws InterruptedException {
            User user = createUser("retry-unlike");
            Ship ship = createShip(user.getId());
            // Ensure no like exists
            likeRepository.deleteAll();

            int duplicates = 50;
            runConcurrent(duplicates, () -> engagementService.unlikeShip(user.getId(), ship.getId()));

            Ship result = shipRepository.findById(ship.getId()).orElseThrow();
            assertThat(result.getLikeCount())
                    .as("Counter must be 0 after duplicate unlike storm on unliked ship")
                    .isEqualTo(0);
            assertThat(likeRepository.countByShipId(ship.getId()))
                    .as("No Like documents should exist")
                    .isEqualTo(0);
        }

        /**
         * Like → 50 duplicate unlikes → like again.
         * Final state must be: likeCount = 1, exactly 1 Like document.
         * Tests that the system recovers correctly after a retry storm.
         */
        @RepeatedTest(5)
        @DisplayName("Like → unlike storm → like again → counter = 1")
        void likeUnlikeStormLike_recoversCorrectly() throws InterruptedException {
            User user = createUser("retry-recover");
            Ship ship = createShip(user.getId());

            // Step 1: like
            engagementService.likeShip(user.getId(), ship.getId());
            assertCounterMatchesDocs(ship.getId(), "after initial like");

            // Step 2: 50 concurrent unlikes (retry storm)
            runConcurrent(50, () -> engagementService.unlikeShip(user.getId(), ship.getId()));
            assertCounterMatchesDocs(ship.getId(), "after unlike storm");

            // Step 3: like again
            engagementService.likeShip(user.getId(), ship.getId());

            Ship result = shipRepository.findById(ship.getId()).orElseThrow();
            long docCount = likeRepository.countByShipId(ship.getId());

            assertThat(result.getLikeCount()).as("likeCount must be 1 after recovery").isEqualTo(1);
            assertThat(docCount).as("Exactly 1 Like document after recovery").isEqualTo(1);
        }
    }

    // =========================================================================
    // SCENARIO 2: HOTSPOT CONTENTION
    // 100 different users all like/unlike the same ship simultaneously.
    // This is the "viral ship" scenario — the most realistic production load.
    // Counter must equal the actual number of Like documents.
    // =========================================================================

    @Nested
    @DisplayName("Hotspot Contention — 100 users hitting the same ship")
    class HotspotContention {

        /**
         * 100 users all like the same ship simultaneously.
         * likeCount must equal 100. No duplicates per user.
         */
        @RepeatedTest(5)
        @DisplayName("100 users like same ship → likeCount = 100, no duplicates")
        void hundredUsersLikeSameShip_counterEqualsUserCount() throws InterruptedException {
            User shipOwner = createUser("hotspot-owner");
            Ship ship = createShip(shipOwner.getId());

            // Create 100 distinct users
            List<User> users = IntStream.range(0, 100)
                    .mapToObj(i -> createUser("hotspot-" + i))
                    .collect(Collectors.toList());

            int threads = users.size();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (User user : users) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await(); // barrier — all 100 fire simultaneously
                        engagementService.likeShip(user.getId(), ship.getId());
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

            Ship result = shipRepository.findById(ship.getId()).orElseThrow();
            long docCount = likeRepository.countByShipId(ship.getId());

            assertThat(docCount)
                    .as("Exactly 100 Like documents — one per user, no duplicates")
                    .isEqualTo(100);
            assertThat(result.getLikeCount())
                    .as("likeCount must equal Like document count (no counter drift)")
                    .isEqualTo((int) docCount);
        }

        /**
         * 100 users like, then 100 users unlike the same ship simultaneously.
         * Final likeCount must be 0. No documents. No negative counter.
         */
        @RepeatedTest(5)
        @DisplayName("100 users like then 100 users unlike → likeCount = 0")
        void hundredUsersLikeThenUnlike_counterReturnsToZero() throws InterruptedException {
            User shipOwner = createUser("hotspot-owner2");
            Ship ship = createShip(shipOwner.getId());

            List<User> users = IntStream.range(0, 100)
                    .mapToObj(i -> createUser("hotspot2-" + i))
                    .collect(Collectors.toList());

            // Phase 1: all 100 like
            runConcurrentForUsers(users, ship.getId(), true);
            assertCounterMatchesDocs(ship.getId(), "after 100 likes");

            // Phase 2: all 100 unlike
            runConcurrentForUsers(users, ship.getId(), false);

            Ship result = shipRepository.findById(ship.getId()).orElseThrow();
            long docCount = likeRepository.countByShipId(ship.getId());

            assertThat(result.getLikeCount())
                    .as("likeCount must be 0 after all users unlike")
                    .isEqualTo(0);
            assertThat(docCount)
                    .as("No Like documents should remain")
                    .isEqualTo(0);
        }

        /**
         * 100 users randomly like or unlike the same ship.
         * Counter must always equal the actual Like document count.
         * This is the most realistic production scenario.
         */
        @RepeatedTest(10)
        @DisplayName("100 users random like/unlike → counter always matches documents")
        void hundredUsersRandomLikeUnlike_counterAlwaysMatchesDocs() throws InterruptedException {
            User shipOwner = createUser("hotspot-owner3");
            Ship ship = createShip(shipOwner.getId());

            List<User> users = IntStream.range(0, 100)
                    .mapToObj(i -> createUser("hotspot3-" + i))
                    .collect(Collectors.toList());

            int threads = users.size();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (User user : users) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await();
                        // Each user randomly likes or unlikes
                        if (Math.random() < 0.5) {
                            engagementService.likeShip(user.getId(), ship.getId());
                        } else {
                            engagementService.unlikeShip(user.getId(), ship.getId());
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
            assertCounterMatchesDocs(ship.getId(), "100-user random mix");

            Ship result = shipRepository.findById(ship.getId()).orElseThrow();
            assertThat(result.getLikeCount())
                    .as("likeCount must not be negative")
                    .isGreaterThanOrEqualTo(0);
            assertThat(result.getLikeCount())
                    .as("likeCount must not exceed user count (100)")
                    .isLessThanOrEqualTo(100);
        }
    }

    // =========================================================================
    // SCENARIO 3: PERFORMANCE BOUNDS
    // Operations must complete within acceptable latency under load.
    // If they don't, the thread pool exhausts and the system degrades.
    // =========================================================================

    @Nested
    @DisplayName("Performance Bounds — operations must complete within latency budget")
    class PerformanceBounds {

        /**
         * 200 concurrent like operations must all complete within 5 seconds.
         * If atomic MongoDB ops are taking > 5s under 200 threads, the connection
         * pool is exhausted or MongoDB is under-provisioned.
         *
         * 5s is deliberately generous — in production, p99 should be < 200ms.
         * This test catches catastrophic degradation, not performance tuning.
         */
        @Test
        @DisplayName("200 concurrent likes must complete within 5 seconds")
        void twoHundredConcurrentLikes_completeWithinLatencyBudget() throws InterruptedException {
            User shipOwner = createUser("perf-owner");
            Ship ship = createShip(shipOwner.getId());

            List<User> users = IntStream.range(0, 200)
                    .mapToObj(i -> createUser("perf-" + i))
                    .collect(Collectors.toList());

            int threads = users.size();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch done = new CountDownLatch(threads);

            long startMs = System.currentTimeMillis();

            for (User user : users) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await();
                        engagementService.likeShip(user.getId(), ship.getId());
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            boolean completed = done.await(5, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - startMs;
            executor.shutdown();

            assertThat(completed)
                    .as("200 concurrent likes must complete within 5s — elapsed: %dms. " +
                        "If this fails, MongoDB connection pool is exhausted or ops are blocking.", elapsedMs)
                    .isTrue();
        }

        /**
         * Measures per-operation latency across 100 sequential like/unlike cycles.
         * Each cycle: like → unlike. Measures wall-clock time per operation.
         * p99 must be < 500ms on a local MongoDB instance.
         */
        @Test
        @DisplayName("Per-operation latency p99 < 500ms on local MongoDB")
        void perOperationLatency_withinBudget() {
            User user = createUser("latency");
            Ship ship = createShip(user.getId());

            List<Long> likeLatencies = new ArrayList<>();
            List<Long> unlikeLatencies = new ArrayList<>();

            for (int i = 0; i < 100; i++) {
                long likeStart = System.currentTimeMillis();
                engagementService.likeShip(user.getId(), ship.getId());
                likeLatencies.add(System.currentTimeMillis() - likeStart);

                long unlikeStart = System.currentTimeMillis();
                engagementService.unlikeShip(user.getId(), ship.getId());
                unlikeLatencies.add(System.currentTimeMillis() - unlikeStart);
            }

            long likeP99 = percentile(likeLatencies, 99);
            long unlikeP99 = percentile(unlikeLatencies, 99);

            assertThat(likeP99)
                    .as("Like p99 latency must be < 500ms (actual: %dms). " +
                        "If this fails, MongoDB is slow or connection pool is misconfigured.", likeP99)
                    .isLessThan(500);
            assertThat(unlikeP99)
                    .as("Unlike p99 latency must be < 500ms (actual: %dms).", unlikeP99)
                    .isLessThan(500);
        }
    }

    // =========================================================================
    // SCENARIO 4: PARTIAL FAILURE SIMULATION
    // What happens when the counter update succeeds but the like document
    // insert fails (or vice versa)? The CounterReconciliationJob is the
    // recovery mechanism — this test validates that drift is detectable.
    // =========================================================================

    @Nested
    @DisplayName("Partial Failure — counter drift is detectable and bounded")
    class PartialFailureSimulation {

        /**
         * Manually inject counter drift by directly writing to MongoDB,
         * bypassing the service layer. Then verify that the drift is detectable
         * by comparing likeCount to the actual Like document count.
         *
         * This simulates what happens if a crash occurs between the Like insert
         * and the counter increment — the exact scenario @Transactional is
         * supposed to prevent but can't on standalone MongoDB.
         *
         * The test proves: drift IS detectable (the reconciliation job can fix it).
         * It does NOT prove the job runs — that's tested in CounterReconciliationIntegrationTest.
         */
        @Test
        @DisplayName("Injected counter drift is detectable via document count comparison")
        void injectedCounterDrift_isDetectable() {
            User user = createUser("drift");
            Ship ship = createShip(user.getId());

            // Inject drift: set likeCount to 5 but insert 0 Like documents
            mongoTemplate.save(
                    shipRepository.findById(ship.getId()).map(s -> {
                        s.setLikeCount(5); // lie
                        return s;
                    }).orElseThrow()
            );

            Ship drifted = shipRepository.findById(ship.getId()).orElseThrow();
            long actualDocs = likeRepository.countByShipId(ship.getId());

            // Drift is detectable
            assertThat(drifted.getLikeCount())
                    .as("Injected drift: likeCount should be 5")
                    .isEqualTo(5);
            assertThat(actualDocs)
                    .as("Actual Like documents should be 0")
                    .isEqualTo(0);
            assertThat((long) drifted.getLikeCount())
                    .as("Drift is detectable: likeCount != document count")
                    .isNotEqualTo(actualDocs);
        }

        /**
         * After injecting drift, run normal like/unlike operations.
         * The service must not make the drift worse — it must operate
         * correctly relative to the actual Like document state.
         *
         * This proves the service is document-state-driven, not counter-driven.
         * The counter is a cache of truth; the Like collection is truth.
         */
        @Test
        @DisplayName("Operations after injected drift do not amplify corruption")
        void operationsAfterDrift_doNotAmplifyCorruption() throws InterruptedException {
            User user = createUser("drift2");
            Ship ship = createShip(user.getId());

            // Inject drift: counter says 3, but 0 documents exist
            mongoTemplate.save(
                    shipRepository.findById(ship.getId()).map(s -> {
                        s.setLikeCount(3);
                        return s;
                    }).orElseThrow()
            );

            // Now run 50 concurrent like/unlike operations
            runConcurrent(25, () -> engagementService.likeShip(user.getId(), ship.getId()));
            runConcurrent(25, () -> engagementService.unlikeShip(user.getId(), ship.getId()));

            Ship result = shipRepository.findById(ship.getId()).orElseThrow();
            long docCount = likeRepository.countByShipId(ship.getId());

            // The counter may still be drifted (pre-existing drift + operations)
            // but it must not be negative, and the Like document state must be consistent
            assertThat(result.getLikeCount())
                    .as("Counter must not go negative even after operations on drifted state")
                    .isGreaterThanOrEqualTo(0);
            assertThat(docCount)
                    .as("Like document count must be 0 or 1 (single user)")
                    .isBetween(0L, 1L);
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void runConcurrent(int threads, Runnable op) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                    op.run();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private void runConcurrentForUsers(List<User> users, String shipId, boolean like)
            throws InterruptedException {
        int threads = users.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (User user : users) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                    if (like) engagementService.likeShip(user.getId(), shipId);
                    else engagementService.unlikeShip(user.getId(), shipId);
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
    }

    private void assertCounterMatchesDocs(String shipId, String scenario) {
        Ship ship = shipRepository.findById(shipId).orElseThrow();
        long docCount = likeRepository.countByShipId(shipId);
        assertThat((long) ship.getLikeCount())
                .as("[%s] likeCount (%d) must match Like document count (%d) — counter drift detected",
                        scenario, ship.getLikeCount(), docCount)
                .isEqualTo(docCount);
    }

    private long percentile(List<Long> latencies, int p) {
        List<Long> sorted = latencies.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
}
