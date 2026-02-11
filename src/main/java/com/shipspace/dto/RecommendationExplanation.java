package com.shipspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO providing transparency for why content was recommended.
 * Includes reason codes, matching factors, and score breakdowns.
 * 
 * Validates Requirements: 10.1, 10.2, 10.3, 10.4, 10.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationExplanation {

    /**
     * Primary reason code indicating the main matching factor.
     */
    private ReasonCode reasonCode;

    /**
     * Matching tech tags (populated when reasonCode is TECH_MATCH).
     */
    private List<String> matchingTags;

    /**
     * Builder name (populated when reasonCode is FOLLOWED_BUILDER).
     */
    private String builderName;

    /**
     * Score components for transparency.
     */
    private ScoreBreakdown scoreBreakdown;

    /**
     * Enum representing the primary reason for recommendation.
     */
    public enum ReasonCode {
        /**
         * Content matches user's tech interests.
         */
        TECH_MATCH,

        /**
         * Content is from a followed builder.
         */
        FOLLOWED_BUILDER,

        /**
         * Content is trending.
         */
        TRENDING,

        /**
         * Content has high quality score.
         */
        HIGH_QUALITY,

        /**
         * User is new, showing trending content.
         */
        COLD_START
    }

    /**
     * Nested class containing score components for transparency.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBreakdown {
        /**
         * Content quality score (0.0 to 1.0).
         */
        private double qualityScore;

        /**
         * Relevance score based on user interests (0.0 to 1.0).
         */
        private double relevanceScore;

        /**
         * Performance score based on engagement (-1.0 to 1.0).
         */
        private double performanceScore;

        /**
         * Combined score using weighted formula (0.0 to 1.0).
         * Formula: 0.3 * quality + 0.5 * relevance + 0.2 * performance
         */
        private double combinedScore;
    }
}
