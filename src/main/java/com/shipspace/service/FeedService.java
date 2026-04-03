package com.shipspace.service;

import com.shipspace.model.Following;
import com.shipspace.model.Ship;
import com.shipspace.repository.FollowingRepository;
import com.shipspace.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating and managing content feeds.
 * Provides following feed (chronological) and trending feed (engagement-based) functionality.
 * Implements Redis caching for trending feeds to optimize performance.
 * 
 * Requirements: 6.1, 6.2, 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 7.5, 11.1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedService {

    private final ShipRepository shipRepository;
    private final FollowingRepository followingRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Generate a chronological feed of ships from users that the authenticated user follows.
     * Returns ships in reverse chronological order (most recent first).
     * 
     * Requirements: 6.1, 6.2, 6.4, 6.5
     * 
     * @param userId the ID of the authenticated user
     * @param pageable pagination and sorting parameters
     * @return Page of ships from followed users
     */
    public Page<Ship> getFollowingFeed(String userId, Pageable pageable) {
        log.debug("Generating following feed for user: {}", userId);
        
        // Get list of users that the authenticated user follows — capped at 500 to prevent heap blow-up.
        // Users following more than 500 people are rare; if needed, paginate this in a future iteration.
        org.springframework.data.domain.Pageable followingCap =
                PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<String> followeeIds = followingRepository.findByFollowerId(userId, followingCap)
                .getContent()
                .stream()
                .map(Following::getFolloweeId)
                .collect(Collectors.toList());
        
        // If user doesn't follow anyone, return empty page
        if (followeeIds.isEmpty()) {
            log.debug("User {} doesn't follow anyone, returning empty feed", userId);
            return Page.empty(pageable);
        }
        
        // Create pageable with reverse chronological ordering
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        
        // Query ships from followed users
        Page<Ship> followingFeed = shipRepository.findByUserIdIn(followeeIds, sortedPageable);
        
        log.debug("Following feed for user {} returned {} ships", userId, followingFeed.getTotalElements());
        return followingFeed;
    }

    /**
     * Generate a trending feed of ships ranked by engagement scoring algorithm.
     * Includes ships from all users regardless of following relationships.
     * 
     * Results are cached in Redis to optimize performance. Cache is invalidated
     * when trending scores are recalculated.
     * 
     * Cache key format: trendingFeed::{pageNumber}:{pageSize}
     * 
     * Requirements: 7.1, 7.2, 7.4, 7.5, 11.1
     * 
     * @param pageable pagination and sorting parameters
     * @return Page of ships ordered by trending score
     */
    @Cacheable(value = "trendingFeed", key = "#pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<Ship> getTrendingFeed(Pageable pageable) {
        log.debug("Generating trending feed (all time) - page: {}, size: {}", 
                pageable.getPageNumber(), pageable.getPageSize());
        
        // Create pageable with trending score ordering
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "trendingScore", "createdAt")
        );
        
        // Query all ships ordered by trending score
        Page<Ship> trendingFeed = shipRepository.findAll(sortedPageable);
        
        log.debug("Trending feed returned {} ships (cached)", trendingFeed.getTotalElements());
        return trendingFeed;
    }

    /**
     * Generate a trending feed with a specific time window.
     * Only includes ships created within the specified time window.
     * 
     * Results are cached in Redis to optimize performance. Cache is invalidated
     * when trending scores are recalculated.
     * 
     * Cache key format: trendingFeedWithTimeWindow::{timeWindow}:{pageNumber}:{pageSize}
     * 
     * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 11.1
     * 
     * @param timeWindow the time window for trending calculation (e.g., "24h", "7d", "30d")
     * @param pageable pagination and sorting parameters
     * @return Page of ships within the time window ordered by trending score
     */
    @Cacheable(value = "trendingFeedWithTimeWindow", 
               key = "#timeWindow + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<Ship> getTrendingFeedWithTimeWindow(String timeWindow, Pageable pageable) {
        log.debug("Generating trending feed with time window: {} - page: {}, size: {}", 
                timeWindow, pageable.getPageNumber(), pageable.getPageSize());
        
        // Calculate the start time based on the time window
        Instant startTime = calculateStartTime(timeWindow);
        
        // Create pageable with trending score ordering
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "trendingScore", "createdAt")
        );
        
        // Query ships within the time window ordered by trending score
        Page<Ship> trendingFeed = shipRepository.findByCreatedAtAfter(startTime, sortedPageable);
        
        log.debug("Trending feed with time window {} returned {} ships (cached)", 
                timeWindow, trendingFeed.getTotalElements());
        return trendingFeed;
    }

    /**
     * Calculate trending scores for all ships in batches of 200.
     * Avoids loading the entire collection into heap.
     */
    @CacheEvict(value = {"trendingFeed", "trendingFeedWithTimeWindow"}, allEntries = true)
    public void calculateTrendingScores() {
        log.info("Starting trending score calculation");

        Instant now = Instant.now();
        int page = 0;
        final int batchSize = 200;
        int updatedCount = 0;

        Page<Ship> batch;
        do {
            batch = shipRepository.findAll(PageRequest.of(page, batchSize));
            List<Ship> dirty = batch.getContent().stream()
                    .filter(ship -> {
                        double newScore = calculateEngagementScore(ship, now);
                        if (Math.abs(ship.getTrendingScore() - newScore) > 0.001) {
                            ship.setTrendingScore(newScore);
                            return true;
                        }
                        return false;
                    })
                    .collect(java.util.stream.Collectors.toList());

            if (!dirty.isEmpty()) {
                // Bulk unordered writes — one round-trip per batch instead of N
                BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Ship.class);
                for (Ship ship : dirty) {
                    Query q = new Query(Criteria.where("_id").is(ship.getId()));
                    Update u = new Update().set("trendingScore", ship.getTrendingScore());
                    bulkOps.updateOne(q, u);
                }
                bulkOps.execute();
                updatedCount += dirty.size();
            }
            page++;
        } while (batch.hasNext());

        log.info("Trending score calculation completed. Updated {} ships. Cache invalidated.", updatedCount);
    }

    /**
     * Scheduled trending score recalculation — runs every 15 minutes.
     * Idempotent: only updates ships whose score changed by > 0.001.
     */
    @Scheduled(fixedRateString = "${feed.trending.recalculation-interval-ms:900000}")
    public void scheduledTrendingRecalculation() {
        log.info("Scheduled trending score recalculation triggered");
        try {
            calculateTrendingScores();
        } catch (Exception e) {
            log.error("Scheduled trending recalculation failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Manually invalidate all trending feed caches.
     * This can be called when engagement data changes (likes, comments) to ensure
     * the trending feed reflects the latest data.
     * 
     * Requirements: 7.5, 11.1
     */
    @CacheEvict(value = {"trendingFeed", "trendingFeedWithTimeWindow"}, allEntries = true)
    public void invalidateTrendingCache() {
        log.info("Manually invalidating trending feed caches");
    }

    /**
     * Calculate the engagement score for a single ship.
     * 
     * @param ship the ship to calculate score for
     * @param now the current timestamp
     * @return the calculated engagement score
     */
    private double calculateEngagementScore(Ship ship, Instant now) {
        // Get engagement metrics
        int likes = ship.getLikeCount() != null ? ship.getLikeCount() : 0;
        int comments = ship.getCommentCount() != null ? ship.getCommentCount() : 0;
        
        // Calculate age in hours
        long ageInHours = ChronoUnit.HOURS.between(ship.getCreatedAt(), now);
        
        // Prevent division by zero and ensure minimum age of 1 hour
        double effectiveAge = Math.max(1.0, ageInHours);
        
        // Calculate engagement score
        // Comments are weighted more heavily than likes (2.0 vs 1.0)
        double engagementPoints = (likes * 1.0) + (comments * 2.0);
        
        // Apply time decay factor (age + 2)^1.5
        // The +2 prevents new posts from having infinite scores
        double decayFactor = Math.pow(effectiveAge + 2.0, 1.5);
        
        // Calculate final score
        double score = engagementPoints / decayFactor;
        
        log.trace("Ship {} score: likes={}, comments={}, age={}h, score={}", 
                ship.getId(), likes, comments, ageInHours, score);
        
        return score;
    }

    /**
     * Calculate the start time for a given time window string.
     * 
     * @param timeWindow the time window string (e.g., "24h", "7d", "30d")
     * @return the start time instant
     * @throws IllegalArgumentException if the time window format is invalid
     */
    private Instant calculateStartTime(String timeWindow) {
        Instant now = Instant.now();
        
        if (timeWindow == null || timeWindow.isEmpty()) {
            throw new IllegalArgumentException("Time window cannot be null or empty");
        }
        
        // Parse the time window format (e.g., "24h", "7d", "30d")
        String unit = timeWindow.substring(timeWindow.length() - 1).toLowerCase();
        String valueStr = timeWindow.substring(0, timeWindow.length() - 1);
        
        try {
            long value = Long.parseLong(valueStr);
            
            return switch (unit) {
                case "h" -> now.minus(value, ChronoUnit.HOURS);
                case "d" -> now.minus(value, ChronoUnit.DAYS);
                case "w" -> now.minus(value * 7, ChronoUnit.DAYS); // Convert weeks to days
                case "m" -> now.minus(value * 30, ChronoUnit.DAYS); // Approximate month as 30 days
                default -> throw new IllegalArgumentException("Invalid time window unit: " + unit + ". Use h (hours), d (days), w (weeks), or m (months)");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid time window format: " + timeWindow + ". Expected format: <number><unit> (e.g., 24h, 7d, 30d)", e);
        }
    }
}
