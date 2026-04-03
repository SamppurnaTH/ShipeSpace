package com.shipspace.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * UserInterestProfile document model representing a user's learned interests
 * based on behavioral signals (likes, comments, views, follows).
 * 
 * This model tracks:
 * - Tech tag interest scores derived from user interactions
 * - Followed builders
 * - Total behavioral signals processed
 * - Timestamps for tracking and decay calculations
 */
@Document(collection = "user_interest_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInterestProfile {

    @Id
    private String id;

    /**
     * User ID this profile belongs to.
     * Unique identifier linking to the User document.
     */
    @Indexed(unique = true)
    private String userId;

    /**
     * Map of tech tag to interest score (0.0 to 1.0).
     * Higher scores indicate stronger interest based on user behavior.
     * Scores decay over time using exponential decay with 30-day half-life.
     */
    @Builder.Default
    private Map<String, Double> techTagScores = new HashMap<>();

    /**
     * Set of builder IDs (user IDs) that this user follows.
     * Updated when user follows a builder after viewing their content.
     */
    @Builder.Default
    private Set<String> followedBuilders = new HashSet<>();

    /**
     * Total number of behavioral signals recorded for this user.
     * Used to determine if user is in cold-start phase (<10 signals).
     */
    @Builder.Default
    private Integer totalSignals = 0;

    /**
     * Timestamp of the last behavioral signal processed.
     * Used for time decay calculations.
     */
    private Instant lastSignalAt;

    /**
     * Timestamp when the profile was last updated.
     * Indexed for cache invalidation queries.
     */
    @Indexed
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Timestamp when the profile was created.
     */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Increment the interest score for a specific tech tag.
     * Scores accumulate without a hard cap; time decay (30-day half-life)
     * prevents unbounded growth and keeps recent signals more influential.
     */
    public void incrementTagScore(String tag, double weight) {
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }
        double currentScore = techTagScores.getOrDefault(tag, 0.0);
        techTagScores.put(tag, currentScore + weight);
    }

    /**
     * Apply exponential time decay to all tech tag scores.
     * Uses the formula: decayed_score = original_score * (0.5 ^ (days_elapsed / half_life_days))
     * 
     * This ensures recent interactions have more weight than older ones.
     * Scores below 0.01 are removed to keep the map clean.
     * 
     * @param halfLifeDays the half-life in days (typically 30 for this system)
     */
    public void applyTimeDecay(int halfLifeDays) {
        if (lastSignalAt == null) {
            return;
        }

        long daysElapsed = ChronoUnit.DAYS.between(lastSignalAt, Instant.now());
        if (daysElapsed <= 0) {
            return; // No decay needed if less than a day has passed
        }

        double decayFactor = Math.pow(0.5, (double) daysElapsed / halfLifeDays);
        
        // Apply decay to all scores and remove very low scores
        Map<String, Double> decayedScores = new HashMap<>();
        for (Map.Entry<String, Double> entry : techTagScores.entrySet()) {
            double decayedScore = entry.getValue() * decayFactor;
            if (decayedScore >= 0.01) { // Keep only meaningful scores
                decayedScores.put(entry.getKey(), decayedScore);
            }
        }
        
        this.techTagScores = decayedScores;
    }

    /**
     * Determine if this user is in cold-start phase.
     * A user is considered cold-start if they have fewer than 10 behavioral signals.
     * Cold-start users receive trending content instead of personalized recommendations.
     * 
     * @return true if user has fewer than 10 signals, false otherwise
     */
    public boolean isColdStart() {
        return totalSignals < 10;
    }
}
