package com.shipspace.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RecommendationExplanation DTO.
 * Validates the structure and behavior of recommendation explanation data.
 * 
 * Tests Requirements 10.1, 10.2, 10.3, 10.4, 10.5:
 * - Reason codes for recommendation transparency
 * - Matching tech tags for TECH_MATCH reason
 * - Builder name for FOLLOWED_BUILDER reason
 * - Score breakdown components
 */
@DisplayName("RecommendationExplanation Tests")
class RecommendationExplanationTest {

    @Test
    @DisplayName("Should create explanation with TECH_MATCH reason code")
    void createExplanation_WithTechMatch_Success() {
        // Arrange
        List<String> matchingTags = Arrays.asList("React", "TypeScript", "AI");
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(0.75)
                .relevanceScore(0.85)
                .performanceScore(0.20)
                .combinedScore(0.72)
                .build();

        // Act
        RecommendationExplanation explanation = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.TECH_MATCH)
                .matchingTags(matchingTags)
                .scoreBreakdown(scoreBreakdown)
                .build();

        // Assert
        assertThat(explanation.getReasonCode())
            .isEqualTo(RecommendationExplanation.ReasonCode.TECH_MATCH);
        assertThat(explanation.getMatchingTags()).containsExactly("React", "TypeScript", "AI");
        assertThat(explanation.getBuilderName()).isNull();
        assertThat(explanation.getScoreBreakdown()).isNotNull();
        assertThat(explanation.getScoreBreakdown().getQualityScore()).isEqualTo(0.75);
        assertThat(explanation.getScoreBreakdown().getRelevanceScore()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("Should create explanation with FOLLOWED_BUILDER reason code")
    void createExplanation_WithFollowedBuilder_Success() {
        // Arrange
        String builderName = "john_doe";
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(0.65)
                .relevanceScore(0.90)
                .performanceScore(0.15)
                .combinedScore(0.74)
                .build();

        // Act
        RecommendationExplanation explanation = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.FOLLOWED_BUILDER)
                .builderName(builderName)
                .scoreBreakdown(scoreBreakdown)
                .build();

        // Assert
        assertThat(explanation.getReasonCode())
            .isEqualTo(RecommendationExplanation.ReasonCode.FOLLOWED_BUILDER);
        assertThat(explanation.getBuilderName()).isEqualTo("john_doe");
        assertThat(explanation.getMatchingTags()).isNull();
        assertThat(explanation.getScoreBreakdown().getRelevanceScore()).isEqualTo(0.90);
    }

    @Test
    @DisplayName("Should create explanation with TRENDING reason code")
    void createExplanation_WithTrending_Success() {
        // Arrange
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(0.85)
                .relevanceScore(0.50)
                .performanceScore(0.80)
                .combinedScore(0.71)
                .build();

        // Act
        RecommendationExplanation explanation = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.TRENDING)
                .scoreBreakdown(scoreBreakdown)
                .build();

        // Assert
        assertThat(explanation.getReasonCode())
            .isEqualTo(RecommendationExplanation.ReasonCode.TRENDING);
        assertThat(explanation.getMatchingTags()).isNull();
        assertThat(explanation.getBuilderName()).isNull();
        assertThat(explanation.getScoreBreakdown().getPerformanceScore()).isEqualTo(0.80);
    }

    @Test
    @DisplayName("Should create explanation with HIGH_QUALITY reason code")
    void createExplanation_WithHighQuality_Success() {
        // Arrange
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(0.95)
                .relevanceScore(0.60)
                .performanceScore(0.30)
                .combinedScore(0.73)
                .build();

        // Act
        RecommendationExplanation explanation = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.HIGH_QUALITY)
                .scoreBreakdown(scoreBreakdown)
                .build();

        // Assert
        assertThat(explanation.getReasonCode())
            .isEqualTo(RecommendationExplanation.ReasonCode.HIGH_QUALITY);
        assertThat(explanation.getScoreBreakdown().getQualityScore()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Should create explanation with COLD_START reason code")
    void createExplanation_WithColdStart_Success() {
        // Arrange
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(0.70)
                .relevanceScore(0.10)
                .performanceScore(0.50)
                .combinedScore(0.41)
                .build();

        // Act
        RecommendationExplanation explanation = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.COLD_START)
                .scoreBreakdown(scoreBreakdown)
                .build();

        // Assert
        assertThat(explanation.getReasonCode())
            .isEqualTo(RecommendationExplanation.ReasonCode.COLD_START);
        assertThat(explanation.getScoreBreakdown().getRelevanceScore()).isEqualTo(0.10);
    }

    @Test
    @DisplayName("Should create ScoreBreakdown with all components")
    void createScoreBreakdown_WithAllComponents_Success() {
        // Act
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(0.75)
                .relevanceScore(0.85)
                .performanceScore(0.20)
                .combinedScore(0.72)
                .build();

        // Assert
        assertThat(scoreBreakdown.getQualityScore()).isEqualTo(0.75);
        assertThat(scoreBreakdown.getRelevanceScore()).isEqualTo(0.85);
        assertThat(scoreBreakdown.getPerformanceScore()).isEqualTo(0.20);
        assertThat(scoreBreakdown.getCombinedScore()).isEqualTo(0.72);
    }

    @Test
    @DisplayName("Should verify combined score formula (0.3*Q + 0.5*R + 0.2*P)")
    void scoreBreakdown_VerifiesCombinedScoreFormula() {
        // Arrange
        double quality = 0.8;
        double relevance = 0.6;
        double performance = 0.4;
        double expectedCombined = (0.3 * quality) + (0.5 * relevance) + (0.2 * performance);

        // Act
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(quality)
                .relevanceScore(relevance)
                .performanceScore(performance)
                .combinedScore(expectedCombined)
                .build();

        // Assert
        assertThat(scoreBreakdown.getCombinedScore()).isEqualTo(0.62, 
            org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void createExplanation_UsingNoArgsConstructor_Success() {
        // Act
        RecommendationExplanation explanation = new RecommendationExplanation();
        explanation.setReasonCode(RecommendationExplanation.ReasonCode.TECH_MATCH);
        explanation.setMatchingTags(Arrays.asList("Python", "AI"));

        // Assert
        assertThat(explanation.getReasonCode())
            .isEqualTo(RecommendationExplanation.ReasonCode.TECH_MATCH);
        assertThat(explanation.getMatchingTags()).containsExactly("Python", "AI");
    }

    @Test
    @DisplayName("Should support equals and hashCode")
    void explanation_SupportsEqualsAndHashCode() {
        // Arrange
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(0.75)
                .relevanceScore(0.85)
                .performanceScore(0.20)
                .combinedScore(0.72)
                .build();

        RecommendationExplanation explanation1 = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.TECH_MATCH)
                .matchingTags(Arrays.asList("React", "TypeScript"))
                .scoreBreakdown(scoreBreakdown)
                .build();

        RecommendationExplanation explanation2 = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.TECH_MATCH)
                .matchingTags(Arrays.asList("React", "TypeScript"))
                .scoreBreakdown(scoreBreakdown)
                .build();

        // Act & Assert
        assertThat(explanation1).isEqualTo(explanation2);
        assertThat(explanation1.hashCode()).isEqualTo(explanation2.hashCode());
    }

    @Test
    @DisplayName("Should support toString")
    void explanation_SupportsToString() {
        // Arrange
        RecommendationExplanation explanation = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.TECH_MATCH)
                .matchingTags(Arrays.asList("React"))
                .build();

        // Act
        String toString = explanation.toString();

        // Assert
        assertThat(toString).contains("TECH_MATCH");
        assertThat(toString).contains("React");
    }

    @Test
    @DisplayName("Should handle empty matching tags list")
    void createExplanation_WithEmptyMatchingTags_Success() {
        // Act
        RecommendationExplanation explanation = RecommendationExplanation.builder()
                .reasonCode(RecommendationExplanation.ReasonCode.TECH_MATCH)
                .matchingTags(Arrays.asList())
                .build();

        // Assert
        assertThat(explanation.getMatchingTags()).isEmpty();
    }

    @Test
    @DisplayName("Should handle negative performance score")
    void scoreBreakdown_WithNegativePerformanceScore_Success() {
        // Act
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            RecommendationExplanation.ScoreBreakdown.builder()
                .qualityScore(0.60)
                .relevanceScore(0.70)
                .performanceScore(-0.50)
                .combinedScore(0.44)
                .build();

        // Assert
        assertThat(scoreBreakdown.getPerformanceScore()).isEqualTo(-0.50);
        assertThat(scoreBreakdown.getCombinedScore()).isEqualTo(0.44);
    }

    @Test
    @DisplayName("Should verify all ReasonCode enum values exist")
    void reasonCode_AllValuesExist() {
        // Act & Assert
        assertThat(RecommendationExplanation.ReasonCode.values())
            .containsExactlyInAnyOrder(
                RecommendationExplanation.ReasonCode.TECH_MATCH,
                RecommendationExplanation.ReasonCode.FOLLOWED_BUILDER,
                RecommendationExplanation.ReasonCode.TRENDING,
                RecommendationExplanation.ReasonCode.HIGH_QUALITY,
                RecommendationExplanation.ReasonCode.COLD_START
            );
    }

    @Test
    @DisplayName("Should create ScoreBreakdown using no-args constructor")
    void createScoreBreakdown_UsingNoArgsConstructor_Success() {
        // Act
        RecommendationExplanation.ScoreBreakdown scoreBreakdown = 
            new RecommendationExplanation.ScoreBreakdown();
        scoreBreakdown.setQualityScore(0.80);
        scoreBreakdown.setRelevanceScore(0.90);
        scoreBreakdown.setPerformanceScore(0.30);
        scoreBreakdown.setCombinedScore(0.75);

        // Assert
        assertThat(scoreBreakdown.getQualityScore()).isEqualTo(0.80);
        assertThat(scoreBreakdown.getRelevanceScore()).isEqualTo(0.90);
        assertThat(scoreBreakdown.getPerformanceScore()).isEqualTo(0.30);
        assertThat(scoreBreakdown.getCombinedScore()).isEqualTo(0.75);
    }
}
