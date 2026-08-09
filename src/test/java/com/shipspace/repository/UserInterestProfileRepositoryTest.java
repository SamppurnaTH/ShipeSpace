package com.shipspace.repository;

import com.shipspace.model.UserInterestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserInterestProfileRepository.
 * Tests the custom query method findByUserId and basic CRUD operations.
 */
@DataMongoTest
@ActiveProfiles("test")
class UserInterestProfileRepositoryTest {

    @Autowired
    private UserInterestProfileRepository repository;

    private UserInterestProfile testProfile;

    @BeforeEach
    void setUp() {
        // Create a test profile with sample data
        Map<String, Double> techTagScores = new HashMap<>();
        techTagScores.put("React", 0.85);
        techTagScores.put("Python", 0.62);
        techTagScores.put("AI", 0.91);

        Set<String> followedBuilders = new HashSet<>();
        followedBuilders.add("builder123");
        followedBuilders.add("builder456");

        testProfile = UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(techTagScores)
                .followedBuilders(followedBuilders)
                .totalSignals(47)
                .lastSignalAt(Instant.now().minusSeconds(3600))
                .updatedAt(Instant.now())
                .createdAt(Instant.now().minusSeconds(86400))
                .build();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void findByUserId_WhenProfileExists_ShouldReturnProfile() {
        // Given: A profile is saved in the database
        repository.save(testProfile);

        // When: Finding by userId
        Optional<UserInterestProfile> result = repository.findByUserId("user123");

        // Then: The profile should be found with correct data
        assertThat(result).isPresent();
        UserInterestProfile found = result.get();
        assertThat(found.getUserId()).isEqualTo("user123");
        assertThat(found.getTechTagScores()).hasSize(3);
        assertThat(found.getTechTagScores().get("React")).isEqualTo(0.85);
        assertThat(found.getTechTagScores().get("Python")).isEqualTo(0.62);
        assertThat(found.getTechTagScores().get("AI")).isEqualTo(0.91);
        assertThat(found.getFollowedBuilders()).containsExactlyInAnyOrder("builder123", "builder456");
        assertThat(found.getTotalSignals()).isEqualTo(47);
    }

    @Test
    void findByUserId_WhenProfileDoesNotExist_ShouldReturnEmpty() {
        // Given: No profile exists for the userId
        // (database is empty after setUp)

        // When: Finding by userId
        Optional<UserInterestProfile> result = repository.findByUserId("nonexistent");

        // Then: The result should be empty
        assertThat(result).isEmpty();
    }

    @Test
    void findByUserId_WhenMultipleProfilesExist_ShouldReturnCorrectOne() {
        // Given: Multiple profiles exist in the database
        repository.save(testProfile);

        UserInterestProfile anotherProfile = UserInterestProfile.builder()
                .userId("user456")
                .techTagScores(new HashMap<>())
                .followedBuilders(new HashSet<>())
                .totalSignals(5)
                .updatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        repository.save(anotherProfile);

        // When: Finding by specific userId
        Optional<UserInterestProfile> result = repository.findByUserId("user456");

        // Then: The correct profile should be returned
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("user456");
        assertThat(result.get().getTotalSignals()).isEqualTo(5);
    }

    @Test
    void save_ShouldPersistProfile() {
        // When: Saving a new profile
        UserInterestProfile saved = repository.save(testProfile);

        // Then: The profile should be persisted with an ID
        assertThat(saved.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void findByUserId_ShouldEnforceUniqueConstraint() {
        // Given: A profile is saved
        repository.save(testProfile);

        // When: Attempting to save another profile with the same userId
        UserInterestProfile duplicate = UserInterestProfile.builder()
                .userId("user123")
                .techTagScores(new HashMap<>())
                .followedBuilders(new HashSet<>())
                .totalSignals(0)
                .updatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        // Then: MongoDB enforces one profile per userId at the unique index.
        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void delete_ShouldRemoveProfile() {
        // Given: A profile is saved
        UserInterestProfile saved = repository.save(testProfile);

        // When: Deleting the profile
        repository.delete(saved);

        // Then: The profile should no longer exist
        Optional<UserInterestProfile> result = repository.findByUserId("user123");
        assertThat(result).isEmpty();
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void findByUserId_WithEmptyTechTagScores_ShouldWork() {
        // Given: A profile with empty tech tag scores
        UserInterestProfile emptyProfile = UserInterestProfile.builder()
                .userId("newUser")
                .techTagScores(new HashMap<>())
                .followedBuilders(new HashSet<>())
                .totalSignals(0)
                .updatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        repository.save(emptyProfile);

        // When: Finding by userId
        Optional<UserInterestProfile> result = repository.findByUserId("newUser");

        // Then: The profile should be found with empty collections
        assertThat(result).isPresent();
        assertThat(result.get().getTechTagScores()).isEmpty();
        assertThat(result.get().getFollowedBuilders()).isEmpty();
        assertThat(result.get().getTotalSignals()).isEqualTo(0);
    }
}
