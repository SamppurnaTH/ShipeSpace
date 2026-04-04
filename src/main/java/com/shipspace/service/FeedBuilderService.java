package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds personalized and trending feed pages.
 *
 * Phase 3 refactor: extracted from PersonalizationService.
 * Single responsibility: feed assembly — candidate selection, scoring, hybrid interleaving,
 * and pagination. Delegates scoring to ContentScoringService and profile lookup to
 * InterestProfileService.
 *
 * Cold-start strategy: users with <10 signals get a pure trending feed.
 * Warm users: 50/50 hybrid of personalized + trending, deduplicated.
 * Candidate pool: capped at 500 ships from the last 7 days (index-backed range query).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedBuilderService {

    private final ShipRepository shipRepository;
    private final ProjectRepository projectRepository;
    private final BehavioralSignalRepository behavioralSignalRepository;
    private final InterestProfileService interestProfileService;
    private final ContentScoringService contentScoringService;
    private final Timer feedGenerationTimer;
    private final Counter coldStartFeedCounter;

    /**
     * Generate a personalized feed page for the given user.
     */
    public Page<Ship> getPersonalizedFeed(String userId, Pageable pageable) {
        return feedGenerationTimer.record(() -> buildFeed(userId, pageable));
    }

    private Page<Ship> buildFeed(String userId, Pageable pageable) {
        log.debug("Building personalized feed for user: {}", userId);

        UserInterestProfile profile = interestProfileService.getProfile(userId);

        if (profile.isColdStart()) {
            log.debug("Cold-start user {}, falling back to trending feed", userId);
            coldStartFeedCounter.increment();
            Pageable sorted = PageRequest.of(
                    pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "trendingScore", "createdAt"));
            return shipRepository.findAll(sorted);
        }

        List<Ship> candidates = getCandidates(userId);

        if (candidates.isEmpty()) {
            Pageable sorted = PageRequest.of(
                    pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "trendingScore", "createdAt"));
            return shipRepository.findAll(sorted);
        }

        // Batch-fetch projects for all candidates in one query
        Set<String> projectIds = candidates.stream()
                .map(Ship::getProjectId)
                .collect(Collectors.toSet());
        Map<String, Project> projectMap = projectRepository.findAllById(projectIds)
                .stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        // Batch-fetch signal counts — 2 aggregation queries instead of 2×N
        List<String> candidateIds = candidates.stream().map(Ship::getId).collect(Collectors.toList());
        Map<String, long[]> signalCounts = contentScoringService.batchSignalCounts(candidateIds);

        // Score each candidate
        List<ScoredShip> scored = candidates.stream()
                .map(ship -> {
                    Project project = projectMap.get(ship.getProjectId());
                    long[] counts = signalCounts.getOrDefault(ship.getId(), new long[]{0L, 0L});
                    double quality = contentScoringService.qualityScore(ship, counts[0], counts[1]);
                    double relevance = contentScoringService.relevanceScore(profile, ship, project);
                    double performance = contentScoringService.performanceScore(ship);
                    double combined = contentScoringService.combinedScore(quality, relevance, performance);
                    combined = contentScoringService.applyColdStartBoost(combined, ship.getCreatedAt());
                    return new ScoredShip(ship, combined);
                })
                .sorted(Comparator.comparingDouble(ScoredShip::score).reversed())
                .collect(Collectors.toList());

        List<Ship> trendingRanked = candidates.stream()
                .sorted(Comparator.comparingDouble(
                        s -> -(s.getTrendingScore() != null ? s.getTrendingScore() : 0.0)))
                .collect(Collectors.toList());

        List<Ship> personalizedRanked = scored.stream()
                .map(ScoredShip::ship)
                .collect(Collectors.toList());

        int targetSize = Math.max(20, pageable.getPageSize() * 2);
        List<Ship> hybrid = buildHybrid(personalizedRanked, trendingRanked, targetSize);

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), hybrid.size());
        List<Ship> page = (start >= hybrid.size()) ? Collections.emptyList() : hybrid.subList(start, end);

        log.debug("Feed for user {}: {} candidates → {} hybrid → {} page items",
                userId, candidates.size(), hybrid.size(), page.size());

        return new PageImpl<>(page, pageable, hybrid.size());
    }

    /**
     * Get candidate ships: recent (last 7 days), excluding already-viewed content.
     * Capped at 500 via index-backed range query — not a full collection scan.
     */
    private List<Ship> getCandidates(String userId) {
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));

        List<BehavioralSignal> recentSignals = behavioralSignalRepository
                .findByUserIdAndTimestampAfter(userId, sevenDaysAgo);

        Set<String> viewedIds = recentSignals.stream()
                .filter(s -> s.getSignalType() == BehavioralSignal.SignalType.VIEW)
                .map(BehavioralSignal::getContentId)
                .collect(Collectors.toSet());

        Pageable bounded = PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createdAt"));
        return shipRepository.findByCreatedAtAfter(sevenDaysAgo, bounded).getContent().stream()
                .filter(ship -> !viewedIds.contains(ship.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Interleave personalized and trending lists 50/50, deduplicating by ID.
     */
    private List<Ship> buildHybrid(List<Ship> personalized, List<Ship> trending, int targetSize) {
        int half = targetSize / 2;
        List<Ship> pTop = personalized.stream().limit(half).collect(Collectors.toList());
        List<Ship> tTop = trending.stream().limit(half).collect(Collectors.toList());

        List<Ship> hybrid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int max = Math.max(pTop.size(), tTop.size());

        for (int i = 0; i < max; i++) {
            if (i < pTop.size()) {
                Ship s = pTop.get(i);
                if (seen.add(s.getId())) hybrid.add(s);
            }
            if (i < tTop.size()) {
                Ship s = tTop.get(i);
                if (seen.add(s.getId())) hybrid.add(s);
            }
        }
        return hybrid;
    }

    private record ScoredShip(Ship ship, double score) {}
}
