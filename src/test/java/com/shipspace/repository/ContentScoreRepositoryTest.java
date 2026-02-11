package com.shipspace.repository;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.ContentScore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ContentScoreRepository.
 * Tests the custom query methods findByContentId and findByAgeCohort,
 * as well as basic CRUD operations.
 */
@DataMongoTest
@ActiveProfiles("test")
class ContentScoreRepositoryTest {

    @Autowired
    private ContentScoreRepository repository;

    private ContentScore testScore1;
    private ContentScore testScore2;
    private ContentScore testScore3;

    @BeforeEach
    void setUp() {
        // Create test content scores with different age cohorts
        testScore1 = ContentScore.builder()
                .contentId("ship123")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .qualityScore(0.78)
                .performanceScore(0.23)
                .ageCohort("0-24h")
                .engagementRate(0.15)
                .updatedAt(Instant.now())
                .build();

        testScore2 = ContentScore.builder()
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .qualityScore(0.65)
                .performanceScore(-0.12)
                .ageCohort("1-7d")
                .engagementRate(0.08)
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();

        testScore3 = ContentScore.builder()
                .contentId("project789")
                .contentType(BehavioralSignal.ContentType.PROJECT)
                .qualityScore(0.92)
                .performanceScore(0.87)
                .ageCohort("1-7d")
                .engagementRate(0.32)
                .updatedAt(Instant.now().minusSeconds(7200))
                .build();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void findByContentId_WhenScoreExists_ShouldReturnScore() {
        // Given: A content score is saved in the database
        repository.save(testScore1);

        // When: Finding by contentId
        Optional<ContentScore> result = repository.findByContentId("ship123");

        // Then: The score should be found with correct data
        assertThat(result).isPresent();
        ContentScore found = result.get();
        assertThat(found.getContentId()).isEqualTo("ship123");
        assertThat(found.getContentType()).isEqualTo(BehavioralSignal.ContentType.SHIP);
        assertThat(found.getQualityScore()).isEqualTo(0.78);
        assertThat(found.getPerformanceScore()).isEqualTo(0.23);
        assertThat(found.getAgeCohort()).isEqualTo("0-24h");
        assertThat(found.getEngagementRate()).isEqualTo(0.15);
    }

    @Test
    void findByContentId_WhenScoreDoesNotExist_ShouldReturnEmpty() {
        // Given: No score exists for the contentId
        // (database is empty after setUp)

        // When: Finding by contentId
        Optional<ContentScore> result = repository.findByContentId("nonexistent");

        // Then: The result should be empty
        assertThat(result).isEmpty();
    }

    @Test
    void findByContentId_WhenMultipleScoresExist_ShouldReturnCorrectOne() {
        // Given: Multiple content scores exist in the database
        repository.save(testScore1);
        repository.save(testScore2);
        repository.save(testScore3);

        // When: Finding by specific contentId
        Optional<ContentScore> result = repository.findByContentId("project789");

        // Then: The correct score should be returned
        assertThat(result).isPresent();
        assertThat(result.get().getContentId()).isEqualTo("project789");
        assertThat(result.get().getContentType()).isEqualTo(BehavioralSignal.ContentType.PROJECT);
        assertThat(result.get().getQualityScore()).isEqualTo(0.92);
    }

    @Test
    void findByAgeCohort_WhenScoresExist_ShouldReturnAllMatchingScores() {
        // Given: Multiple content scores with different age cohorts
        repository.save(testScore1);
        repository.save(testScore2);
        repository.save(testScore3);

        // When: Finding by age cohort "1-7d"
        List<ContentScore> results = repository.findByAgeCohort("1-7d");

        // Then: All scores in that cohort should be returned
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(ContentScore::getContentId)
                .containsExactlyInAnyOrder("ship456", "project789");
        assertThat(results)
                .allMatch(score -> score.getAgeCohort().equals("1-7d"));
    }

    @Test
    void findByAgeCohort_WhenNoScoresInCohort_ShouldReturnEmptyList() {
        // Given: Content scores exist but not in the queried cohort
        repository.save(testScore1); // "0-24h"
        repository.save(testScore2); // "1-7d"

        // When: Finding by age cohort "30d+"
        List<ContentScore> results = repository.findByAgeCohort("30d+");

        // Then: The result should be empty
        assertThat(results).isEmpty();
    }

    @Test
    void findByAgeCohort_WithSingleScore_ShouldReturnSingletonList() {
        // Given: Only one score exists in a cohort
        repository.save(testScore1); // "0-24h"

        // When: Finding by that cohort
        List<ContentScore> results = repository.findByAgeCohort("0-24h");

        // Then: A list with one element should be returned
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getContentId()).isEqualTo("ship123");
    }

    @Test
    void save_ShouldPersistContentScore() {
        // When: Saving a new content score
        ContentScore saved = repository.save(testScore1);

        // Then: The score should be persisted with an ID
        assertThat(saved.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void findByContentId_ShouldEnforceUniqueConstraint() {
        // Given: A content score is saved
        repository.save(testScore1);

        // When: Attempting to save another score with the same contentId
        ContentScore duplicate = ContentScore.builder()
                .contentId("ship123")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .qualityScore(0.50)
                .performanceScore(0.10)
                .ageCohort("1-7d")
                .engagementRate(0.10)
                .updatedAt(Instant.now())
                .build();

        // Then: The unique constraint should be enforced
        // MongoDB unique index enforcement happens at the database level
        // Attempting to save a duplicate should throw DuplicateKeyException
        assertThat(repository.findByContentId("ship123")).isPresent();
        
        // Verify that attempting to insert duplicate throws exception
        // (In production, updates should use save with existing ID or update operations)
        try {
            repository.save(duplicate);
            // If we get here, the unique constraint is not working as expected
            // But we can still verify only one document exists
            assertThat(repository.count()).isEqualTo(1);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Expected: unique constraint is enforced
            assertThat(e.getMessage()).contains("duplicate key error");
        }
    }

    @Test
    void delete_ShouldRemoveContentScore() {
        // Given: A content score is saved
        ContentScore saved = repository.save(testScore1);

        // When: Deleting the score
        repository.delete(saved);

        // Then: The score should no longer exist
        Optional<ContentScore> result = repository.findByContentId("ship123");
        assertThat(result).isEmpty();
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void findByAgeCohort_ShouldHandleAllCohortTypes() {
        // Given: Content scores in all cohort types
        ContentScore cohort0_24h = ContentScore.builder()
                .contentId("content1")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .qualityScore(0.5)
                .performanceScore(0.0)
                .ageCohort("0-24h")
                .engagementRate(0.1)
                .updatedAt(Instant.now())
                .build();

        ContentScore cohort1_7d = ContentScore.builder()
                .contentId("content2")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .qualityScore(0.6)
                .performanceScore(0.1)
                .ageCohort("1-7d")
                .engagementRate(0.12)
                .updatedAt(Instant.now())
                .build();

        ContentScore cohort7_30d = ContentScore.builder()
                .contentId("content3")
                .contentType(BehavioralSignal.ContentType.PROJECT)
                .qualityScore(0.7)
                .performanceScore(0.2)
                .ageCohort("7-30d")
                .engagementRate(0.14)
                .updatedAt(Instant.now())
                .build();

        ContentScore cohort30dPlus = ContentScore.builder()
                .contentId("content4")
                .contentType(BehavioralSignal.ContentType.PROJECT)
                .qualityScore(0.8)
                .performanceScore(0.3)
                .ageCohort("30d+")
                .engagementRate(0.16)
                .updatedAt(Instant.now())
                .build();

        repository.save(cohort0_24h);
        repository.save(cohort1_7d);
        repository.save(cohort7_30d);
        repository.save(cohort30dPlus);

        // When/Then: Each cohort query should return the correct score
        assertThat(repository.findByAgeCohort("0-24h")).hasSize(1);
        assertThat(repository.findByAgeCohort("1-7d")).hasSize(1);
        assertThat(repository.findByAgeCohort("7-30d")).hasSize(1);
        assertThat(repository.findByAgeCohort("30d+")).hasSize(1);
    }

    @Test
    void findByAgeCohort_ShouldIncludeBothShipsAndProjects() {
        // Given: Both ships and projects in the same cohort
        repository.save(testScore2); // ship in "1-7d"
        repository.save(testScore3); // project in "1-7d"

        // When: Finding by cohort
        List<ContentScore> results = repository.findByAgeCohort("1-7d");

        // Then: Both content types should be included
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(ContentScore::getContentType)
                .containsExactlyInAnyOrder(
                        BehavioralSignal.ContentType.SHIP,
                        BehavioralSignal.ContentType.PROJECT
                );
    }

    @Test
    void findByContentId_WithProjectContent_ShouldWork() {
        // Given: A project content score
        repository.save(testScore3);

        // When: Finding by contentId
        Optional<ContentScore> result = repository.findByContentId("project789");

        // Then: The project score should be found
        assertThat(result).isPresent();
        assertThat(result.get().getContentType()).isEqualTo(BehavioralSignal.ContentType.PROJECT);
    }

    @Test
    void save_WithNegativePerformanceScore_ShouldWork() {
        // Given: A content score with negative performance score
        ContentScore negativeScore = ContentScore.builder()
                .contentId("lowPerformer")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .qualityScore(0.45)
                .performanceScore(-0.75)
                .ageCohort("7-30d")
                .engagementRate(0.03)
                .updatedAt(Instant.now())
                .build();

        // When: Saving the score
        repository.save(negativeScore);

        // Then: The negative performance score should be persisted correctly
        Optional<ContentScore> result = repository.findByContentId("lowPerformer");
        assertThat(result).isPresent();
        assertThat(result.get().getPerformanceScore()).isEqualTo(-0.75);
    }

    @Test
    void findByAgeCohort_WithUpdatedAtTimestamps_ShouldReturnAllMatches() {
        // Given: Multiple scores in same cohort with different update times
        repository.save(testScore2); // updated 1 hour ago
        repository.save(testScore3); // updated 2 hours ago

        // When: Finding by cohort
        List<ContentScore> results = repository.findByAgeCohort("1-7d");

        // Then: All scores should be returned regardless of update time
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(ContentScore::getUpdatedAt)
                .allMatch(instant -> instant.isBefore(Instant.now()));
    }
}
