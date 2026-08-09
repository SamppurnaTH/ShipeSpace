package com.shipspace.service;

import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserActivityResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.dto.UserUpdateRequest;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserCountRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.util.DtoMapper;
import com.shipspace.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for user profile management operations.
 * Handles user profile retrieval, updates, and activity aggregation.
 * 
 * Implements Requirements 2.1, 2.3, 2.4, 2.5:
 * - User profile retrieval with authentication
 * - Profile update with validation
 * - Public profile access by user ID
 * - User activity and project aggregation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ShipRepository shipRepository;
    private final DtoMapper dtoMapper;
    private final UserCountRepository userCountRepository;
    private final InputSanitizer inputSanitizer;

    /**
     * Retrieves a user by their ID.
     * 
     * @param userId the ID of the user to retrieve
     * @return the user entity
     * @throws ResourceNotFoundException if user is not found
     */
    public User getUserById(String userId) {
        log.debug("Retrieving user with ID: {}", userId);
        
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Retrieves a public user profile by user ID.
     * Returns only publicly visible information.
     * 
     * @param userId the ID of the user
     * @return public profile information
     * @throws ResourceNotFoundException if user is not found
     */
    public UserProfileResponse getPublicProfile(String userId) {
        log.debug("Retrieving public profile for user ID: {}", userId);
        
        User user = getUserById(userId);
        
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .location(user.getLocation())
                .websiteUrl(user.getWebsiteUrl())
                .followerCount(user.getFollowerCount())
                .followingCount(user.getFollowingCount())
                .projectCount(user.getProjectCount())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Updates a user's profile information.
     * Only updates fields that are provided (non-null) in the request.
     * 
     * @param userId the ID of the user to update
     * @param updateRequest the update request with new profile data
     * @return the updated user as CurrentUserResponse DTO
     * @throws ResourceNotFoundException if user is not found
     */
    @Transactional
    public CurrentUserResponse updateUserProfile(String userId, UserUpdateRequest updateRequest) {
        log.info("Updating profile for user ID: {}", userId);
        
        User user = getUserById(userId);
        
        // Update only provided fields — sanitize all free-text inputs
        if (updateRequest.getEmail() != null) {
            user.setEmail(updateRequest.getEmail());
        }
        if (updateRequest.getBio() != null) {
            user.setBio(inputSanitizer.sanitize(updateRequest.getBio(), 500));
        }
        if (updateRequest.getLocation() != null) {
            user.setLocation(inputSanitizer.sanitize(updateRequest.getLocation(), 100));
        }
        if (updateRequest.getWebsiteUrl() != null) {
            user.setWebsiteUrl(updateRequest.getWebsiteUrl());
        }
        if (updateRequest.getAvatarUrl() != null) {
            user.setAvatarUrl(updateRequest.getAvatarUrl());
        }
        
        user.setUpdatedAt(Instant.now());
        
        User updatedUser = userRepository.save(user);
        log.info("Successfully updated profile for user: {}", updatedUser.getUsername());
        
        return toCurrentUserResponse(updatedUser);
    }

    /**
     * Retrieves ships posted by a user (paginated, public).
     */
    public org.springframework.data.domain.Page<com.shipspace.dto.ShipResponse> getUserShips(
            String userId, org.springframework.data.domain.Pageable pageable) {
        getUserById(userId); // validates user exists
        return shipRepository.findByUserId(userId, pageable)
                .map(dtoMapper::toShipResponse);
    }

    /**
     * Retrieves projects owned by a user with pagination.
     *
     * @param userId   the ID of the user
     * @param pageable pagination parameters
     * @return page of project responses
     */
    public org.springframework.data.domain.Page<ProjectResponse> getUserProjects(
            String userId, org.springframework.data.domain.Pageable pageable) {
        log.debug("Retrieving projects for user ID: {}", userId);
        getUserById(userId);
        return projectRepository.findByUserId(userId, pageable)
                .map(dtoMapper::toProjectResponse);
    }

    /**
     * Retrieves all projects owned by a user (no pagination — kept for internal use only).
     * Do NOT expose this via a public API endpoint.
     */
    public List<ProjectResponse> getUserProjects(String userId) {
        log.debug("Retrieving all projects for user ID: {} (internal use)", userId);
        getUserById(userId);
        List<Project> projects = projectRepository.findByUserId(userId);
        return projects.stream().map(dtoMapper::toProjectResponse).toList();
    }

    /**
     * Retrieves a user's recent activity including ships and engagement metrics.
     * Returns the most recent ships and aggregated engagement statistics.
     * 
     * @param userId the ID of the user
     * @param limit the maximum number of recent ships to return (default: 10)
     * @return user activity response with recent ships and metrics
     * @throws ResourceNotFoundException if user is not found
     */
    public UserActivityResponse getUserActivity(String userId, int limit) {
        log.debug("Retrieving activity for user ID: {} with limit: {}", userId, limit);
        
        // Verify user exists
        getUserById(userId);
        
        // Get recent ships sorted by creation date (most recent first)
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Ship> recentShips = shipRepository.findByUserId(userId, pageable).getContent();

        // Use count query — never load all ships into heap
        long totalShips = shipRepository.countByUserId(userId);

        // Calculate total engagement on the returned page only (documented as "recent engagement")
        long totalEngagement = recentShips.stream()
                .mapToLong(ship -> ship.getLikeCount() + ship.getCommentCount())
                .sum();
        
        log.debug("Found {} recent ships and {} total ships for user ID: {}", 
                recentShips.size(), totalShips, userId);
        
        List<ShipResponse> shipResponses = recentShips.stream()
                .map(dtoMapper::toShipResponse)
                .toList();
        
        return UserActivityResponse.builder()
                .recentShips(shipResponses)
                .totalShips(totalShips)
                .totalEngagement(totalEngagement)
                .build();
    }

    /**
     * Batch-fetch users by IDs. Used by FeedController for enrichment.
     */
    public List<User> findAllByIds(java.util.Collection<String> ids) {
        return userRepository.findAllById(ids);
    }

    /**
     * Maps User entity to CurrentUserResponse DTO.
     */
    private CurrentUserResponse toCurrentUserResponse(User user) {
        return CurrentUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .location(user.getLocation())
                .websiteUrl(user.getWebsiteUrl())
                .followerCount(user.getFollowerCount())
                .followingCount(user.getFollowingCount())
                .projectCount(user.getProjectCount())
                .shipScore(user.getShipScore())
                .level(user.getLevel())
                .rank(user.getRank())
                .roles(user.getRoles())
                .banned(user.isBanned())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Retrieves a user's recent activity with default limit of 10 ships.
     * 
     * @param userId the ID of the user
     * @return user activity response with recent ships and metrics
     * @throws ResourceNotFoundException if user is not found
     */
    public UserActivityResponse getUserActivity(String userId) {
        return getUserActivity(userId, 10);
    }

    /**
     * Atomically increments the project count for a user.
     * delta = +1 on create, -1 on delete.
     */
    @Transactional
    public void updateProjectCount(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        userCountRepository.incrementProjectCount(userId, 1);
        log.debug("Atomically incremented project count for user {}", userId);
    }

    /**
     * Atomically decrements the project count for a user (on project delete).
     */
    @Transactional
    public void decrementProjectCount(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        userCountRepository.incrementProjectCount(userId, -1);
        log.debug("Atomically decremented project count for user {}", userId);
    }
}
