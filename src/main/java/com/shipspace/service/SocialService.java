package com.shipspace.service;

import com.shipspace.dto.UserProfileResponse;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Following;
import com.shipspace.model.User;
import com.shipspace.repository.FollowingRepository;
import com.shipspace.repository.UserCountRepository;
import com.shipspace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    private final UserCountRepository userCountRepository;
    private final NotificationDispatchService notificationDispatchService;

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

        if (followerId.equals(followeeId)) {
            throw new ForbiddenException("Users cannot follow themselves");
        }

        // Verify both users exist
        if (!userRepository.existsById(followerId)) {
            throw new ResourceNotFoundException("User", followerId);
        }
        if (!userRepository.existsById(followeeId)) {
            throw new ResourceNotFoundException("User", followeeId);
        }

        if (followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.debug("Following relationship already exists between {} and {}", followerId, followeeId);
            return;
        }

        try {
            followingRepository.save(Following.builder()
                    .followerId(followerId)
                    .followeeId(followeeId)
                    .createdAt(Instant.now())
                    .build());
        } catch (DuplicateKeyException e) {
            log.debug("Concurrent duplicate follow ignored for {} -> {}", followerId, followeeId);
            return;
        }
        log.info("Created following relationship: {} -> {}", followerId, followeeId);

        // Atomic increments — no read-modify-write race
        userCountRepository.incrementFollowingCount(followerId, 1);
        userCountRepository.incrementFollowerCount(followeeId, 1);

        // Notify followee asynchronously
        User follower = userRepository.findById(followerId).orElse(null);
        notificationDispatchService.dispatch(followeeId, "FOLLOW",
                (follower != null ? follower.getUsername() : "Someone") + " started following you",
                followerId,
                follower != null ? follower.getAvatarUrl() : null,
                followerId, "/profile/" + followerId);
    }

    @Transactional
    public void unfollowUser(String followerId, String followeeId) {
        log.info("User {} attempting to unfollow user {}", followerId, followeeId);

        if (!userRepository.existsById(followerId)) {
            throw new ResourceNotFoundException("User", followerId);
        }
        if (!userRepository.existsById(followeeId)) {
            throw new ResourceNotFoundException("User", followeeId);
        }

        if (!followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            log.debug("No following relationship exists between {} and {}", followerId, followeeId);
            return;
        }

        long deleted = followingRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        if (deleted == 0) {
            log.debug("Concurrent unfollow already removed relationship {} -> {}", followerId, followeeId);
            return;
        }
        log.info("Deleted following relationship: {} -> {}", followerId, followeeId);

        // Atomic decrements — floor at 0 is enforced by the DB constraint (counts never go negative)
        userCountRepository.incrementFollowingCount(followerId, -1);
        userCountRepository.incrementFollowerCount(followeeId, -1);
    }

    /**
     * Retrieves a paginated list of users following the specified user.
     * Uses a single bulk findAllById instead of N individual lookups.
     */
    public Page<UserProfileResponse> getFollowers(String userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        Page<Following> followingPage = followingRepository.findByFolloweeId(userId, pageable);

        // Collect all follower IDs from this page, then fetch in one query
        Set<String> followerIds = followingPage.getContent().stream()
                .map(Following::getFollowerId)
                .collect(Collectors.toSet());

        Map<String, User> userMap = userRepository.findAllById(followerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<UserProfileResponse> profiles = followingPage.getContent().stream()
                .map(f -> {
                    User u = userMap.get(f.getFollowerId());
                    if (u == null) {
                        log.warn("Follower user {} not found", f.getFollowerId());
                        return null;
                    }
                    return mapToUserProfileResponse(u);
                })
                .filter(p -> p != null)
                .collect(Collectors.toList());

        return new PageImpl<>(profiles, pageable, followingPage.getTotalElements());
    }

    /**
     * Retrieves a paginated list of users that the specified user is following.
     * Uses a single bulk findAllById instead of N individual lookups.
     */
    public Page<UserProfileResponse> getFollowing(String userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        Page<Following> followingPage = followingRepository.findByFollowerId(userId, pageable);

        Set<String> followeeIds = followingPage.getContent().stream()
                .map(Following::getFolloweeId)
                .collect(Collectors.toSet());

        Map<String, User> userMap = userRepository.findAllById(followeeIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<UserProfileResponse> profiles = followingPage.getContent().stream()
                .map(f -> {
                    User u = userMap.get(f.getFolloweeId());
                    if (u == null) {
                        log.warn("Followee user {} not found", f.getFolloweeId());
                        return null;
                    }
                    return mapToUserProfileResponse(u);
                })
                .filter(p -> p != null)
                .collect(Collectors.toList());

        return new PageImpl<>(profiles, pageable, followingPage.getTotalElements());
    }

    /**
     * Gets the list of user IDs that a specific user is following.
     * Capped at 1000 to prevent OOM on power users. Feed generation uses
     * the paginated FeedService path which caps at 500 anyway.
     */
    public List<String> getFollowingUserIds(String userId) {
        log.debug("Retrieving following user IDs for user ID: {}", userId);
        org.springframework.data.domain.Pageable cap =
                org.springframework.data.domain.PageRequest.of(0, 1000,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return followingRepository.findByFollowerId(userId, cap)
                .getContent()
                .stream()
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

    @Async("taskExecutor")
    protected void notifyAsync(String recipientId, String type, String text,
                               String actorId, String actorAvatarUrl,
                               String targetId, String targetView) {
        try {
            notificationDispatchService.dispatch(recipientId, type, text,
                    actorId, actorAvatarUrl, targetId, targetView);
        } catch (Exception e) {
            log.error("Failed to create {} notification for user {}: {}", type, recipientId, e.getMessage());
        }
    }
}
