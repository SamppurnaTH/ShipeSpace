package com.shipspace.service;

import com.shipspace.dto.UserActivityResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.dto.UserUpdateRequest;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
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
     * @return the updated user entity
     * @throws ResourceNotFoundException if user is not found
     */
    @Transactional
    public User updateUserProfile(String userId, UserUpdateRequest updateRequest) {
        log.info("Updating profile for user ID: {}", userId);
        
        User user = getUserById(userId);
        
        // Update only provided fields
        if (updateRequest.getEmail() != null) {
            user.setEmail(updateRequest.getEmail());
        }
        if (updateRequest.getBio() != null) {
            user.setBio(updateRequest.getBio());
        }
        if (updateRequest.getLocation() != null) {
            user.setLocation(updateRequest.getLocation());
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
        
        return updatedUser;
    }

    /**
     * Retrieves all projects owned by a user.
     * 
     * @param userId the ID of the user
     * @return list of projects owned by the user
     * @throws ResourceNotFoundException if user is not found
     */
    public List<Project> getUserProjects(String userId) {
        log.debug("Retrieving projects for user ID: {}", userId);
        
        // Verify user exists
        getUserById(userId);
        
        // Get all projects for the user, sorted by most recent first
        return projectRepository.findByUserId(userId);
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
        
        // Count total ships
        long totalShips = shipRepository.findByUserId(userId, Pageable.unpaged()).getTotalElements();
        
        // Calculate total engagement (sum of likes and comments across all ships)
        long totalEngagement = recentShips.stream()
                .mapToLong(ship -> ship.getLikeCount() + ship.getCommentCount())
                .sum();
        
        log.debug("Found {} recent ships and {} total ships for user ID: {}", 
                recentShips.size(), totalShips, userId);
        
        return UserActivityResponse.builder()
                .recentShips(recentShips)
                .totalShips(totalShips)
                .totalEngagement(totalEngagement)
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
     * Updates the project count for a user.
     * Called when projects are created or deleted.
     * 
     * @param userId the ID of the user
     */
    @Transactional
    public void updateProjectCount(String userId) {
        log.debug("Updating project count for user ID: {}", userId);
        
        User user = getUserById(userId);
        long projectCount = projectRepository.countByUserId(userId);
        user.setProjectCount((int) projectCount);
        user.setUpdatedAt(Instant.now());
        
        userRepository.save(user);
        log.debug("Updated project count to {} for user ID: {}", projectCount, userId);
    }
}
