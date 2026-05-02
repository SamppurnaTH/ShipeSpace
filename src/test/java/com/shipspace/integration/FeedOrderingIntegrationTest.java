package com.shipspace.integration;

import com.shipspace.model.Ship;
import com.shipspace.repository.ShipRepository;
import com.shipspace.service.FeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for feed ordering and pagination stability.
 *
 * Uses real local MongoDB (shipspace-integration-test database) to verify that:
 * - FeedService sorts ships by trendingScore DESC (then createdAt DESC as tiebreaker)
 * - Pagination produces stable, non-overlapping pages
 * - Empty repositories return empty pages cleanly
 *
 * Explicitly clears the trendingFeed cache before each test to ensure
 * assertions reflect real MongoDB query results, not cached stale data.
 *
 * Flapdoodle embedded MongoDB is excluded — it cannot download its binary here.
 */
@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/shipspace-integration-test",
        "spring.autoconfigure.exclude=de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration",
        "spring.main.allow-bean-definition-overriding=true"
})
@DisplayName("Feed Ordering and Pagination Integration Tests")
class FeedOrderingIntegrationTest {

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private FeedService feedService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        shipRepository.deleteAll();
        evictTrendingCaches();
    }

    private void evictTrendingCaches() {
        for (String cacheName : new String[]{"trendingFeed", "trendingFeedWithTimeWindow"}) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @Test
    @DisplayName("getTrendingFeed — ships are returned in descending trendingScore order")
    void getTrendingFeed_orderedByTrendingScoreDescending() {
        shipRepository.save(buildShip("u1", "p1", 1.0, Instant.now()));
        shipRepository.save(buildShip("u2", "p2", 10.0, Instant.now()));
        shipRepository.save(buildShip("u3", "p3", 5.0, Instant.now()));

        Page<Ship> result = feedService.getTrendingFeed(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getTrendingScore()).isEqualTo(10.0);
        assertThat(result.getContent().get(1).getTrendingScore()).isEqualTo(5.0);
        assertThat(result.getContent().get(2).getTrendingScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("getTrendingFeed — equal trendingScore ships are resolved by createdAt descending")
    void getTrendingFeed_equalScore_resolvedByCreatedAtDescending() {
        Instant earlier = Instant.now().minusSeconds(3600);
        Instant later = Instant.now();

        Ship old = shipRepository.save(buildShip("u1", "p1", 5.0, earlier));
        Ship recent = shipRepository.save(buildShip("u2", "p2", 5.0, later));

        Page<Ship> result = feedService.getTrendingFeed(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(recent.getId());
        assertThat(result.getContent().get(1).getId()).isEqualTo(old.getId());
    }

    @Test
    @DisplayName("getTrendingFeed — pagination produces stable non-overlapping pages")
    void getTrendingFeed_paginationIsStable_noOverlapBetweenPages() {
        for (int i = 1; i <= 5; i++) {
            shipRepository.save(buildShip("u" + i, "p" + i, (double) i, Instant.now().minusSeconds(i)));
        }

        evictTrendingCaches();
        Page<Ship> page0 = feedService.getTrendingFeed(PageRequest.of(0, 2));
        evictTrendingCaches();
        Page<Ship> page1 = feedService.getTrendingFeed(PageRequest.of(1, 2));

        assertThat(page0.getTotalElements()).isEqualTo(5L);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);

        Set<String> page0Ids = page0.getContent().stream()
                .map(Ship::getId).collect(Collectors.toSet());
        Set<String> page1Ids = page1.getContent().stream()
                .map(Ship::getId).collect(Collectors.toSet());

        assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);
    }

    @Test
    @DisplayName("getTrendingFeed — first page contains the highest-scoring ships")
    void getTrendingFeed_firstPageContainsHighestScoringShips() {
        for (int i = 1; i <= 6; i++) {
            shipRepository.save(buildShip("u" + i, "p" + i, (double) i * 10, Instant.now()));
        }

        Page<Ship> page0 = feedService.getTrendingFeed(PageRequest.of(0, 3));

        assertThat(page0.getContent()).hasSize(3);
        assertThat(page0.getContent().get(0).getTrendingScore()).isEqualTo(60.0);
        assertThat(page0.getContent().get(1).getTrendingScore()).isEqualTo(50.0);
        assertThat(page0.getContent().get(2).getTrendingScore()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("getTrendingFeed — returns empty page when no ships exist")
    void getTrendingFeed_emptyRepository_returnsEmptyPage() {
        Page<Ship> result = feedService.getTrendingFeed(PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("getTrendingFeed — single ship is returned on page 0 with correct totals")
    void getTrendingFeed_singleShip_returnedOnFirstPage() {
        Ship only = shipRepository.save(buildShip("u1", "p1", 42.0, Instant.now()));

        Page<Ship> result = feedService.getTrendingFeed(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(only.getId());
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    private Ship buildShip(String userId, String projectId, double trendingScore, Instant createdAt) {
        return Ship.builder()
                .userId(userId)
                .projectId(projectId)
                .content("Ship content for " + userId)
                .trendingScore(trendingScore)
                .likeCount(0)
                .commentCount(0)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
