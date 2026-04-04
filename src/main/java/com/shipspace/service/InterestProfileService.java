package com.shipspace.service;

import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.UserInterestProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Manages user interest profiles — creation, retrieval, and cache invalidation.
 *
 * Phase 3 refactor: extracted from PersonalizationService which was doing
 * profile management, content scoring, AND feed building in one 400-line class.
 * Single responsibility: own the UserInterestProfile lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestProfileService {

    private final UserInterestProfileRepository userInterestProfileRepository;

    /**
     * Get user interest profile with 5-minute cache.
     * Returns an empty (cold-start) profile for new users — never null.
     */
    @Cacheable(value = "userInterestProfile", key = "#userId")
    public UserInterestProfile getProfile(String userId) {
        log.debug("Loading interest profile for user: {}", userId);
        return userInterestProfileRepository.findByUserId(userId)
                .orElseGet(() -> UserInterestProfile.builder()
                        .userId(userId)
                        .techTagScores(new HashMap<>())
                        .followedBuilders(new HashSet<>())
                        .totalSignals(0)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
    }

    /**
     * Evict the cached profile for a user — call after behavioral signal processing.
     */
    @CacheEvict(value = "userInterestProfile", key = "#userId")
    public void evictProfile(String userId) {
        log.debug("Evicted interest profile cache for user: {}", userId);
    }

    /**
     * Get or create a persisted profile. Used by BehavioralTrackingService.
     */
    public UserInterestProfile getOrCreatePersistedProfile(String userId) {
        return userInterestProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
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
}
