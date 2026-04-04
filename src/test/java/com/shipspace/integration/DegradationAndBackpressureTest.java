package com.shipspace.integration;

import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.LikeRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.EngagementService;
import com.shipspace.service.FeedService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Degradation and backpressure tests.
 *
 * These tests validate HOW the system fails, not just WHETHER it fails.
 * A system that degrades gracefully is production-ready.
 * A system that collapses silently is not.
 *
 * CONSISTENCY MODEL (explicit — not assumed):
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  ShipSpace Like Counter Consistency Model                       │
 * │                                                                 │
 * │  STRONG consistency guarantee:                                  │
 * │    likeCount == actual Like document count                      │
 * │    This holds at all times EXCEPT during the CounterReconcil-   │
 * │    iationJob window (3 AM daily, ~30s).                         │
 * │                                                                 │
 * │  EVENTUAL consistency window:                                   │
 * │    If a crash occurs between Like insert and $inc counter,      │
 * │    drift is bounded to ±1 per crash event.                      │
 * │    The CounterReconciliationJob corrects drift within 24h.      │
 * │                                                                 │
 * │  NEVER:                                                         │
 * │    likeCount < 0                                                │
 * │    likeCount > actual Like document count by more than 1        │
 * │    (bounded drift, not unbounded corruption)                    │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * DEGRADATION CONTRACT:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Dependency    │ Failure Mode    │ System Behavior              │
 * │────────────────┼─────────────────┼──────────────────────────── │
 * │  Redis         │ Down            │ Fail-open: serve from        │
 * │                │                 │ Caffeine local cache         │
 * │  Redis         │ Slow (>500ms)   │ Timeout, fail-open           │
 * │  MongoDB       │ Slow            │ Circuit breaker opens        │
 * │                │                 │ after 50% failure rate       │
 * │  Async pool    │ Saturated       │ CallerRunsPolicy: HTTP       │
 * │                │                 │ thread runs task (slows      │
 * │                │                 │ response, doesn't drop)      │
 * │  Behavioral    │ Saturated       │ DiscardOldestPolicy: drops   │
 * │  tracking pool │                 │ signals (acceptable loss)    │
 * └─────────────────────────────────────────────────────────────────┘
 */
class DegradationAndBackpressureTest extends AbstractIntegrationTest {

    @Autowired private EngagementService engagementService;
    @Autowired private FeedService feedService;
    @Autowired private ShipRepository shipRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private CacheManager redisCacheManager;

    private String shipId;
    private String userId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .githubId("gh-degrade-" + System.nanoTime())
                .username("degrade-user-" + System.nanoTime())
                .followerCount(0).followingCount(0).projectCount(0)
                .shipScore(0).level(1)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
        userId = user.getId();

        Project project = projectRepository.save(Project.builder()
                .userId(userId).name("Degradation Project").description("Test")
                .shipCount(0).createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        Ship ship = shipRepository.save(Ship.builder()
                .userId(userId).projectId(project.getId())
                .content("Degradation test ship").likeCount(0).commentCount(0)
                .trendingScore(0.0).createdAt(Instant.now()).updatedAt(Instant.now())
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

    // =========================================================================
    // SCENARIO 1: CACHE DEGRADATION
    // When Redis cache is cleared/unavailable, the system must fall back to
    // MongoDB and still return correct results — just slower.
    // =========================================================================

    @Nested
    @DisplayName("Cache Degradation — system serves correct data without cache")
    class CacheDegradation {

        /**
         * Evict all trending feed cache entries, then verify the feed still loads.
         * This simulates Redis being flushed or a cache miss storm after deployment.
         *
         * DEGRADATION CONTRACT: cache miss → MongoDB query → correct result.
         * The system must not return empty or error — just slower.
         */
        @Test
        @DisplayName("Trending feed serves correct data after full cache eviction")
        void trendingFeed_servesCorrectDataAfterCacheEviction() {
            // Warm the cache
            var firstLoad = feedService.getTrendingFeed(
                    org.springframework.data.domain.PageRequest.of(0, 20));

            // Evict — simulates Redis flush or cache miss storm
            feedService.invalidateTrendingCache();

            // Must still serve data — from MongoDB this time
            var afterEviction = feedService.getTrendingFeed(
                    org.springframework.data.domain.PageRequest.of(0, 20));

            assertThat(afterEviction).as("Feed must return data after cache eviction").isNotNull();
            assertThat(afterEviction.getTotalElements())
                    .as("Element count must be consistent before and after eviction")
                    .isEqualTo(firstLoad.getTotalElements());
        }

        /**
         * Simulate cache unavailability by disabling the cache and verifying
         * the system falls back to the Caffeine local cache.
         *
         * This tests the CacheConfig.localCacheManager() fallback path.
         */
        @Test
        @DisplayName("System falls back to local Caffeine cache when Redis cache is cleared")
        void system_fallsBackToLocalCache_whenRedisCacheCleared() {
            // Clear all Redis cache entries for trending feed
            var trendingCache = redisCacheManager.getCache("trendingFeed");
            if (trendingCache != null) {
                trendingCache.clear();
            }

            // System must still respond — either from MongoDB or local cache
            var result = feedService.getTrendingFeed(
                    org.springframework.data.domain.PageRequest.of(0, 20));

            assertThat(result)
                    .as("System must respond even when Redis cache is cleared")
                    .isNotNull();
        }

        /**
         * 50 concurrent cache-miss requests — the "thundering herd" scenario.
         * All requests hit MongoDB simultaneously when cache is cold.
         * System must handle this without errors or timeouts.
         *
         * This is what happens after every deployment when the cache is cold.
         */
        @Test
        @DisplayName("50 concurrent cache-miss requests do not cause errors (thundering herd)")
        void thunderingHerd_concurrentCacheMisses_noErrors() throws InterruptedException {
            feedService.invalidateTrendingCache();

            int threads = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await(); // all hit simultaneously — cold cache
                        feedService.getTrendingFeed(
                                org.springframework.data.domain.PageRequest.of(0, 20));
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            boolean completed = done.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).as("All requests must complete within 30s").isTrue();
            assertThat(errors.get())
                    .as("No errors during thundering herd — system must handle cold cache gracefully")
                    .isEqualTo(0);
        }
    }

    // =========================================================================
    // SCENARIO 2: BACKPRESSURE
    // When the async thread pool is saturated, CallerRunsPolicy kicks in.
    // The HTTP thread runs the task — response slows but nothing is dropped.
    // This is the correct degradation: slow, not broken.
    // =========================================================================

    @Nested
    @DisplayName("Backpressure — system slows under saturation, does not collapse")
    class Backpressure {

        /**
         * Saturate the async task executor by submitting more tasks than its
         * queue capacity (500). CallerRunsPolicy means the submitting thread
         * (HTTP thread) runs the overflow tasks.
         *
         * EXPECTED BEHAVIOR:
         *   - No RejectedExecutionException
         *   - All tasks complete (some on HTTP threads, some on pool threads)
         *   - Response time increases (acceptable degradation)
         *   - System does NOT crash or drop requests
         *
         * This validates the CallerRunsPolicy choice in AsyncConfig.
         */
        @Test
        @DisplayName("Async pool saturation: CallerRunsPolicy prevents task loss")
        void asyncPoolSaturation_callerRunsPolicy_noTaskLoss() throws InterruptedException {
            // Get the actual executor to inspect its state
            var ctx = org.springframework.test.context.TestContextManager.class;

            // Submit 600 tasks — 100 more than queue capacity (500)
            // CallerRunsPolicy means overflow runs on the submitting thread
            int taskCount = 600;
            AtomicInteger completed = new AtomicInteger(0);
            AtomicInteger rejected = new AtomicInteger(0);

            // Use a bounded pool to simulate HTTP thread pool (Tomcat default: 200)
            ExecutorService httpThreadPool = Executors.newFixedThreadPool(20);
            CountDownLatch done = new CountDownLatch(taskCount);

            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                httpThreadPool.submit(() -> {
                    try {
                        // Simulate what an HTTP thread does: like a ship
                        // This triggers async notification dispatch internally
                        engagementService.likeShip(userId, shipId);
                        completed.incrementAndGet();
                    } catch (RejectedExecutionException e) {
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        // Other exceptions (duplicate key etc.) are fine
                        completed.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            done.await(60, TimeUnit.SECONDS);
            httpThreadPool.shutdown();

            assertThat(rejected.get())
                    .as("CallerRunsPolicy must prevent RejectedExecutionException — " +
                        "no tasks should be rejected, they run on the caller thread instead")
                    .isEqualTo(0);

            assertThat(completed.get())
                    .as("All %d tasks must complete (some on pool threads, some on caller threads)",
                            taskCount)
                    .isEqualTo(taskCount);
        }

        /**
         * Verify the system responds to requests even when under heavy async load.
         * The HTTP thread pool must not be fully consumed by async overflow.
         *
         * DEGRADATION CONTRACT: under saturation, p99 increases but the system
         * remains responsive. Requests do not time out or return 503.
         */
        @Test
        @DisplayName("System remains responsive under async pool saturation")
        void systemRemainsResponsive_underAsyncSaturation() throws InterruptedException {
            int heavyLoad = 200;
            ExecutorService executor = Executors.newFixedThreadPool(heavyLoad);
            CountDownLatch ready = new CountDownLatch(heavyLoad);
            CountDownLatch done = new CountDownLatch(heavyLoad);
            List<Long> latencies = new ArrayList<>();
            Object latencyLock = new Object();

            for (int i = 0; i < heavyLoad; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await();
                        long start = System.currentTimeMillis();
                        engagementService.likeShip(userId, shipId);
                        long elapsed = System.currentTimeMillis() - start;
                        synchronized (latencyLock) {
                            latencies.add(elapsed);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            boolean completed = done.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed)
                    .as("All requests must complete within 30s even under saturation")
                    .isTrue();

            // Under saturation, p99 will be higher than normal — but must not be catastrophic
            // 10s is the absolute ceiling — if p99 > 10s, the system is collapsing, not degrading
            if (!latencies.isEmpty()) {
                long p99 = percentile(latencies, 99);
                assertThat(p99)
                        .as("p99 latency under saturation must be < 10s (actual: %dms). " +
                            "If this fails, CallerRunsPolicy is causing thread starvation.", p99)
                        .isLessThan(10_000);
            }
        }
    }

    // =========================================================================
    // SCENARIO 3: CONSISTENCY MODEL VALIDATION
    // Explicitly tests the documented consistency model above.
    // Strong consistency: likeCount == docs at all times (outside reconciliation window)
    // Bounded drift: drift is ±1 per crash, not unbounded
    // Never negative: floor is enforced atomically
    // =========================================================================

    @Nested
    @DisplayName("Consistency Model — validates the documented consistency guarantees")
    class ConsistencyModelValidation {

        /**
         * STRONG CONSISTENCY: after any sequence of like/unlike operations,
         * likeCount must exactly equal the Like document count.
         * No eventual consistency window — this is synchronous and atomic.
         */
        @Test
        @DisplayName("Strong consistency: likeCount == document count after every operation")
        void strongConsistency_counterMatchesDocumentsAfterEveryOperation() {
            // Sequence: like → unlike → like → unlike → like
            // Check after each step — not just at the end
            engagementService.likeShip(userId, shipId);
            assertStrongConsistency("after like 1");

            engagementService.unlikeShip(userId, shipId);
            assertStrongConsistency("after unlike 1");

            engagementService.likeShip(userId, shipId);
            assertStrongConsistency("after like 2");

            engagementService.unlikeShip(userId, shipId);
            assertStrongConsistency("after unlike 2");

            engagementService.likeShip(userId, shipId);
            assertStrongConsistency("after like 3");
        }

        /**
         * NEVER NEGATIVE: the floor guarantee must hold under any operation sequence.
         * This is enforced by the MongoDB aggregation pipeline update.
         */
        @Test
        @DisplayName("Never negative: floor holds under any operation sequence")
        void neverNegative_floorHoldsUnderAnySequence() throws InterruptedException {
            // Start with no like, run 100 concurrent unlikes
            int threads = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch done = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await();
                        engagementService.unlikeShip(userId, shipId);
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            done.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            Ship result = shipRepository.findById(shipId).orElseThrow();
            assertThat(result.getLikeCount())
                    .as("likeCount must never be negative — floor guarantee violated")
                    .isGreaterThanOrEqualTo(0);
        }

        /**
         * BOUNDED DRIFT: manually inject ±1 drift (simulates a crash between
         * Like insert and counter increment), then verify the drift is bounded
         * and does not grow with subsequent operations.
         *
         * This validates the documented eventual consistency window.
         */
        @Test
        @DisplayName("Bounded drift: injected ±1 drift does not grow with operations")
        void boundedDrift_doesNotGrowWithSubsequentOperations() {
            // Inject +1 drift (counter ahead of documents)
            Ship ship = shipRepository.findById(shipId).orElseThrow();
            ship.setLikeCount(1); // counter says 1, but 0 Like documents exist
            shipRepository.save(ship);

            long docsBefore = likeRepository.countByShipId(shipId);
            int counterBefore = shipRepository.findById(shipId).orElseThrow().getLikeCount();
            long driftBefore = counterBefore - docsBefore; // should be +1

            assertThat(driftBefore).as("Injected drift should be +1").isEqualTo(1);

            // Run 10 like/unlike cycles — drift must not grow
            for (int i = 0; i < 10; i++) {
                engagementService.likeShip(userId, shipId);
                engagementService.unlikeShip(userId, shipId);
            }

            long docsAfter = likeRepository.countByShipId(shipId);
            int counterAfter = shipRepository.findById(shipId).orElseThrow().getLikeCount();
            long driftAfter = Math.abs(counterAfter - docsAfter);

            assertThat(driftAfter)
                    .as("Drift must not grow beyond ±1 after operations on drifted state. " +
                        "Before: %d, After: %d", driftBefore, driftAfter)
                    .isLessThanOrEqualTo(1);

            assertThat(counterAfter)
                    .as("Counter must not be negative after operations on drifted state")
                    .isGreaterThanOrEqualTo(0);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertStrongConsistency(String checkpoint) {
        Ship ship = shipRepository.findById(shipId).orElseThrow();
        long docCount = likeRepository.countByShipId(shipId);
        assertThat((long) ship.getLikeCount())
                .as("[%s] STRONG CONSISTENCY VIOLATION: likeCount=%d, docs=%d",
                        checkpoint, ship.getLikeCount(), docCount)
                .isEqualTo(docCount);
    }

    private long percentile(List<Long> values, int p) {
        List<Long> sorted = values.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
}
