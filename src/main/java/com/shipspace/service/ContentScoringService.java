package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.ContentScore;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ContentScoreRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Computes quality, relevance, and performance scores for content items.
 *
 * Phase 3 refactor: extracted from PersonalizationService.
 * Single responsibility: scoring logic only — no feed building, no profile management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentScoringService {

    private final ShipRepository shipRepository;
    private final ProjectRepository projectRepository;
    private final BehavioralSignalRepository behavioralSignalRepository;
    private final ContentScoreRepository contentScoreRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Quality score (0.0–1.0) based on engagement relative to content age.
     * Uses denormalized counts from the Ship document — no extra DB queries.
     */
    public double qualityScore(Ship ship) {
        return qualityScore(ship, 0L, 0L);
    }

    public double qualityScore(Ship ship, long views, long follows) {
        int likes = ship.getLikeCount() != null ? ship.getLikeCount() : 0;
        int comments = ship.getCommentCount() != null ? ship.getCommentCount() : 0;
        double raw = (likes * 1.0) + (comments * 2.0) + (views * 0.5) + (follows * 3.0);
        long hours = Duration.between(ship.getCreatedAt(), Instant.now()).toHours();
        double ageFactor = Math.max(1.0, hours);
        return Math.min(1.0, (raw / Math.sqrt(ageFactor)) / 100.0);
    }

    /**
     * Relevance score (0.1–1.0) based on tag match and followed-builder boost.
     */
    public double relevanceScore(UserInterestProfile profile, Ship ship, Project project) {
        List<String> techTags = (project != null && project.getTechTags() != null)
                ? project.getTechTags() : Collections.emptyList();
        String builderId = ship != null ? ship.getUserId() : null;

        double tagMatchScore = 0.0;
        if (!techTags.isEmpty() && profile.getTechTagScores() != null) {
            double total = techTags.stream()
                    .mapToDouble(tag -> profile.getTechTagScores().getOrDefault(tag, 0.0))
                    .sum();
            tagMatchScore = total / techTags.size();
        }

        double builderBoost = (builderId != null
                && profile.getFollowedBuilders() != null
                && profile.getFollowedBuilders().contains(builderId)) ? 0.3 : 0.0;

        return Math.max(0.1, Math.min(1.0, tagMatchScore + builderBoost));
    }

    /**
     * Performance score from cached ContentScore. Returns 0.0 (neutral) if not cached.
     * Background job populates this — avoids blocking feed generation with extra DB calls.
     */
    public double performanceScore(Ship ship) {
        ContentScore cached = contentScoreRepository.findByContentId(ship.getId()).orElse(null);
        if (cached != null && cached.getPerformanceScore() != null) {
            Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
            if (cached.getUpdatedAt() != null && cached.getUpdatedAt().isAfter(oneHourAgo)) {
                return cached.getPerformanceScore();
            }
        }
        return 0.0;
    }

    /**
     * Combined score: 30% quality + 50% relevance + 20% performance.
     */
    public double combinedScore(double quality, double relevance, double performance) {
        return (0.3 * quality) + (0.5 * relevance) + (0.2 * performance);
    }

    /**
     * Boost new content (<24h old) by 0.2 to give it initial visibility.
     */
    public double applyColdStartBoost(double score, Instant createdAt) {
        long hours = Duration.between(createdAt, Instant.now()).toHours();
        return (hours < 24) ? score + 0.2 : score;
    }

    /**
     * Batch-fetch VIEW and FOLLOW signal counts for a list of ship IDs.
     * Two aggregation queries replace 2×N individual count queries.
     * Returns map of shipId → long[]{viewCount, followCount}.
     */
    public Map<String, long[]> batchSignalCounts(List<String> shipIds) {
        Map<String, long[]> result = new java.util.HashMap<>();
        for (String id : shipIds) result.put(id, new long[]{0L, 0L});

        BehavioralSignal.SignalType[] types = {
            BehavioralSignal.SignalType.VIEW, BehavioralSignal.SignalType.FOLLOW
        };
        for (int i = 0; i < types.length; i++) {
            final int idx = i;
            BehavioralSignal.SignalType type = types[i];
            try {
                Aggregation agg = Aggregation.newAggregation(
                        Aggregation.match(Criteria.where("contentId").in(shipIds)
                                .and("signalType").is(type.name())),
                        Aggregation.group("contentId").count().as("cnt")
                );
                AggregationResults<Map> res = mongoTemplate.aggregate(
                        agg, "behavioral_signals", Map.class);
                for (Map<?, ?> row : res.getMappedResults()) {
                    String id = (String) row.get("_id");
                    long cnt = row.get("cnt") instanceof Number n ? n.longValue() : 0L;
                    if (result.containsKey(id)) result.get(id)[idx] = cnt;
                }
            } catch (Exception e) {
                log.warn("Batch signal count aggregation failed for {}: {}", type, e.getMessage());
            }
        }
        return result;
    }

    private record CohortStats(double median, double stddev) {}
}
