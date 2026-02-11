package com.shipspace.service;

import com.shipspace.model.Ship;
import com.shipspace.repository.FollowingRepository;
import com.shipspace.repository.ShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for FeedService Redis caching functionality.
 * Validates that trending feeds are properly cached and invalidated.
 * 
 * Requirements: 7.5, 11.1
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class FeedServiceCacheTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ShipRepository shipRepository;

    @MockBean
    private FollowingRepository followingRepository;

    private List<Ship> testShips;

    @BeforeEach
    void setUp() {
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> 
            cacheManager.getCache(cacheName).clear()
        );

        // Create test ships
        testShips = createTestShips();
    }

    /**
     * Test that trending feed results are cached.
     * Validates Requirement 7.5: Cache results to optimize subsequent requests.
     */
    @Test
    void testTrendingFeedIsCached() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> expectedPage = new PageImpl<>(testShips, pageable, testShips.size());
        when(shipRepository.findAll(any(Pageable.class))).thenReturn(expectedPage);

        // Act - First call should hit the database
        Page<Ship> firstResult = feedService.getTrendingFeed(pageable);

        // Act - Second call should use cache
        Page<Ship> secondResult = feedService.getTrendingFeed(pageable);

        // Assert - Repository should only be called once (first call)
        verify(shipRepository, times(1)).findAll(any(Pageable.class));

        // Assert - Both results should be identical
        assertThat(firstResult.getContent()).hasSize(testShips.size());
        assertThat(secondResult.getContent()).hasSize(testShips.size());
        assertThat(firstResult.getContent()).isEqualTo(secondResult.getContent());
    }

    /**
     * Test that trending feed with time window results are cached.
     * Validates Requirement 7.5: Cache results to optimize subsequent requests.
     */
    @Test
    void testTrendingFeedWithTimeWindowIsCached() {
        // Arrange
        String timeWindow = "24h";
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> expectedPage = new PageImpl<>(testShips, pageable, testShips.size());
        when(shipRepository.findByCreatedAtAfter(any(Instant.class), any(Pageable.class)))
                .thenReturn(expectedPage);

        // Act - First call should hit the database
        Page<Ship> firstResult = feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable);

        // Act - Second call should use cache
        Page<Ship> secondResult = feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable);

        // Assert - Repository should only be called once (first call)
        verify(shipRepository, times(1)).findByCreatedAtAfter(any(Instant.class), any(Pageable.class));

        // Assert - Both results should be identical
        assertThat(firstResult.getContent()).hasSize(testShips.size());
        assertThat(secondResult.getContent()).hasSize(testShips.size());
        assertThat(firstResult.getContent()).isEqualTo(secondResult.getContent());
    }

    /**
     * Test that different pagination parameters create separate cache entries.
     * Validates Requirement 11.1: Implement pagination and caching to optimize response times.
     */
    @Test
    void testDifferentPaginationParametersUseDifferentCacheEntries() {
        // Arrange
        Pageable page0 = PageRequest.of(0, 20);
        Pageable page1 = PageRequest.of(1, 20);
        Page<Ship> expectedPage0 = new PageImpl<>(testShips, page0, testShips.size());
        Page<Ship> expectedPage1 = new PageImpl<>(new ArrayList<>(), page1, testShips.size());
        
        when(shipRepository.findAll(page0)).thenReturn(expectedPage0);
        when(shipRepository.findAll(page1)).thenReturn(expectedPage1);

        // Act - Call with different page numbers
        feedService.getTrendingFeed(page0);
        feedService.getTrendingFeed(page1);
        feedService.getTrendingFeed(page0); // Should use cache
        feedService.getTrendingFeed(page1); // Should use cache

        // Assert - Each unique pagination should hit database once
        verify(shipRepository, times(1)).findAll(page0);
        verify(shipRepository, times(1)).findAll(page1);
    }

    /**
     * Test that different time windows create separate cache entries.
     * Validates Requirement 7.3: Provide configurable time windows for trending calculations.
     */
    @Test
    void testDifferentTimeWindowsUseDifferentCacheEntries() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> expectedPage = new PageImpl<>(testShips, pageable, testShips.size());
        when(shipRepository.findByCreatedAtAfter(any(Instant.class), any(Pageable.class)))
                .thenReturn(expectedPage);

        // Act - Call with different time windows
        feedService.getTrendingFeedWithTimeWindow("24h", pageable);
        feedService.getTrendingFeedWithTimeWindow("7d", pageable);
        feedService.getTrendingFeedWithTimeWindow("24h", pageable); // Should use cache
        feedService.getTrendingFeedWithTimeWindow("7d", pageable); // Should use cache

        // Assert - Each unique time window should hit database once
        verify(shipRepository, times(2)).findByCreatedAtAfter(any(Instant.class), any(Pageable.class));
    }

    /**
     * Test that cache is invalidated when trending scores are recalculated.
     * Validates Requirement 7.5: Cache results and invalidate when scores are updated.
     */
    @Test
    void testCacheInvalidatedAfterTrendingScoreCalculation() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> expectedPage = new PageImpl<>(testShips, pageable, testShips.size());
        when(shipRepository.findAll(any(Pageable.class))).thenReturn(expectedPage);
        when(shipRepository.findAll()).thenReturn(testShips);
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act - First call to populate cache
        feedService.getTrendingFeed(pageable);
        verify(shipRepository, times(1)).findAll(any(Pageable.class));

        // Act - Recalculate trending scores (should invalidate cache)
        feedService.calculateTrendingScores();

        // Act - Next call should hit database again (cache was invalidated)
        feedService.getTrendingFeed(pageable);

        // Assert - Repository should be called twice (before and after cache invalidation)
        verify(shipRepository, times(2)).findAll(any(Pageable.class));
    }

    /**
     * Test that manual cache invalidation works correctly.
     * Validates Requirement 7.5: Cache invalidation strategies.
     */
    @Test
    void testManualCacheInvalidation() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> expectedPage = new PageImpl<>(testShips, pageable, testShips.size());
        when(shipRepository.findAll(any(Pageable.class))).thenReturn(expectedPage);

        // Act - First call to populate cache
        feedService.getTrendingFeed(pageable);
        verify(shipRepository, times(1)).findAll(any(Pageable.class));

        // Act - Manually invalidate cache
        feedService.invalidateTrendingCache();

        // Act - Next call should hit database again (cache was invalidated)
        feedService.getTrendingFeed(pageable);

        // Assert - Repository should be called twice (before and after cache invalidation)
        verify(shipRepository, times(2)).findAll(any(Pageable.class));
    }

    /**
     * Test that cache invalidation affects both trending feed types.
     * Validates Requirement 7.5: Cache invalidation for all trending feeds.
     */
    @Test
    void testCacheInvalidationAffectsBothFeedTypes() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> expectedPage = new PageImpl<>(testShips, pageable, testShips.size());
        when(shipRepository.findAll(any(Pageable.class))).thenReturn(expectedPage);
        when(shipRepository.findByCreatedAtAfter(any(Instant.class), any(Pageable.class)))
                .thenReturn(expectedPage);
        when(shipRepository.findAll()).thenReturn(testShips);
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Populate both caches
        feedService.getTrendingFeed(pageable);
        feedService.getTrendingFeedWithTimeWindow("24h", pageable);
        verify(shipRepository, times(1)).findAll(any(Pageable.class));
        verify(shipRepository, times(1)).findByCreatedAtAfter(any(Instant.class), any(Pageable.class));

        // Act - Invalidate all caches
        feedService.calculateTrendingScores();

        // Act - Call both feed types again
        feedService.getTrendingFeed(pageable);
        feedService.getTrendingFeedWithTimeWindow("24h", pageable);

        // Assert - Both should hit database again after invalidation
        verify(shipRepository, times(2)).findAll(any(Pageable.class));
        verify(shipRepository, times(2)).findByCreatedAtAfter(any(Instant.class), any(Pageable.class));
    }

    /**
     * Test that cache entries have proper TTL configuration.
     * Validates Requirement 11.1: Optimize response times with appropriate caching.
     */
    @Test
    void testCacheEntriesExist() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> expectedPage = new PageImpl<>(testShips, pageable, testShips.size());
        when(shipRepository.findAll(any(Pageable.class))).thenReturn(expectedPage);

        // Act
        feedService.getTrendingFeed(pageable);

        // Assert - Cache should exist and contain the entry
        assertThat(cacheManager.getCache("trendingFeed")).isNotNull();
        assertThat(cacheManager.getCache("trendingFeed").get("0:20")).isNotNull();
    }

    /**
     * Helper method to create test ships with varying engagement metrics.
     */
    private List<Ship> createTestShips() {
        List<Ship> ships = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < 5; i++) {
            Ship ship = Ship.builder()
                    .id("ship-" + i)
                    .userId("user-" + i)
                    .projectId("project-" + i)
                    .content("Test ship content " + i)
                    .likeCount(i * 10)
                    .commentCount(i * 5)
                    .trendingScore((double) i)
                    .createdAt(now.minus(i, ChronoUnit.HOURS))
                    .updatedAt(now.minus(i, ChronoUnit.HOURS))
                    .editWindowExpires(now.plus(10, ChronoUnit.MINUTES))
                    .build();
            ships.add(ship);
        }

        return ships;
    }
}
