package com.shipspace.repository;

import com.shipspace.model.Ship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository interface for Ship document operations.
 * Provides CRUD operations and custom query methods for ship content management.
 */
@Repository
public interface ShipRepository extends MongoRepository<Ship, String> {

    /**
     * Find all ships created by a specific user.
     * Returns ships in the order specified by the Pageable parameter.
     *
     * @param userId the ID of the user who created the ships
     * @param pageable pagination and sorting parameters
     * @return Page of ships created by the user
     */
    Page<Ship> findByUserId(String userId, Pageable pageable);

    /**
     * Find all ships associated with a specific project.
     * Returns ships in chronological order (most recent first).
     *
     * @param projectId the ID of the project
     * @param pageable pagination and sorting parameters
     * @return Page of ships for the project
     */
    Page<Ship> findByProjectId(String projectId, Pageable pageable);

    /**
     * Find ships created by a list of user IDs.
     * Used for generating following feeds.
     *
     * @param userIds list of user IDs to include
     * @param pageable pagination and sorting parameters
     * @return Page of ships from the specified users
     */
    Page<Ship> findByUserIdIn(List<String> userIds, Pageable pageable);

    /**
     * Find ships created within a specific time window, ordered by trending score.
     * Used for generating trending feeds.
     *
     * @param startTime the start of the time window
     * @param pageable pagination and sorting parameters (should sort by trendingScore descending)
     * @return Page of ships within the time window
     */
    Page<Ship> findByCreatedAtAfter(Instant startTime, Pageable pageable);

    /**
     * Find all ships ordered by trending score.
     * Used for generating trending feeds without time window restrictions.
     *
     * @param pageable pagination and sorting parameters (should sort by trendingScore descending)
     * @return Page of ships ordered by trending score
     */
    @Query(sort = "{ 'trendingScore': -1 }")
    Page<Ship> findAllByOrderByTrendingScoreDesc(Pageable pageable);

    /**
     * Count the number of ships for a specific project.
     *
     * @param projectId the ID of the project
     * @return the count of ships for the project
     */
    long countByProjectId(String projectId);

    /**
     * Find ships with expired edit windows.
     * Used for cleanup or analytics.
     *
     * @param currentTime the current timestamp
     * @return List of ships with expired edit windows
     */
    List<Ship> findByEditWindowExpiresBefore(Instant currentTime);
}
