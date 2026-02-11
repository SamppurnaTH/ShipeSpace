package com.shipspace.service;

import com.shipspace.dto.UserProfileResponse;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Following;
import com.shipspace.model.User;
import com.shipspace.repository.FollowingRepository;
import com.shipspace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing social following relationships.
 * Handles follow/unfollow operations and retrieval of follower/following lists.
 * 
 * Implements Requirements 5.1, 5.2, 5.4, 5.5:
 * - Follow/unfollow operations with duplicate prevention
 * - Follower count updates
 * - Self-follow prevention
 * - Paginated follower/following lists with user profile summaries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialService {

    private final FollowingRepository followingRepository;
    private final UserRepository userRepository;

    /**
     * Creates a following relationship between two users.
     * Prevents duplicate follows and self-follows.
     * Updates follower/following counts for both users.
     * 
     * @param followerId the ID of the user who wants to follow
     * @param followeeId the ID of the user to be followed
     * @throws ForbiddenException if user attempts to follow themselves
     * @throws ResourceNotFoundException if either user is not found
     */
    @Transactional
    public void followUser(String followerId, String followeeId) {
        log.info("User {} attempting to follow user {}", followerId, followeeId);
        
        // Prevent self-follow
        if (followerId.equals(followeeId)) {
            log.warn("User {} attempted to follow themselves", followerId);
            throw new ForbiddenException("Users cannot follow themselves");
        }
        
        // Verify both users exist
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", followerId));
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", followeeId));
        
        // Check if relationship already exists (duplicate prevention)
        if (followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.debug("Following relationship already exists between {} and {}", followerId, followeeId);
            return; // Idempotent operation - silently succeed if already following
        }
        
        // Create following relationship
        Following following = Following.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .createdAt(Instant.now())
                .build();
        
        followingRepository.save(following);
        log.info("Created following relationship: {} -> {}", followerId, followeeId);
        
        // Update follower counts
        updateFollowerCounts(follower, followee);
    }

    /**
     * Removes a following relationship between two users.
     * Updates follower/following counts for both users.
     * 
     * @param followerId the ID of the user who wants to unfollow
     * @param followeeId the ID of the user to be unfollowed
     * @throws ResourceNotFoundException if either user is not found
     */
    @Transactional
    public void unfollowUser(String followerId, String followeeId) {
        log.info("User {} attempting to unfollow user {}", followerId, followeeId);
        
        // Verify both users exist
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", followerId));
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", followeeId));
        
        // Check if relationship exists
        if (!followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.debug("No following relationship exists between {} and {}", followerId, followeeId);
            return; // Idempotent operation - silently succeed if not following
        }
        
        // Delete following relationship
        followingRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        log.info("Deleted following relationship: {} -> {}", followerId, followeeId);
        
        // Update follower counts
        updateFollowerCounts(follower, followee);
    }

    /**
     * Retrieves a paginated list of users following the specified user.
     * Returns user profile summaries for each follower.
     * 
     * @param userId the ID of the user whose followers to retrieve
     * @param pageable pagination and sorting parameters
     * @return page of user profile responses for followers
     * @throws ResourceNotFoundException if user is not found
     */
    public Page<UserProfileResponse> getFollowers(String userId, Pageable pageable) {
        log.debug("Retrieving followers for user ID: {} with pagination: {}", userId, pageable);
        
        // Verify user exists
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        
        // Get paginated following relationships where this user is the followee
        Page<Following> followingPage = followingRepository.findByFolloweeId(userId, pageable);
        
        // Map to user profile responses
        return followingPage.map(following -> {
            User follower = userRepository.findById(following.getFollowerId())
                    .orElse(null);
            
            if (follower == null) {
                log.warn("Follower user {} not found for following relationship {}", 
                        following.getFollowerId(), following.getId());
                return null;
            }
            
            return mapToUserProfileResponse(follower);
        }).map(profile -> profile); // Filter out nulls handled by Spring Data
        
    }

    /**
     * Retrieves a paginated list of users that the specified user is following.
     * Returns user profile summaries for each followed user.
     * 
     * @param userId the ID of the user whose following list to retrieve
     * @param pageable pagination and sorting parameters
     * @return page of user profile responses for followed users
     * @throws ResourceNotFoundException if user is not found
     */
    public Page<UserProfileResponse> getFollowing(String userId, Pageable pageable) {
        log.debug("Retrieving following list for user ID: {} with pagination: {}", userId, pageable);
        
        // Verify user exists
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        
        // Get paginated following relationships where this user is the follower
        Page<Following> followingPage = followingRepository.findByFollowerId(userId, pageable);
        
        // Map to user profile responses
        return followingPage.map(following -> {
            User followee = userRepository.findById(following.getFolloweeId())
                    .orElse(null);
            
            if (followee == null) {
                log.warn("Followee user {} not found for following relationship {}", 
                        following.getFolloweeId(), following.getId());
                return null;
            }
            
            return mapToUserProfileResponse(followee);
        }).map(profile -> profile); // Filter out nulls handled by Spring Data
    }

    /**
     * Gets the list of user IDs that a specific user is following.
     * Used for feed generation and filtering.
     * 
     * @param userId the ID of the user
     * @return list of user IDs being followed
     */
    public List<String> getFollowingUserIds(String userId) {
        log.debug("Retrieving following user IDs for user ID: {}", userId);
        
        List<Following> followingList = followingRepository.findByFollowerId(userId);
        
        return followingList.stream()
                .map(Following::getFolloweeId)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a user is following another user.
     * 
     * @param followerId the ID of the potential follower
     * @param followeeId the ID of the potential followee
     * @return true if the following relationship exists
     */
    public boolean isFollowing(String followerId, String followeeId) {
        return followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    /**
     * Updates follower and following counts for both users in a relationship.
     * Recalculates counts from the database to ensure accuracy.
     * 
     * @param follower the user who is following
     * @param followee the user being followed
     */
    private void updateFollowerCounts(User follower, User followee) {
        log.debug("Updating follower counts for users {} and {}", follower.getId(), followee.getId());
        
        // Update follower's following count
        long followingCount = followingRepository.countByFollowerId(follower.getId());
        follower.setFollowingCount((int) followingCount);
        follower.setUpdatedAt(Instant.now());
        
        // Update followee's follower count
        long followerCount = followingRepository.countByFolloweeId(followee.getId());
        followee.setFollowerCount((int) followerCount);
        followee.setUpdatedAt(Instant.now());
        
        // Save both users
        userRepository.save(follower);
        userRepository.save(followee);
        
        log.debug("Updated counts - Follower {} following: {}, Followee {} followers: {}", 
                follower.getId(), followingCount, followee.getId(), followerCount);
    }

    /**
     * Maps a User entity to a UserProfileResponse DTO.
     * 
     * @param user the user entity
     * @return user profile response DTO
     */
    private UserProfileResponse mapToUserProfileResponse(User user) {
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
}
