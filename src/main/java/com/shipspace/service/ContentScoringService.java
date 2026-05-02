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

    private static final int MIN_COHORT_SIZE = 5;

    // ── Quality Score ─────────────────────────────────────────────────────────

    /**
     * Quality score (0.0–1.0) for a Ship, querying behavioral signals from the repo.
     */
    public double qualityScore(Ship ship) {
        long views = behavioralSignalRepository.countByContentIdAndSignalType(
                ship.getId(), BehavioralSignal.SignalType.VIEW);
        long follows = behavioralSignalRepository.countByContentIdAndSignalType(
                ship.getId(), BehavioralSignal.SignalType.FOLLOW);
        return qualityScore(ship, views, follows);
    }

    /**
     * Quality score (0.0–1.0) with pre-fetched engagement counts.
     * Formula: raw = likes*1 + comments*2 + views*0.5 + follows*3
     *           normalized = raw / sqrt(ageHours), capped at 100.
     */
    public double qualityScore(Ship ship, long views, long follows) {
        int likes = ship.getLikeCount() != null ? ship.getLikeCount() : 0;
        int comments = ship.getCommentCount() != null ? ship.getCommentCount() : 0;
        double raw = (likes * 1.0) + (comments * 2.0) + (views * 0.5) + (follows * 3.0);
        long hours = Duration.between(ship.getCreatedAt(), Instant.now()).toHours();
        double ageFactor = Math.max(1.0, hours);
        return Math.min(1.0, (raw / Math.sqrt(ageFactor)) / 100.0);
    }

    /**
     * Quality score (0.0–1.0) for a Project using behavioral signal counts.
     */
    public double qualityScore(Project project, long views, long follows) {
        double raw = (views * 0.5) + (follows * 3.0);
        long hours = Duration.between(project.getCreatedAt(), Instant.now()).toHours();
        double ageFactor = Math.max(1.0, hours);
        return Math.min(1.0, (raw / Math.sqrt(ageFactor)) / 100.0);
    }

    /**
     * Quality score (0.0–1.0) for a Project, querying behavioral signals internally.
     */
    public double qualityScore(Project project) {
        long views = behavioralSignalRepository.countByContentIdAndSignalType(
                project.getId(), BehavioralSignal.SignalType.VIEW);
        long follows = behavioralSignalRepository.countByContentIdAndSignalType(
                project.getId(), BehavioralSignal.SignalType.FOLLOW);
        return qualityScore(project, views, follows);
    }

    // ── Relevance Score ───────────────────────────────────────────────────────

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

    // ── Performance Score ─────────────────────────────────────────────────────

    /**
     * Performance score by content ID (cache-first, full algorithm).
     *
     * Algorithm:
     *  1. Check contentScoreRepository for a fresh cached score (< 1 hour).
     *     If found, return it immediately — shipRepository is NOT called.
     *  2. Look up Ship from shipRepository; if not found, fall back to Project.
     *     Throws IllegalArgumentException if neither exists.
     *  3. Compute engagement rate via calculateEngagementRate(contentId) — which
     *     calls shipRepository again (documented intentional double-call for Ships).
     *  4. Determine age cohort; compare against cohort averages.
     *  5. Save updated ContentScore; return score.
     */
    public double performanceScore(String contentId) {
        // Step 1: fresh cache check — do NOT call shipRepository if cache is fresh
        ContentScore cached = contentScoreRepository.findByContentId(contentId).orElse(null);
        if (cached != null && cached.getPerformanceScore() != null && cached.getUpdatedAt() != null) {
            Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
            if (cached.getUpdatedAt().isAfter(oneHourAgo)) {
                return cached.getPerformanceScore();
            }
        }

        // Step 2: resolve content type (call #1 on shipRepository for ships)
        var shipOpt = shipRepository.findById(contentId);
        Instant createdAt;
        BehavioralSignal.ContentType contentType;

        if (shipOpt.isPresent()) {
            createdAt = shipOpt.get().getCreatedAt();
            contentType = BehavioralSignal.ContentType.SHIP;
        } else {
            var projectOpt = projectRepository.findById(contentId);
            if (projectOpt.isEmpty()) {
                throw new IllegalArgumentException("Content not found: " + contentId);
            }
            createdAt = projectOpt.get().getCreatedAt();
            contentType = BehavioralSignal.ContentType.PROJECT;
        }

        // Step 3: engagement rate.
        // For Ships, calculateEngagementRate(contentId) re-queries shipRepository (call #2).
        // For Projects, we pass the pre-resolved createdAt to avoid an extra repo call.
        double engagementRate = (contentType == BehavioralSignal.ContentType.SHIP)
                ? calculateEngagementRate(contentId)
                : calculateEngagementRate(contentId, createdAt);

        // Step 4: cohort comparison
        String ageCohort = determineAgeCohort(createdAt);
        List<ContentScore> cohortScores = contentScoreRepository.findByAgeCohort(ageCohort);

        double score = (cohortScores.size() < MIN_COHORT_SIZE)
                ? 0.0
                : computePercentileScore(engagementRate, cohortScores);

        // Step 5: persist
        ContentScore updated = ContentScore.builder()
                .contentId(contentId)
                .contentType(contentType)
                .performanceScore(score)
                .engagementRate(engagementRate)
                .ageCohort(ageCohort)
                .updatedAt(Instant.now())
                .build();
        if (cached != null) {
            updated.setId(cached.getId());
        }
        contentScoreRepository.save(updated);

        return score;
    }

    /**
     * Performance score from a Ship object (lightweight, cache-only check).
     * Returns 0.0 (neutral) when no fresh cache exists.
     * Used in the feed hot path to avoid blocking on signal queries.
     */
    public double performanceScore(Ship ship) {
        ContentScore cached = contentScoreRepository.findByContentId(ship.getId()).orElse(null);
        if (cached != null && cached.getPerformanceScore() != null && cached.getUpdatedAt() != null) {
            Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
            if (cached.getUpdatedAt().isAfter(oneHourAgo)) {
                return cached.getPerformanceScore();
            }
        }
        return 0.0;
    }

    // ── Combined Score ────────────────────────────────────────────────────────

    /** Combined score: 30% quality + 50% relevance + 20% performance. */
    public double combinedScore(double quality, double relevance, double performance) {
        return (0.3 * quality) + (0.5 * relevance) + (0.2 * performance);
    }

    // ── Cold Start ────────────────────────────────────────────────────────────

    /** Boost new content (<24h old) by 0.2 to give it initial visibility. */
    public double applyColdStartBoost(double score, Instant createdAt) {
        long hours = Duration.between(createdAt, Instant.now()).toHours();
        return (hours < 24) ? score + 0.2 : score;
    }

    // ── Batch Signals ─────────────────────────────────────────────────────────

    /**
     * Batch-fetch VIEW and FOLLOW signal counts for a list of ship IDs.
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Compute engagement rate: (views + 5×follows) / max(1, ageHours).
     * Uses pre-resolved createdAt to avoid an extra repo call in the project path.
     */
    public double calculateEngagementRate(String contentId, Instant createdAt) {
        long views = behavioralSignalRepository.countByContentIdAndSignalType(
                contentId, BehavioralSignal.SignalType.VIEW);
        long follows = behavioralSignalRepository.countByContentIdAndSignalType(
                contentId, BehavioralSignal.SignalType.FOLLOW);
        long hours = Math.max(1L, Duration.between(createdAt, Instant.now()).toHours());
        return (double) (views + 5 * follows) / hours;
    }

    /**
     * Compute engagement rate for a Ship by its ID (resolves createdAt from shipRepository).
     * This is the second shipRepository call in the stale-cache recalculation path.
     */
    public double calculateEngagementRate(String contentId) {
        Ship ship = shipRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Ship not found: " + contentId));
        return calculateEngagementRate(contentId, ship.getCreatedAt());
    }

    /** Age cohort buckets used for percentile comparison. */
    public String determineAgeCohort(Instant createdAt) {
        long hours = Duration.between(createdAt, Instant.now()).toHours();
        if (hours < 24) return "0-24h";
        if (hours < 24 * 7) return "1-7d";
        if (hours < 24 * 30) return "7-30d";
        return "30d+";
    }

    /**
     * Maps engagement rate to [-1.0, 1.0] via percentile rank in cohort.
     * Above median → positive; below median → negative.
     */
    private double computePercentileScore(double engagementRate, List<ContentScore> cohortScores) {
        if (cohortScores.size() < MIN_COHORT_SIZE) return 0.0;
        long below = cohortScores.stream()
                .filter(cs -> cs.getEngagementRate() != null && cs.getEngagementRate() < engagementRate)
                .count();
        double percentile = (double) below / cohortScores.size();
        return Math.max(-1.0, Math.min(1.0, (percentile - 0.5) * 2.0));
    }

    private record CohortStats(double median, double stddev) {}
}
