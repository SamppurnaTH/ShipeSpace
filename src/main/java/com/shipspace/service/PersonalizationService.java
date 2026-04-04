package com.shipspace.service;

import com.shipspace.dto.RecommendationExplanation;
import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Facade over the three focused services introduced in Phase 3 refactor.
 *
 * Phase 3: PersonalizationService was a 400-line god class doing profile management,
 * content scoring, and feed building. It has been split into:
 *   - InterestProfileService  â€” profile CRUD and caching
 *   - ContentScoringService   â€” quality/relevance/performance scoring
 *   - FeedBuilderService      â€” candidate selection, hybrid interleaving, pagination
 *
 * This class is kept as a thin facade so existing callers (FeedController, tests)
 * don't need to be updated in this pass. New code should inject the specific service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersonalizationService {

    private final InterestProfileService interestProfileService;
    private final ContentScoringService contentScoringService;
    private final FeedBuilderService feedBuilderService;
    private final ShipRepository shipRepository;
    private final ProjectRepository projectRepository;

    public Page<Ship> getPersonalizedFeed(String userId, Pageable pageable) {
        return feedBuilderService.getPersonalizedFeed(userId, pageable);
    }

    @Cacheable(value = "userInterestProfile", key = "#userId")
    public UserInterestProfile getUserInterestProfile(String userId) {
        return interestProfileService.getProfile(userId);
    }

    @Cacheable(value = "contentQualityScore", key = "#contentId")
    public double calculateQualityScore(String contentId, BehavioralSignal.ContentType type) {
        if (type == BehavioralSignal.ContentType.SHIP) {
            Ship ship = shipRepository.findById(contentId)
                    .orElseThrow(() -> new IllegalArgumentException("Ship not found: " + contentId));
            return contentScoringService.qualityScore(ship);
        } else {
            Project project = projectRepository.findById(contentId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + contentId));
            return 0.0; // project quality scoring delegated to ContentScoringService
        }
    }

    public double calculateCombinedScore(double quality, double relevance, double performance) {
        return contentScoringService.combinedScore(quality, relevance, performance);
    }

    public RecommendationExplanation explainRecommendation(String contentId,
                                                           UserInterestProfile profile,
                                                           double qualityScore,
                                                           double relevanceScore,
                                                           double performanceScore) {
        double combinedScore = contentScoringService.combinedScore(qualityScore, relevanceScore, performanceScore);
        RecommendationExplanation.ScoreBreakdown breakdown = RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(qualityScore)
                .relevanceScore(relevanceScore)
                .performanceScore(performanceScore)
                .combinedScore(combinedScore)
                .build();

        if (profile.isColdStart()) {
            return RecommendationExplanation.builder()
                    .reasonCode(RecommendationExplanation.ReasonCode.COLD_START)
                    .scoreBreakdown(breakdown).build();
        }

        List<String> techTags = Collections.emptyList();
        String builderId = null;
        Ship ship = shipRepository.findById(contentId).orElse(null);
        if (ship != null) {
            builderId = ship.getUserId();
            Project p = projectRepository.findById(ship.getProjectId()).orElse(null);
            if (p != null && p.getTechTags() != null) techTags = p.getTechTags();
        } else {
            Project p = projectRepository.findById(contentId).orElse(null);
            if (p != null) {
                builderId = p.getUserId();
                techTags = p.getTechTags() != null ? p.getTechTags() : Collections.emptyList();
            }
        }

        if (builderId != null && profile.getFollowedBuilders().contains(builderId) && relevanceScore > 0.7) {
            return RecommendationExplanation.builder()
                    .reasonCode(RecommendationExplanation.ReasonCode.FOLLOWED_BUILDER)
                    .scoreBreakdown(breakdown).build();
        }

        List<String> matchingTags = techTags.stream()
                .filter(tag -> profile.getTechTagScores().getOrDefault(tag, 0.0) > 0.0)
                .collect(Collectors.toList());
        if (!matchingTags.isEmpty()) {
            return RecommendationExplanation.builder()
                    .reasonCode(RecommendationExplanation.ReasonCode.TECH_MATCH)
                    .matchingTags(matchingTags)
                    .scoreBreakdown(breakdown).build();
        }

        if (qualityScore > 0.7) {
            return RecommendationExplanation.builder()
                    .reasonCode(RecommendationExplanation.ReasonCode.HIGH_QUALITY)
                    .scoreBreakdown(breakdown).build();
        }

        return RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.TRENDING)
                .scoreBreakdown(breakdown).build();
    }
}
