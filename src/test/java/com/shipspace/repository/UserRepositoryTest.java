package com.shipspace.repository;

import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserRepository.
 * Tests custom query methods for user management and score decay.
 * 
 * Validates:
 * - findByLastShipActivityAtBefore() for identifying inactive users
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 11.2</a>
 */
@DataMongoTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    private Instant now;
    private Instant sixtyDaysAgo;
    private Instant thirtyDaysAgo;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        sixtyDaysAgo = now.minus(60, ChronoUnit.DAYS);
        thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void findByLastShipActivityAtBefore_WhenInactiveUsersExist_ShouldReturnThem() {
        // Given: Users with activity before the threshold (inactive)
        User inactiveUser1 = createUser("user1", "inactive1", sixtyDaysAgo.minus(10, ChronoUnit.DAYS));
        User inactiveUser2 = createUser("user2", "inactive2", sixtyDaysAgo.minus(5, ChronoUnit.DAYS));
        User activeUser = createUser("user3", "active1", thirtyDaysAgo);

        repository.save(inactiveUser1);
        repository.save(inactiveUser2);
        repository.save(activeUser);

        // When: Finding users inactive before the threshold
        List<User> inactiveUsers = repository.findByLastShipActivityAtBefore(sixtyDaysAgo);

        // Then: Should return only inactive users
        assertThat(inactiveUsers).hasSize(2);
        assertThat(inactiveUsers).extracting(User::getUsername)
                .containsExactlyInAnyOrder("inactive1", "inactive2");
    }

    @Test
    void findByLastShipActivityAtBefore_WhenNoInactiveUsers_ShouldReturnEmptyList() {
        // Given: All users are active (activity within threshold)
        User activeUser1 = createUser("user1", "active1", thirtyDaysAgo);
        User activeUser2 = createUser("user2", "active2", now.minus(10, ChronoUnit.DAYS));

        repository.save(activeUser1);
        repository.save(activeUser2);

        // When: Finding users inactive before the threshold
        List<User> inactiveUsers = repository.findByLastShipActivityAtBefore(sixtyDaysAgo);

        // Then: Should return empty list
        assertThat(inactiveUsers).isEmpty();
    }

    @Test
    void findByLastShipActivityAtBefore_WhenNoUsersExist_ShouldReturnEmptyList() {
        // Given: No users in the database
        // (database is empty after setUp)

        // When: Finding inactive users
        List<User> inactiveUsers = repository.findByLastShipActivityAtBefore(sixtyDaysAgo);

        // Then: Should return empty list
        assertThat(inactiveUsers).isEmpty();
    }

    @Test
    void findByLastShipActivityAtBefore_WithExactThreshold_ShouldNotIncludeUsersAtThreshold() {
        // Given: User with activity exactly at the threshold
        User userAtThreshold = createUser("user1", "threshold", sixtyDaysAgo);
        User userBeforeThreshold = createUser("user2", "before", sixtyDaysAgo.minus(1, ChronoUnit.SECONDS));

        repository.save(userAtThreshold);
        repository.save(userBeforeThreshold);

        // When: Finding users inactive before the threshold
        List<User> inactiveUsers = repository.findByLastShipActivityAtBefore(sixtyDaysAgo);

        // Then: Should only include users strictly before threshold
        assertThat(inactiveUsers).hasSize(1);
        assertThat(inactiveUsers.get(0).getUsername()).isEqualTo("before");
    }

    @Test
    void findByLastShipActivityAtBefore_WithNullLastShipActivityAt_ShouldNotIncludeThem() {
        // Given: Users with null lastShipActivityAt (never had activity)
        User userWithNullActivity = createUser("user1", "nullActivity", null);
        User userWithActivity = createUser("user2", "withActivity", sixtyDaysAgo.minus(10, ChronoUnit.DAYS));

        repository.save(userWithNullActivity);
        repository.save(userWithActivity);

        // When: Finding users inactive before the threshold
        List<User> inactiveUsers = repository.findByLastShipActivityAtBefore(sixtyDaysAgo);

        // Then: Should only include users with non-null activity before threshold
        assertThat(inactiveUsers).hasSize(1);
        assertThat(inactiveUsers.get(0).getUsername()).isEqualTo("withActivity");
    }

    @Test
    void findByLastShipActivityAtBefore_ForDecayJob_ShouldIdentifyCorrectUsers() {
        // Given: Mix of users with various activity timestamps
        Instant sixtyOneDaysAgo = now.minus(61, ChronoUnit.DAYS);
        Instant ninetyDaysAgo = now.minus(90, ChronoUnit.DAYS);
        Instant fiftyNineDaysAgo = now.minus(59, ChronoUnit.DAYS);

        User veryInactive = createUser("user1", "veryInactive", ninetyDaysAgo);
        User justInactive = createUser("user2", "justInactive", sixtyOneDaysAgo);
        User justActive = createUser("user3", "justActive", fiftyNineDaysAgo);
        User veryActive = createUser("user4", "veryActive", now.minus(1, ChronoUnit.DAYS));

        repository.save(veryInactive);
        repository.save(justInactive);
        repository.save(justActive);
        repository.save(veryActive);

        // When: Finding users for decay (inactive > 60 days)
        List<User> usersForDecay = repository.findByLastShipActivityAtBefore(sixtyDaysAgo);

        // Then: Should identify users inactive for more than 60 days
        assertThat(usersForDecay).hasSize(2);
        assertThat(usersForDecay).extracting(User::getUsername)
                .containsExactlyInAnyOrder("veryInactive", "justInactive");
    }

    @Test
    void findByLastShipActivityAtBefore_ShouldReturnUsersWithAllFields() {
        // Given: User with complete profile
        User user = User.builder()
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .shipScore(500)
                .level(13)
                .rank(RankTier.ORBITER)
                .lastShipActivityAt(sixtyDaysAgo.minus(10, ChronoUnit.DAYS))
                .rankUpdatedAt(now.minus(100, ChronoUnit.DAYS))
                .createdAt(now.minus(200, ChronoUnit.DAYS))
                .build();

        repository.save(user);

        // When: Finding inactive users
        List<User> inactiveUsers = repository.findByLastShipActivityAtBefore(sixtyDaysAgo);

        // Then: Should return user with all fields intact
        assertThat(inactiveUsers).hasSize(1);
        User found = inactiveUsers.get(0);
        assertThat(found.getGithubId()).isEqualTo("github123");
        assertThat(found.getUsername()).isEqualTo("testuser");
        assertThat(found.getShipScore()).isEqualTo(500);
        assertThat(found.getLevel()).isEqualTo(13);
        assertThat(found.getRank()).isEqualTo(RankTier.ORBITER);
        assertThat(found.getLastShipActivityAt()).isBefore(sixtyDaysAgo);
    }

    // Helper method to create a test user
    private User createUser(String githubId, String username, Instant lastShipActivityAt) {
        return User.builder()
                .githubId(githubId)
                .username(username)
                .email(username + "@example.com")
                .shipScore(100)
                .level(3)
                .rank(RankTier.DOCKED)
                .lastShipActivityAt(lastShipActivityAt)
                .createdAt(now.minus(100, ChronoUnit.DAYS))
                .build();
    }
}
