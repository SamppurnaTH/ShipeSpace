package com.shipspace.service;

import com.shipspace.dto.FeedItemResponse;
import com.shipspace.dto.RecommendationExplanation;
import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.ContentScore;
import com.shipspace.model.Ship;
import com.shipspace.model.Project;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ContentScoreRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class PersonalizationService {

    private final UserInterestProfileRepository userInterestProfileRepository;
    private final BehavioralSignalRepository behavioralSignalRepository;
    private final ContentScoreRepository contentScoreRepository;
    private final ShipRepository shipRepository;
    private final ProjectRepository projectRepository;
    
    /**
     * Apply cold-start boost to new content.
     * Adds +0.2 to combined score for content < 24 hours old.
     */
    private double applyColdStartBoost(double combinedScore, Instant createdAt) {
        if (isColdStartContent(createdAt)) {
            log.debug("Applying cold-start boost (+0.2) to content created at {}", createdAt);
            return combinedScore + 0.2;
        }
        return combinedScore;
    }

    /**
     * Get user interest profile with caching (5min TTL).
     * 
     * The profile contains:
     * - Tech tag interest scores (0.0 to 1.0)
     * - Followed builders
     * - Total behavioral signals count
     * - Timestamps for decay calculations
     * 
     * If profile doesn't exist, returns an empty profile (cold-start user).
     * 
     * Requirements: 8.2
     * 
     * @param userId the user ID
     * @return UserInterestProfile (never null)
     */
    @Cacheable(value = "userInterestProfile", key = "#userId")
    public UserInterestProfile getUserInterestProfile(String userId) {
        log.debug("Loading user interest profile for user: {}", userId);
        
        // Query repository by userId
        Optional<UserInterestProfile> profileOpt = userInterestProfileRepository.findByUserId(userId);
        
        if (profileOpt.isPresent()) {
            UserInterestProfile profile = profileOpt.get();
            log.debug("Found existing profile for user {}: {} signals, {} tags, {} followed builders",
                    userId, profile.getTotalSignals(), 
                    profile.getTechTagScores().size(),
                    profile.getFollowedBuilders().size());
            return profile;
        }
        
        // Return empty profile for cold-start users
        log.debug("No profile found for user {}, returning empty profile (cold-start)", userId);
        return UserInterestProfile.builder()
                .userId(userId)
                .techTagScores(new HashMap<>())
                .followedBuilders(new HashSet<>())
                .totalSignals(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Calculate content quality score based on engagement metrics.
     * Cached with 1-hour TTL.
     * 
     * Formula:
     * - raw_quality = (likes * 1.0) + (comments * 2.0) + (views * 0.5) + (follows * 3.0)
     * - age_factor = max(1.0, hours_since_creation)
     * - normalized_quality = raw_quality / sqrt(age_factor)
     * - quality_score = min(1.0, normalized_quality / 100.0)
     * 
     * Normalization by content age prevents bias toward older content.
     * 
     * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 8.3
     * 
     * @param contentId the content ID (ship or project)
     * @param type the content type (SHIP or PROJECT)
     * @return score between 0.0 and 1.0
     */
    @Cacheable(value = "contentQualityScore", key = "#contentId")
    public double calculateQualityScore(String contentId, BehavioralSignal.ContentType type) {
        log.debug("Calculating quality score for content: {} (type: {})", contentId, type);
        
        // Query content to get engagement metrics and creation time
        int likes = 0;
        int comments = 0;
        Instant createdAt = Instant.now();
        
        if (type == BehavioralSignal.ContentType.SHIP) {
            Ship ship = shipRepository.findById(contentId)
                    .orElseThrow(() -> new IllegalArgumentException("Ship not found: " + contentId));
            likes = ship.getLikeCount() != null ? ship.getLikeCount() : 0;
            comments = ship.getCommentCount() != null ? ship.getCommentCount() : 0;
            createdAt = ship.getCreatedAt();
        } else if (type == BehavioralSignal.ContentType.PROJECT) {
            Project project = projectRepository.findById(contentId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + contentId));
            // Projects don't have direct like/comment counts, so we'll use 0 for now
            // In a real implementation, we might aggregate from ships or have separate metrics
            likes = 0;
            comments = 0;
            createdAt = project.getCreatedAt();
        }
        
        // Count views and follows from behavioral signals
        long views = behavioralSignalRepository.countByContentIdAndSignalType(
                contentId, BehavioralSignal.SignalType.VIEW);
        long follows = behavioralSignalRepository.countByContentIdAndSignalType(
                contentId, BehavioralSignal.SignalType.FOLLOW);
        
        // Calculate raw quality score with weighted metrics
        // likes * 1.0 + comments * 2.0 + views * 0.5 + follows * 3.0
        double rawQuality = (likes * 1.0) + (comments * 2.0) + (views * 0.5) + (follows * 3.0);
        
        // Calculate age factor (hours since creation)
        long hoursSinceCreation = Duration.between(createdAt, Instant.now()).toHours();
        double ageFactor = Math.max(1.0, hoursSinceCreation);
        
        // Normalize by age using square root to prevent bias toward older content
        double normalizedQuality = rawQuality / Math.sqrt(ageFactor);
        
        // Scale to 0-1 range (divide by 100 as per design)
        double qualityScore = Math.min(1.0, normalizedQuality / 100.0);
        
        log.debug("Quality score for {}: {} (likes={}, comments={}, views={}, follows={}, age={}h)",
                contentId, qualityScore, likes, comments, views, follows, hoursSinceCreation);
        
        return qualityScore;
    }

    /**
     * Calculate relevance score for content against user profile.
     * 
     * Formula:
     * - tag_match_score = sum(profile.techTagScores[tag] for each tag) / number_of_tags
     * - builder_boost = 0.3 if content from followed builder, else 0.0
     * - relevance_score = max(0.1, min(1.0, tag_match_score + builder_boost))
     * 
     * Minimum baseline of 0.1 ensures non-matching content can still appear.
     * 
     * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
     * 
     * @param profile the user interest profile
     * @param contentId the content ID
     * @param type the content type
     * @return score between 0.1 and 1.0
     */
    public double calculateRelevanceScore(UserInterestProfile profile, String contentId, 
                                         BehavioralSignal.ContentType type) {
        log.debug("Calculating relevance score for content: {} against user profile", contentId);
        
        // Query content to get tech tags and builder ID
        List<String> techTags = null;
        String builderId = null;
        
        if (type == BehavioralSignal.ContentType.SHIP) {
            Ship ship = shipRepository.findById(contentId)
                    .orElseThrow(() -> new IllegalArgumentException("Ship not found: " + contentId));
            builderId = ship.getUserId();
            
            // Get tech tags from the associated project
            Project project = projectRepository.findById(ship.getProjectId())
                    .orElse(null);
            if (project != null) {
                techTags = project.getTechTags();
            }
        } else if (type == BehavioralSignal.ContentType.PROJECT) {
            Project project = projectRepository.findById(contentId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + contentId));
            techTags = project.getTechTags();
            builderId = project.getUserId();
        }
        
        // Calculate tag match score
        double tagMatchScore = 0.0;
        if (techTags != null && !techTags.isEmpty() && profile.getTechTagScores() != null) {
            double totalScore = 0.0;
            for (String tag : techTags) {
                totalScore += profile.getTechTagScores().getOrDefault(tag, 0.0);
            }
            tagMatchScore = totalScore / techTags.size();
        }
        
        // Calculate builder boost
        double builderBoost = 0.0;
        if (builderId != null && profile.getFollowedBuilders() != null 
                && profile.getFollowedBuilders().contains(builderId)) {
            builderBoost = 0.3;
        }
        
        // Calculate final relevance score with minimum baseline of 0.1
        double relevanceScore = Math.max(0.1, Math.min(1.0, tagMatchScore + builderBoost));
        
        log.debug("Relevance score for {}: {} (tagMatch={}, builderBoost={}, tags={}, followedBuilder={})",
                contentId, relevanceScore, tagMatchScore, builderBoost, 
                techTags != null ? techTags.size() : 0, builderBoost > 0);
        
        return relevanceScore;
    }

    /**
     * Calculate performance score based on engagement rate.
     * Compares content engagement to median for its age cohort.
     * 
     * Formula:
     * - cohort_median = median(engagement_rates for content in same age_cohort)
     * - cohort_stddev = stddev(engagement_rates for content in same age_cohort)
     * - z_score = (content.engagement_rate - cohort_median) / cohort_stddev
     * - performance_score = clamp(z_score, -1.0, 1.0)
     * 
     * Age cohorts: "0-24h", "1-7d", "7-30d", "30d+"
     * 
     * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
     * 
     * @param contentId the content ID
     * @return score between -1.0 and 1.0
     */
    public double calculatePerformanceScore(String contentId) {
        log.debug("Calculating performance score for content: {}", contentId);

        // Check if we have a cached ContentScore
        ContentScore cachedScore = contentScoreRepository.findByContentId(contentId).orElse(null);

        // If cached and recent (within 1 hour), return cached performance score
        if (cachedScore != null && cachedScore.getPerformanceScore() != null) {
            Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
            if (cachedScore.getUpdatedAt() != null && cachedScore.getUpdatedAt().isAfter(oneHourAgo)) {
                log.debug("Using cached performance score for {}: {}", contentId, cachedScore.getPerformanceScore());
                return cachedScore.getPerformanceScore();
            }
        }

        // Need to calculate performance score
        // First, determine content type and get creation time
        Ship ship = shipRepository.findById(contentId).orElse(null);
        Project project = null;
        Instant createdAt;
        BehavioralSignal.ContentType contentType;

        if (ship != null) {
            createdAt = ship.getCreatedAt();
            contentType = BehavioralSignal.ContentType.SHIP;
        } else {
            project = projectRepository.findById(contentId).orElse(null);
            if (project == null) {
                throw new IllegalArgumentException("Content not found: " + contentId);
            }
            createdAt = project.getCreatedAt();
            contentType = BehavioralSignal.ContentType.PROJECT;
        }

        // Determine age cohort
        String ageCohort = determineAgeCohort(createdAt);

        // Calculate engagement rate for this content
        double engagementRate = calculateEngagementRate(contentId, contentType);

        // Query all content scores in the same age cohort
        List<ContentScore> cohortScores = contentScoreRepository.findByAgeCohort(ageCohort);

        // If cohort is empty or has only this content, return neutral score
        if (cohortScores.isEmpty() || (cohortScores.size() == 1 && cohortScores.get(0).getContentId().equals(contentId))) {
            log.debug("Insufficient cohort data for {}, returning neutral score 0.0", contentId);

            // Cache the result
            ContentScore scoreToSave = cachedScore != null ? cachedScore : ContentScore.builder()
                    .contentId(contentId)
                    .contentType(contentType)
                    .ageCohort(ageCohort)
                    .build();
            scoreToSave.setEngagementRate(engagementRate);
            scoreToSave.setPerformanceScore(0.0);
            scoreToSave.setUpdatedAt(Instant.now());
            contentScoreRepository.save(scoreToSave);

            return 0.0;
        }

        // Extract engagement rates from cohort (excluding null values)
        List<Double> cohortEngagementRates = cohortScores.stream()
                .filter(cs -> cs.getEngagementRate() != null)
                .map(ContentScore::getEngagementRate)
                .sorted()
                .toList();

        if (cohortEngagementRates.isEmpty()) {
            log.debug("No engagement rates in cohort for {}, returning neutral score 0.0", contentId);

            // Cache the result
            ContentScore scoreToSave = cachedScore != null ? cachedScore : ContentScore.builder()
                    .contentId(contentId)
                    .contentType(contentType)
                    .ageCohort(ageCohort)
                    .build();
            scoreToSave.setEngagementRate(engagementRate);
            scoreToSave.setPerformanceScore(0.0);
            scoreToSave.setUpdatedAt(Instant.now());
            contentScoreRepository.save(scoreToSave);

            return 0.0;
        }

        // Calculate cohort median
        double cohortMedian;
        int size = cohortEngagementRates.size();
        if (size % 2 == 0) {
            cohortMedian = (cohortEngagementRates.get(size / 2 - 1) + cohortEngagementRates.get(size / 2)) / 2.0;
        } else {
            cohortMedian = cohortEngagementRates.get(size / 2);
        }

        // Calculate cohort standard deviation
        double mean = cohortEngagementRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = cohortEngagementRates.stream()
                .mapToDouble(rate -> Math.pow(rate - mean, 2))
                .average()
                .orElse(0.0);
        double cohortStddev = Math.sqrt(variance);

        // Calculate z-score
        double zScore;
        if (cohortStddev == 0.0) {
            // If standard deviation is 0, all values are the same
            zScore = 0.0;
        } else {
            zScore = (engagementRate - cohortMedian) / cohortStddev;
        }

        // Clamp to [-1.0, 1.0]
        double performanceScore = Math.max(-1.0, Math.min(1.0, zScore));

        log.debug("Performance score for {}: {} (engagementRate={}, cohortMedian={}, cohortStddev={}, zScore={}, cohort={})",
                contentId, performanceScore, engagementRate, cohortMedian, cohortStddev, zScore, ageCohort);

        // Cache the result in ContentScore collection
        ContentScore scoreToSave = cachedScore != null ? cachedScore : ContentScore.builder()
                .contentId(contentId)
                .contentType(contentType)
                .ageCohort(ageCohort)
                .build();
        scoreToSave.setEngagementRate(engagementRate);
        scoreToSave.setPerformanceScore(performanceScore);
        scoreToSave.setUpdatedAt(Instant.now());
        contentScoreRepository.save(scoreToSave);

        return performanceScore;
    }


    /**
     * Combine quality, relevance, and performance scores.
     * 
     * Formula: 0.3*quality + 0.5*relevance + 0.2*performance
     * 
     * Weights reflect priorities:
     * - Relevance (0.5): Most important - content should match user interests
     * - Quality (0.3): Important - content should be high quality
     * - Performance (0.2): Bonus - reward content that drives engagement
     * 
     * Requirements: 6.1, 6.2
     * 
     * @param quality quality score (0.0 to 1.0)
     * @param relevance relevance score (0.1 to 1.0)
     * @param performance performance score (-1.0 to 1.0)
     * @return combined score
     */
    public double calculateCombinedScore(double quality, double relevance, double performance) {
        log.trace("Combining scores - quality: {}, relevance: {}, performance: {}", 
                 quality, relevance, performance);
        
        // Calculate combined score using fixed weights
        // 0.3 for quality, 0.5 for relevance, 0.2 for performance
        double combined = (0.3 * quality) + (0.5 * relevance) + (0.2 * performance);
        
        log.debug("Combined score: {} (quality={}, relevance={}, performance={})",
                 combined, quality, relevance, performance);
        
        return combined;
    }

    /**
     * Check if content is in cold-start phase (less than 24 hours old).
     * Cold-start content receives a visibility boost to ensure initial exposure.
     * 
     * Requirements: 5.3, 5.4
     * 
     * @param createdAt content creation timestamp
     * @return true if content is less than 24 hours old, false otherwise
     */
    private boolean isColdStartContent(Instant createdAt) {
        long hoursSinceCreation = Duration.between(createdAt, Instant.now()).toHours();
        return hoursSinceCreation < 24;
    }

    /**
     * Generate explanation metadata for a recommendation.
     * 
     * Determines primary reason code based on score components:
     * - FOLLOWED_BUILDER: if builder in followedBuilders and relevance > 0.7
     * - TECH_MATCH: if tag_match_score > 0.5
     * - HIGH_QUALITY: if quality_score > 0.7
     * - TRENDING: if from trending feed
     * - COLD_START: if user is cold-start
     * 
     * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5
     * 
     * @param contentId the content ID
     * @param profile the user interest profile
     * @param qualityScore the quality score
     * @param relevanceScore the relevance score
     * @param performanceScore the performance score
     * @return RecommendationExplanation with reason code and details
     */
    public RecommendationExplanation explainRecommendation(String contentId,
                                                          UserInterestProfile profile,
                                                          double qualityScore,
                                                          double relevanceScore,
                                                          double performanceScore) {
        log.debug("Generating explanation for content: {}", contentId);
        
        // TODO: Implementation in subsequent tasks
        throw new UnsupportedOperationException("explainRecommendation not yet implemented");
    }

    /**
     * Determine age cohort for content.
     * 
     * Cohorts:
     * - "0-24h": New content (cold-start, receives visibility boost)
     * - "1-7d": Recent content (active engagement period)
     * - "7-30d": Established content (stable engagement)
     * - "30d+": Older content (declining engagement)
     * 
     * Requirements: 4.1, 4.2
     * 
     * @param createdAt content creation timestamp
     * @return age cohort string
     */
    private String determineAgeCohort(Instant createdAt) {
        long hoursSinceCreation = Duration.between(createdAt, Instant.now()).toHours();
        
        if (hoursSinceCreation < 24) {
            return "0-24h";
        } else if (hoursSinceCreation < 24 * 7) {
            return "1-7d";
        } else if (hoursSinceCreation < 24 * 30) {
            return "7-30d";
        } else {
            return "30d+";
        }
    }

    /**
     * Calculate engagement rate for content.
     * 
     * Formula: (likes + comments + views + follows) / max(1, views)
     * 
     * This ensures we don't divide by zero and provides a meaningful
     * engagement metric even for content with no views.
     * 
     * @param contentId the content ID
     * @param contentType the content type (SHIP or PROJECT)
     * @return engagement rate
     */
    private double calculateEngagementRate(String contentId, BehavioralSignal.ContentType contentType) {
        int likes = 0;
        int comments = 0;
        
        if (contentType == BehavioralSignal.ContentType.SHIP) {
            Ship ship = shipRepository.findById(contentId).orElse(null);
            if (ship != null) {
                likes = ship.getLikeCount() != null ? ship.getLikeCount() : 0;
                comments = ship.getCommentCount() != null ? ship.getCommentCount() : 0;
            }
        }
        // Projects don't have direct like/comment counts in the current schema
        
        // Count views and follows from behavioral signals
        long views = behavioralSignalRepository.countByContentIdAndSignalType(
                contentId, BehavioralSignal.SignalType.VIEW);
        long follows = behavioralSignalRepository.countByContentIdAndSignalType(
                contentId, BehavioralSignal.SignalType.FOLLOW);
        
        // Calculate engagement rate: (likes + comments + views + follows) / max(1, views)
        double totalEngagement = likes + comments + views + follows;
        double engagementRate = totalEngagement / Math.max(1.0, views);
        
        return engagementRate;
    }

    /**
     * Query candidate content excluding recently viewed.
     * 
     * Retrieves ships and projects that the user has not viewed in the past 7 days.
     * This ensures feed freshness and prevents showing the same content repeatedly.
     * 
     * Algorithm:
     * 1. Query user's VIEW signals from past 7 days
     * 2. Extract contentIds from VIEW signals
     * 3. Query all ships excluding viewed contentIds
     * 4. Query all projects excluding viewed contentIds
     * 5. Combine and return as list of ships (projects converted to ship format)
     * 
     * Performance considerations:
     * - Uses indexed query on behavioral_signals.{userId, timestamp}
     * - Uses indexed query on ships._id (implicit)
     * - Limits result set to reasonable size for scoring
     * 
     * Requirements: 6.5, 8.4
     * 
     * @param userId the user ID
     * @return List of candidate ships (excluding recently viewed)
     */
    private List<Ship> getCandidateContent(String userId) {
        log.debug("Querying candidate content for user: {}", userId);

        // Calculate timestamp for 7 days ago
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));

        // Query user's VIEW signals from past 7 days
        // Uses compound index: {userId: 1, timestamp: -1}
        List<BehavioralSignal> recentViewSignals = behavioralSignalRepository
            .findByUserIdAndTimestampAfter(userId, sevenDaysAgo);

        // Extract contentIds from VIEW signals
        // We only care about VIEW signals for exclusion
        java.util.Set<String> viewedContentIds = new HashSet<>();
        for (BehavioralSignal signal : recentViewSignals) {
            if (signal.getSignalType() == BehavioralSignal.SignalType.VIEW) {
                viewedContentIds.add(signal.getContentId());
            }
        }

        log.debug("Found {} viewed content items in past 7 days for user: {}", 
                  viewedContentIds.size(), userId);

        // Query all ships excluding viewed contentIds
        // Note: MongoDB doesn't have a native "NOT IN" for large sets, so we fetch all
        // and filter in memory for MVP. For production, consider pagination or custom query.
        List<Ship> allShips = shipRepository.findAll();
        List<Ship> candidateShips = new java.util.ArrayList<>();

        for (Ship ship : allShips) {
            if (!viewedContentIds.contains(ship.getId())) {
                candidateShips.add(ship);
            }
        }

        log.debug("Found {} candidate ships for user: {}", candidateShips.size(), userId);

        // TODO: Phase 2 - Include projects in candidate content
        // For MVP, we focus on ships only as they are the primary content type
        // Projects can be added in a future iteration with proper conversion to FeedItemResponse

        return candidateShips;
    }


    /**
     * Create hybrid feed by interleaving personalized and trending.
     * 
     * Takes top 50% from personalized ranking and top 50% from trending,
     * interleaves them, and deduplicates by contentId.
     * 
     * Requirements: 6.3, 6.6
     * 
     * @param personalizedRanked personalized content sorted by combined score
     * @param trendingRanked trending content sorted by trending score
     * @param targetSize target feed size (minimum 20)
     * @return hybrid feed list
     */
    private List<Ship> createHybridFeed(List<Ship> personalizedRanked,
                                       List<Ship> trendingRanked,
                                       int targetSize) {
        log.debug("Creating hybrid feed with target size: {}", targetSize);
        
        // Calculate how many items to take from each list (50/50 split)
        int halfTarget = targetSize / 2;
        
        // Take top 50% from personalized list
        List<Ship> personalizedTop = personalizedRanked.stream()
                .limit(halfTarget)
                .collect(java.util.stream.Collectors.toList());
        
        // Take top 50% from trending list
        List<Ship> trendingTop = trendingRanked.stream()
                .limit(halfTarget)
                .collect(java.util.stream.Collectors.toList());
        
        log.debug("Personalized top: {} items, Trending top: {} items",
                personalizedTop.size(), trendingTop.size());
        
        // Interleave the two lists (alternating items)
        List<Ship> hybridFeed = new java.util.ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        
        int maxLength = Math.max(personalizedTop.size(), trendingTop.size());
        
        for (int i = 0; i < maxLength; i++) {
            // Add from personalized list
            if (i < personalizedTop.size()) {
                Ship personalizedShip = personalizedTop.get(i);
                if (!seenIds.contains(personalizedShip.getId())) {
                    hybridFeed.add(personalizedShip);
                    seenIds.add(personalizedShip.getId());
                }
            }
            
            // Add from trending list
            if (i < trendingTop.size()) {
                Ship trendingShip = trendingTop.get(i);
                if (!seenIds.contains(trendingShip.getId())) {
                    hybridFeed.add(trendingShip);
                    seenIds.add(trendingShip.getId());
                }
            }
        }
        
        log.debug("Created hybrid feed with {} items (after deduplication)", hybridFeed.size());
        
        return hybridFeed;
    }
}
