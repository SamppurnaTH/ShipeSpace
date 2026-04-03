package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.BehavioralSignal.ContentType;
import com.shipspace.model.BehavioralSignal.SignalType;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for recording and processing user behavioral signals.
 * 
 * This service tracks user actions (likes, comments, views, follows, skips)
 * and asynchronously updates user interest profiles based on these signals.
 * All tracking methods are asynchronous to avoid blocking user actions.
 * 
 * Key responsibilities:
 * - Record behavioral signals to the database
 * - Extract tech tags from content
 * - Update user interest profiles with weighted scores
 * - Track followed builders
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BehavioralTrackingService {

    private final BehavioralSignalRepository behavioralSignalRepository;
    private final UserInterestProfileRepository userInterestProfileRepository;
    private final ShipRepository shipRepository;
    private final ProjectRepository projectRepository;

    /**
     * Track a like action on a ship.
     * Records signal and updates user interest profile asynchronously.
     * 
     * Signal weight: 1.0
     * 
     * @param userId the ID of the user who liked the ship
     * @param shipId the ID of the ship that was liked
     * 
     * Requirements: 7.1
     */
    @Async("taskExecutor")
    public void trackLike(String userId, String shipId) {
        log.debug("Tracking LIKE signal for user {} on ship {}", userId, shipId);

        try {
            // Fetch ship to get tech tags and builder ID
            Optional<Ship> shipOpt = shipRepository.findById(shipId);
            if (shipOpt.isEmpty()) {
                log.warn("Ship {} not found, cannot track LIKE signal", shipId);
                return;
            }

            Ship ship = shipOpt.get();
            List<String> techTags = extractTechTags(shipId, ContentType.SHIP);

            // Create and persist the signal
            BehavioralSignal signal = BehavioralSignal.builder()
                    .userId(userId)
                    .signalType(SignalType.LIKE)
                    .contentId(shipId)
                    .contentType(ContentType.SHIP)
                    .techTags(techTags)
                    .builderId(ship.getUserId())
                    .timestamp(Instant.now())
                    .build();

            behavioralSignalRepository.save(signal);
            log.debug("LIKE signal persisted for user {} on ship {}", userId, shipId);

            // Process signal to update user interest profile
            processSignal(signal);

        } catch (Exception e) {
            log.error("Error tracking LIKE signal for user {} on ship {}", userId, shipId, e);
        }
    }


    /**
     * Track a comment action on a ship.
     * Records signal and updates user interest profile asynchronously.
     * 
     * Signal weight: 2.0 (stronger signal than like)
     * 
     * @param userId the ID of the user who commented
     * @param shipId the ID of the ship that was commented on
     * 
     * Requirements: 7.2
     */
    @Async("taskExecutor")
    public void trackComment(String userId, String shipId) {
        log.debug("Tracking COMMENT signal for user {} on ship {}", userId, shipId);
        
        try {
            // Fetch ship to get tech tags and builder ID
            Optional<Ship> shipOpt = shipRepository.findById(shipId);
            if (shipOpt.isEmpty()) {
                log.warn("Ship {} not found, cannot track COMMENT signal", shipId);
                return;
            }
            
            Ship ship = shipOpt.get();
            List<String> techTags = extractTechTags(shipId, ContentType.SHIP);
            
            // Create and persist the signal
            BehavioralSignal signal = BehavioralSignal.builder()
                    .userId(userId)
                    .signalType(SignalType.COMMENT)
                    .contentId(shipId)
                    .contentType(ContentType.SHIP)
                    .techTags(techTags)
                    .builderId(ship.getUserId())
                    .timestamp(Instant.now())
                    .build();
            
            behavioralSignalRepository.save(signal);
            log.debug("COMMENT signal persisted for user {} on ship {}", userId, shipId);
            
            // Process signal to update user interest profile
            processSignal(signal);
            
        } catch (Exception e) {
            log.error("Error tracking COMMENT signal for user {} on ship {}", userId, shipId, e);
        }
    }

    /**
     * Track a view action on content (ship or project).
     * Only records views that exceed 10 seconds duration.
     * Records signal and updates user interest profile asynchronously.
     * 
     * Signal weight: 0.5 (passive engagement)
     * 
     * @param userId the ID of the user who viewed the content
     * @param contentId the ID of the content (ship or project)
     * @param contentType the type of content (SHIP or PROJECT)
     * @param durationSeconds the duration of the view in seconds
     * 
     * Requirements: 7.3
     */
    @Async("taskExecutor")
    public void trackView(String userId, String contentId, ContentType contentType, int durationSeconds) {
        log.debug("Tracking VIEW signal for user {} on {} {} (duration: {}s)", 
                  userId, contentType, contentId, durationSeconds);
        
        // Only track views longer than 10 seconds
        if (durationSeconds <= 10) {
            log.debug("View duration {}s is too short, not tracking", durationSeconds);
            return;
        }
        
        try {
            // Fetch content to get tech tags and builder ID
            String builderId = null;
            if (contentType == ContentType.SHIP) {
                Optional<Ship> shipOpt = shipRepository.findById(contentId);
                if (shipOpt.isEmpty()) {
                    log.warn("Ship {} not found, cannot track VIEW signal", contentId);
                    return;
                }
                builderId = shipOpt.get().getUserId();
            } else if (contentType == ContentType.PROJECT) {
                Optional<Project> projectOpt = projectRepository.findById(contentId);
                if (projectOpt.isEmpty()) {
                    log.warn("Project {} not found, cannot track VIEW signal", contentId);
                    return;
                }
                builderId = projectOpt.get().getUserId();
            }
            
            List<String> techTags = extractTechTags(contentId, contentType);
            
            // Create and persist the signal
            BehavioralSignal signal = BehavioralSignal.builder()
                    .userId(userId)
                    .signalType(SignalType.VIEW)
                    .contentId(contentId)
                    .contentType(contentType)
                    .techTags(techTags)
                    .builderId(builderId)
                    .durationSeconds(durationSeconds)
                    .timestamp(Instant.now())
                    .build();
            
            behavioralSignalRepository.save(signal);
            log.debug("VIEW signal persisted for user {} on {} {} (duration: {}s)", 
                      userId, contentType, contentId, durationSeconds);
            
            // Process signal to update user interest profile
            processSignal(signal);
            
        } catch (Exception e) {
            log.error("Error tracking VIEW signal for user {} on {} {}", 
                      userId, contentType, contentId, e);
        }
    }

    /**
     * Track a follow action on a builder.
     * Records signal and updates user interest profile asynchronously.
     * 
     * Signal weight: 3.0 (strongest signal of interest)
     * 
     * @param userId the ID of the user who followed the builder
     * @param builderId the ID of the builder who was followed
     * @param contentId the ID of the content that led to the follow (optional context)
     * 
     * Requirements: 7.4
     */
    @Async("taskExecutor")
    public void trackFollow(String userId, String builderId, String contentId) {
        log.debug("Tracking FOLLOW signal for user {} following builder {} (context: {})", 
                  userId, builderId, contentId);
        
        try {
            // Extract tech tags from the content that led to the follow (if provided)
            List<String> techTags = new ArrayList<>();
            ContentType contentType = null;
            
            if (contentId != null && !contentId.isEmpty()) {
                // Try to find as ship first
                Optional<Ship> shipOpt = shipRepository.findById(contentId);
                if (shipOpt.isPresent()) {
                    contentType = ContentType.SHIP;
                    techTags = extractTechTags(contentId, ContentType.SHIP);
                } else {
                    // Try as project
                    Optional<Project> projectOpt = projectRepository.findById(contentId);
                    if (projectOpt.isPresent()) {
                        contentType = ContentType.PROJECT;
                        techTags = extractTechTags(contentId, ContentType.PROJECT);
                    }
                }
            }
            
            // Create and persist the signal
            BehavioralSignal signal = BehavioralSignal.builder()
                    .userId(userId)
                    .signalType(SignalType.FOLLOW)
                    .contentId(contentId)
                    .contentType(contentType)
                    .techTags(techTags)
                    .builderId(builderId)
                    .timestamp(Instant.now())
                    .build();
            
            behavioralSignalRepository.save(signal);
            log.debug("FOLLOW signal persisted for user {} following builder {}", userId, builderId);
            
            // Process signal to update user interest profile
            processSignal(signal);
            
        } catch (Exception e) {
            log.error("Error tracking FOLLOW signal for user {} following builder {}", 
                      userId, builderId, e);
        }
    }

    /**
     * Track a skip action (user scrolled past content without interaction).
     * Records signal for future ML training (Phase 2).
     * 
     * Signal weight: -0.1 (negative signal)
     * 
     * @param userId the ID of the user who skipped the content
     * @param contentId the ID of the content that was skipped
     * 
     * Requirements: 7.5
     */
    @Async("taskExecutor")
    public void trackSkip(String userId, String contentId) {
        log.debug("Tracking SKIP signal for user {} on content {}", userId, contentId);
        
        try {
            // Try to determine content type and extract tech tags
            List<String> techTags = new ArrayList<>();
            ContentType contentType = null;
            String builderId = null;
            
            // Try to find as ship first
            Optional<Ship> shipOpt = shipRepository.findById(contentId);
            if (shipOpt.isPresent()) {
                contentType = ContentType.SHIP;
                builderId = shipOpt.get().getUserId();
                techTags = extractTechTags(contentId, ContentType.SHIP);
            } else {
                // Try as project
                Optional<Project> projectOpt = projectRepository.findById(contentId);
                if (projectOpt.isPresent()) {
                    contentType = ContentType.PROJECT;
                    builderId = projectOpt.get().getUserId();
                    techTags = extractTechTags(contentId, ContentType.PROJECT);
                } else {
                    log.warn("Content {} not found, cannot track SKIP signal", contentId);
                    return;
                }
            }
            
            // Create and persist the signal
            BehavioralSignal signal = BehavioralSignal.builder()
                    .userId(userId)
                    .signalType(SignalType.SKIP)
                    .contentId(contentId)
                    .contentType(contentType)
                    .techTags(techTags)
                    .builderId(builderId)
                    .timestamp(Instant.now())
                    .build();
            
            behavioralSignalRepository.save(signal);
            log.debug("SKIP signal persisted for user {} on content {}", userId, contentId);
            
            // Process signal to update user interest profile
            processSignal(signal);
            
        } catch (Exception e) {
            log.error("Error tracking SKIP signal for user {} on content {}", userId, contentId, e);
        }
    }

    /**
     * Process a behavioral signal and update user interest profile.
     * Extracts tech tags from content and increments scores based on signal weight.
     * 
     * This method:
     * 1. Persists the signal to the database
     * 2. Extracts tech tags from the content
     * 3. Gets or creates the user's interest profile
     * 4. Increments tech tag scores with appropriate weights
     * 5. Updates followed builders for FOLLOW signals
     * 6. Updates profile metadata (totalSignals, timestamps)
     * 7. Persists the updated profile
     * 
     * @param signal the behavioral signal to process
     * 
     * Requirements: 1.1, 1.2, 1.3, 1.4, 1.6
     */
    private void processSignal(BehavioralSignal signal) {
        log.debug("Processing signal: type={}, userId={}, contentId={}", 
                  signal.getSignalType(), signal.getUserId(), signal.getContentId());
        
        try {
            // Get or create user interest profile
            UserInterestProfile profile = getOrCreateProfile(signal.getUserId());
            
            // Calculate signal weight based on signal type
            double weight = getSignalWeight(signal.getSignalType());
            
            // Increment tech tag scores for all tags associated with the content
            // Use a Set to deduplicate tags - each unique tag should only be counted once per signal
            List<String> techTags = signal.getTechTags();
            if (techTags != null && !techTags.isEmpty()) {
                // Deduplicate tags to ensure each unique tag is only incremented once
                java.util.Set<String> uniqueTags = new java.util.HashSet<>(techTags);
                for (String tag : uniqueTags) {
                    profile.incrementTagScore(tag, weight);
                    log.debug("Incremented score for tag '{}' by {} for user {}", 
                              tag, weight, signal.getUserId());
                }
            }
            
            // Update followed builders set for FOLLOW signals
            if (signal.getSignalType() == SignalType.FOLLOW && signal.getBuilderId() != null) {
                profile.getFollowedBuilders().add(signal.getBuilderId());
                log.debug("Added builder {} to followed builders for user {}", 
                          signal.getBuilderId(), signal.getUserId());
            }
            
            // Increment total signals counter
            profile.setTotalSignals(profile.getTotalSignals() + 1);
            
            // Update timestamps
            profile.setLastSignalAt(signal.getTimestamp());
            profile.setUpdatedAt(Instant.now());
            
            // Persist updated profile
            userInterestProfileRepository.save(profile);
            log.debug("Updated interest profile for user {}: totalSignals={}, tagCount={}, followedBuilders={}", 
                      signal.getUserId(), profile.getTotalSignals(), 
                      profile.getTechTagScores().size(), profile.getFollowedBuilders().size());
            
        } catch (Exception e) {
            log.error("Error processing signal for user {}", signal.getUserId(), e);
        }
    }

    /**
     * Get or create a user interest profile.
     * If the profile doesn't exist, creates a new one with default values.
     * 
     * @param userId the ID of the user
     * @return the user's interest profile (existing or newly created)
     * 
     * Requirements: 1.6
     */
    private UserInterestProfile getOrCreateProfile(String userId) {
        log.debug("Getting or creating profile for user {}", userId);
        
        return userInterestProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.debug("Creating new interest profile for user {}", userId);
                    UserInterestProfile newProfile = UserInterestProfile.builder()
                            .userId(userId)
                            .techTagScores(new HashMap<>())
                            .followedBuilders(new HashSet<>())
                            .totalSignals(0)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    return userInterestProfileRepository.save(newProfile);
                });
    }

    /**
     * Calculate the weight for a signal type.
     * Different signal types have different weights based on their strength as indicators of interest:
     * - LIKE: 1.0 (basic positive signal)
     * - COMMENT: 2.0 (strong engagement signal)
     * - VIEW: 0.5 (passive engagement)
     * - FOLLOW: 3.0 (strongest signal of interest)
     * - SKIP: -0.1 (negative signal for future ML)
     * 
     * @param signalType the type of signal
     * @return the weight for the signal type
     */
    private double getSignalWeight(SignalType signalType) {
        return switch (signalType) {
            case LIKE -> 1.0;
            case COMMENT -> 2.0;
            case VIEW -> 0.5;
            case FOLLOW -> 3.0;
            case SKIP -> -0.1;
        };
    }

    /**
     * Extract tech tags from content (ship or project).
     * For ships, retrieves the associated project and extracts its tech tags.
     * For projects, directly extracts tech tags.
     * 
     * @param contentId the ID of the content
     * @param contentType the type of content (SHIP or PROJECT)
     * @return list of tech tags associated with the content
     */
    private List<String> extractTechTags(String contentId, ContentType contentType) {
        log.debug("Extracting tech tags for {} {}", contentType, contentId);
        
        try {
            if (contentType == ContentType.SHIP) {
                // For ships, get the associated project and extract its tech tags
                Optional<Ship> shipOpt = shipRepository.findById(contentId);
                if (shipOpt.isEmpty()) {
                    log.warn("Ship {} not found, cannot extract tech tags", contentId);
                    return new ArrayList<>();
                }
                
                Ship ship = shipOpt.get();
                String projectId = ship.getProjectId();
                
                Optional<Project> projectOpt = projectRepository.findById(projectId);
                if (projectOpt.isEmpty()) {
                    log.warn("Project {} not found for ship {}, cannot extract tech tags", 
                             projectId, contentId);
                    return new ArrayList<>();
                }
                
                Project project = projectOpt.get();
                List<String> techTags = project.getTechTags();
                log.debug("Extracted {} tech tags from project {} for ship {}", 
                          techTags.size(), projectId, contentId);
                return techTags != null ? techTags : new ArrayList<>();
                
            } else if (contentType == ContentType.PROJECT) {
                // For projects, directly extract tech tags
                Optional<Project> projectOpt = projectRepository.findById(contentId);
                if (projectOpt.isEmpty()) {
                    log.warn("Project {} not found, cannot extract tech tags", contentId);
                    return new ArrayList<>();
                }
                
                Project project = projectOpt.get();
                List<String> techTags = project.getTechTags();
                log.debug("Extracted {} tech tags from project {}", 
                          techTags.size(), contentId);
                return techTags != null ? techTags : new ArrayList<>();
            }
            
            log.warn("Unknown content type: {}", contentType);
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Error extracting tech tags for {} {}", contentType, contentId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Scheduled job to apply time decay to all user interest profiles.
     * Processes in batches of 200 to avoid loading the entire collection into heap.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void applyTimeDecayToAllProfiles() {
        log.info("Starting scheduled time decay job for all user interest profiles");

        int page = 0;
        final int batchSize = 200;
        int updatedCount = 0;
        int skippedCount = 0;

        org.springframework.data.domain.Page<UserInterestProfile> batch;
        do {
            batch = userInterestProfileRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(page, batchSize));

            for (UserInterestProfile profile : batch.getContent()) {
                if (profile.getLastSignalAt() == null || profile.getTotalSignals() == 0) {
                    skippedCount++;
                    continue;
                }
                try {
                    profile.applyTimeDecay(30);
                    profile.setUpdatedAt(Instant.now());
                    userInterestProfileRepository.save(profile);
                    updatedCount++;
                } catch (Exception e) {
                    log.error("Error applying time decay to profile for user {}", profile.getUserId(), e);
                }
            }
            page++;
        } while (batch.hasNext());

        log.info("Time decay job completed: {} profiles updated, {} profiles skipped",
                updatedCount, skippedCount);
    }

    /**
     * Convenience overload for feed view tracking.
     * Uses SHIP content type and a default duration of 30 seconds (above the 10s threshold).
     */
    @Async("taskExecutor")
    public void trackView(String userId, String shipId) {
        trackView(userId, shipId, ContentType.SHIP, 30);
    }
}
