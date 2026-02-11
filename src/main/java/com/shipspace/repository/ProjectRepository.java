package com.shipspace.repository;

import com.shipspace.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Project document operations.
 * Provides CRUD operations and custom query methods for project management.
 */
@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {

    /**
     * Find all projects owned by a specific user.
     * Returns projects in the order specified by the Pageable parameter.
     *
     * @param userId the ID of the user who owns the projects
     * @param pageable pagination and sorting parameters
     * @return Page of projects owned by the user
     */
    Page<Project> findByUserId(String userId, Pageable pageable);

    /**
     * Find all projects owned by a specific user.
     * Returns all projects without pagination.
     *
     * @param userId the ID of the user who owns the projects
     * @return List of projects owned by the user
     */
    List<Project> findByUserId(String userId);

    /**
     * Find projects by technology tags.
     * Used for discovering projects using specific technologies.
     *
     * @param techTag the technology tag to search for
     * @param pageable pagination and sorting parameters
     * @return Page of projects containing the specified tech tag
     */
    Page<Project> findByTechTagsContaining(String techTag, Pageable pageable);

    /**
     * Count the number of projects owned by a specific user.
     *
     * @param userId the ID of the user
     * @return the count of projects owned by the user
     */
    long countByUserId(String userId);
}
