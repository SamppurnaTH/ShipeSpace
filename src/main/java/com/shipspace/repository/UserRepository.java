package com.shipspace.repository;

import com.shipspace.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User document operations.
 * Provides CRUD operations and custom query methods for user management.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Find a user by their GitHub ID.
     * Used during OAuth authentication to retrieve or create users.
     *
     * @param githubId the GitHub user ID
     * @return Optional containing the user if found
     */
    Optional<User> findByGithubId(String githubId);

    /**
     * Find a user by their username.
     * Used for profile lookups and username validation.
     *
     * @param username the username to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if a user exists with the given GitHub ID.
     *
     * @param githubId the GitHub user ID
     * @return true if a user exists with this GitHub ID
     */
    boolean existsByGithubId(String githubId);

    /**
     * Check if a user exists with the given username.
     * Used for username uniqueness validation.
     *
     * @param username the username to check
     * @return true if a user exists with this username
     */
    boolean existsByUsername(String username);

    /**
     * Find all users whose last ship activity occurred before the specified threshold.
     * Used by the score decay job to identify inactive users.
     *
     * @param threshold the Instant threshold; users with lastShipActivityAt before this time are returned
     * @return List of users who have been inactive since before the threshold
     */
    List<User> findByLastShipActivityAtBefore(Instant threshold);
}
