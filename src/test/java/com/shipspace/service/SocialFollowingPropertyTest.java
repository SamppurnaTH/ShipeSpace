package com.shipspace.service;

import com.shipspace.dto.UserProfileResponse;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Following;
import com.shipspace.model.User;
import com.shipspace.repository.FollowingRepository;
import com.shipspace.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import com.shipspace.repository.UserCountRepository;
import com.shipspace.service.NotificationDispatchService;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for social following features.
 * 
 * **Validates: Requirements 5.1, 5.2, 5.4, 5.5**
 * 
 * These tests verify the correctness properties of the social following system
 * across a wide range of inputs using property-based testing with jqwik.
 */
class SocialFollowingPropertyTest {

    private SocialService socialService;
    private FollowingRepository followingRepository;
    private UserRepository userRepository;

    @BeforeTry
    void setUp() {
        followingRepository = mock(FollowingRepository.class);
        userRepository = mock(UserRepository.class);
        UserCountRepository userCountRepository = mock(UserCountRepository.class);
        NotificationDispatchService notificationDispatchService = mock(NotificationDispatchService.class);
        socialService = new SocialService(followingRepository, userRepository, userCountRepository, notificationDispatchService);
    }

    /**
     * Property 19: Following relationship management
     * 
     * For any follow/unfollow request between two users, the following relationship 
     * should be created/removed in MongoDB and follower counts should be updated accordingly.
     * 
     * **Validates: Requirements 5.1, 5.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 19: Following relationship management - Follow creates relationship")
    void followCreatesRelationship(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followerId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followeeId) {
        
        Assume.that(!followerId.equals(followeeId)); // Prevent self-follow for this test
        
        // Given: Both users exist and no relationship exists
        User follower = createMockUser(followerId, "follower_" + followerId);
        User followee = createMockUser(followeeId, "followee_" + followeeId);
        
        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(false);
        when(followingRepository.save(any(Following.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(followingRepository.countByFollowerId(followerId)).thenReturn(1L);
        when(followingRepository.countByFolloweeId(followeeId)).thenReturn(1L);

        // When: User follows another user
        socialService.followUser(followerId, followeeId);

        // Then: Following relationship should be created
        verify(followingRepository).save(argThat(following -> 
            following.getFollowerId().equals(followerId) && 
            following.getFolloweeId().equals(followeeId) &&
            following.getCreatedAt() != null
        ));
        
        // And: Follower counts should be updated
        verify(userRepository, times(2)).save(any(User.class));
        verify(followingRepository).countByFollowerId(followerId);
        verify(followingRepository).countByFolloweeId(followeeId);
    }

    /**
     * Property 19 (variant): Unfollow removes relationship
     * 
     * For any unfollow request, the following relationship should be removed 
     * and follower counts should be updated.
     * 
     * **Validates: Requirements 5.1, 5.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 19: Following relationship management - Unfollow removes relationship")
    void unfollowRemovesRelationship(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followerId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followeeId) {
        
        Assume.that(!followerId.equals(followeeId)); // Prevent self-follow for this test
        
        // Given: Both users exist and relationship exists
        User follower = createMockUser(followerId, "follower_" + followerId);
        User followee = createMockUser(followeeId, "followee_" + followeeId);
        
        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(true);
        when(followingRepository.countByFollowerId(followerId)).thenReturn(0L);
        when(followingRepository.countByFolloweeId(followeeId)).thenReturn(0L);

        // When: User unfollows another user
        socialService.unfollowUser(followerId, followeeId);

        // Then: Following relationship should be deleted
        verify(followingRepository).deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        
        // And: Follower counts should be updated
        verify(userRepository, times(2)).save(any(User.class));
        verify(followingRepository).countByFollowerId(followerId);
        verify(followingRepository).countByFolloweeId(followeeId);
    }

    /**
     * Property 19 (variant): Follow is idempotent
     * 
     * Following the same user multiple times should not create duplicate relationships.
     * 
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 19: Following relationship management - Follow is idempotent")
    void followIsIdempotent(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followerId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followeeId) {
        
        Assume.that(!followerId.equals(followeeId));
        
        // Given: Both users exist and relationship already exists
        User follower = createMockUser(followerId, "follower_" + followerId);
        User followee = createMockUser(followeeId, "followee_" + followeeId);
        
        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(true);

        // When: User tries to follow again
        socialService.followUser(followerId, followeeId);

        // Then: No new relationship should be created
        verify(followingRepository, never()).save(any(Following.class));
        
        // And: Counts should not be updated
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * Property 19 (variant): Unfollow is idempotent
     * 
     * Unfollowing a user that is not followed should not cause errors.
     * 
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 19: Following relationship management - Unfollow is idempotent")
    void unfollowIsIdempotent(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followerId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followeeId) {
        
        Assume.that(!followerId.equals(followeeId));
        
        // Given: Both users exist but no relationship exists
        User follower = createMockUser(followerId, "follower_" + followerId);
        User followee = createMockUser(followeeId, "followee_" + followeeId);
        
        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(false);

        // When: User tries to unfollow
        socialService.unfollowUser(followerId, followeeId);

        // Then: No deletion should be attempted
        verify(followingRepository, never()).deleteByFollowerIdAndFolloweeId(anyString(), anyString());
        
        // And: Counts should not be updated
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * Property 19 (variant): Follower counts are accurate
     * 
     * After follow/unfollow operations, follower counts should reflect actual relationships.
     * 
     * **Validates: Requirements 5.1, 5.2**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 19: Following relationship management - Follower counts are accurate")
    void followerCountsAreAccurate(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followerId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String followeeId,
            @ForAll @IntRange(min = 0, max = 100) int initialFollowerCount,
            @ForAll @IntRange(min = 0, max = 100) int initialFollowingCount) {
        
        Assume.that(!followerId.equals(followeeId));
        
        // Given: Both users exist with initial counts
        User follower = createMockUser(followerId, "follower_" + followerId);
        follower.setFollowingCount(initialFollowingCount);
        User followee = createMockUser(followeeId, "followee_" + followeeId);
        followee.setFollowerCount(initialFollowerCount);
        
        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(false);
        when(followingRepository.save(any(Following.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        long newFollowingCount = initialFollowingCount + 1L;
        long newFollowerCount = initialFollowerCount + 1L;
        when(followingRepository.countByFollowerId(followerId)).thenReturn(newFollowingCount);
        when(followingRepository.countByFolloweeId(followeeId)).thenReturn(newFollowerCount);

        // When: User follows another user
        socialService.followUser(followerId, followeeId);

        // Then: Counts should be updated based on actual database counts
        verify(userRepository).save(argThat(user -> 
            user.getId().equals(followerId) && 
            user.getFollowingCount() == newFollowingCount
        ));
        verify(userRepository).save(argThat(user -> 
            user.getId().equals(followeeId) && 
            user.getFollowerCount() == newFollowerCount
        ));
    }

    /**
     * Property 20: Self-follow prevention
     * 
     * For any user attempting to follow themselves, the request should be rejected 
     * with appropriate error response.
     * 
     * **Validates: Requirements 5.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 20: Self-follow prevention")
    void selfFollowPrevention(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId) {
        
        // Given: A user exists
        User user = createMockUser(userId, "user_" + userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When/Then: User attempts to follow themselves, should throw ForbiddenException
        assertThatThrownBy(() -> socialService.followUser(userId, userId))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("cannot follow themselves");
        
        // And: No relationship should be created
        verify(followingRepository, never()).save(any(Following.class));
        verify(followingRepository, never()).existsByFollowerIdAndFolloweeId(anyString(), anyString());
    }

    /**
     * Property 20 (variant): Self-follow prevention happens before database checks
     * 
     * Self-follow validation should occur before any database operations.
     * 
     * **Validates: Requirements 5.4**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 20: Self-follow prevention - Early validation")
    void selfFollowPreventionEarlyValidation(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId) {
        
        // When/Then: User attempts to follow themselves
        assertThatThrownBy(() -> socialService.followUser(userId, userId))
            .isInstanceOf(ForbiddenException.class);
        
        // And: No database operations should be performed
        verify(userRepository, never()).findById(anyString());
        verify(followingRepository, never()).existsByFollowerIdAndFolloweeId(anyString(), anyString());
        verify(followingRepository, never()).save(any(Following.class));
    }

    /**
     * Property 21: Following list pagination
     * 
     * For any user with following relationships, querying followers/following should 
     * return paginated results with user profile summaries.
     * 
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 21: Following list pagination - Followers")
    void followersListPagination(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @IntRange(min = 0, max = 3) int pageNumber,
            @ForAll @IntRange(min = 1, max = 20) int pageSize,
            @ForAll @IntRange(min = 0, max = 50) int totalFollowers) {
        
        // Given: User exists with followers
        when(userRepository.existsById(userId)).thenReturn(true);
        
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        List<Following> followingList = createFollowingList(totalFollowers, userId, false);
        
        // Calculate expected page content
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, totalFollowers);
        List<Following> pageContent = start < totalFollowers ? 
            followingList.subList(start, end) : new ArrayList<>();
        
        Page<Following> followingPage = new PageImpl<>(pageContent, pageable, totalFollowers);
        when(followingRepository.findByFolloweeId(userId, pageable)).thenReturn(followingPage);
        
        // Mock user repository to return users for each follower
        for (Following following : pageContent) {
            User follower = createMockUser(following.getFollowerId(), "user_" + following.getFollowerId());
            when(userRepository.findById(following.getFollowerId())).thenReturn(Optional.of(follower));
        }

        // When: Requesting followers with pagination
        Page<UserProfileResponse> result = socialService.getFollowers(userId, pageable);

        // Then: Should return paginated results
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(pageContent.size());
        assertThat(result.getTotalElements()).isEqualTo(totalFollowers);
        assertThat(result.getNumber()).isEqualTo(pageNumber);
        assertThat(result.getSize()).isEqualTo(pageSize);
        
        // And: Each result should contain user profile summary
        for (UserProfileResponse profile : result.getContent()) {
            assertThat(profile.getId()).isNotNull();
            assertThat(profile.getUsername()).isNotNull();
            assertThat(profile.getFollowerCount()).isNotNull();
            assertThat(profile.getFollowingCount()).isNotNull();
            assertThat(profile.getProjectCount()).isNotNull();
        }
        
        verify(followingRepository).findByFolloweeId(userId, pageable);
    }

    /**
     * Property 21 (variant): Following list pagination
     * 
     * For any user, querying their following list should return paginated results 
     * with user profile summaries.
     * 
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 21: Following list pagination - Following")
    void followingListPagination(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @IntRange(min = 0, max = 3) int pageNumber,
            @ForAll @IntRange(min = 1, max = 20) int pageSize,
            @ForAll @IntRange(min = 0, max = 50) int totalFollowing) {
        
        // Given: User exists with following relationships
        when(userRepository.existsById(userId)).thenReturn(true);
        
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        List<Following> followingList = createFollowingList(totalFollowing, userId, true);
        
        // Calculate expected page content
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, totalFollowing);
        List<Following> pageContent = start < totalFollowing ? 
            followingList.subList(start, end) : new ArrayList<>();
        
        Page<Following> followingPage = new PageImpl<>(pageContent, pageable, totalFollowing);
        when(followingRepository.findByFollowerId(userId, pageable)).thenReturn(followingPage);
        
        // Mock user repository to return users for each followee
        for (Following following : pageContent) {
            User followee = createMockUser(following.getFolloweeId(), "user_" + following.getFolloweeId());
            when(userRepository.findById(following.getFolloweeId())).thenReturn(Optional.of(followee));
        }

        // When: Requesting following list with pagination
        Page<UserProfileResponse> result = socialService.getFollowing(userId, pageable);

        // Then: Should return paginated results
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(pageContent.size());
        assertThat(result.getTotalElements()).isEqualTo(totalFollowing);
        assertThat(result.getNumber()).isEqualTo(pageNumber);
        assertThat(result.getSize()).isEqualTo(pageSize);
        
        // And: Each result should contain user profile summary
        for (UserProfileResponse profile : result.getContent()) {
            assertThat(profile.getId()).isNotNull();
            assertThat(profile.getUsername()).isNotNull();
            assertThat(profile.getFollowerCount()).isNotNull();
            assertThat(profile.getFollowingCount()).isNotNull();
            assertThat(profile.getProjectCount()).isNotNull();
        }
        
        verify(followingRepository).findByFollowerId(userId, pageable);
    }

    /**
     * Property 21 (variant): Empty following lists
     * 
     * For any user with no followers/following, pagination should return empty results.
     * 
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 21: Following list pagination - Empty lists")
    void emptyFollowingLists(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @IntRange(min = 0, max = 3) int pageNumber,
            @ForAll @IntRange(min = 1, max = 20) int pageSize) {
        
        // Given: User exists with no followers
        when(userRepository.existsById(userId)).thenReturn(true);
        
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Following> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
        when(followingRepository.findByFolloweeId(userId, pageable)).thenReturn(emptyPage);

        // When: Requesting followers
        Page<UserProfileResponse> result = socialService.getFollowers(userId, pageable);

        // Then: Should return empty page
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    /**
     * Property 21 (variant): Non-existent user handling
     * 
     * Requesting followers/following for non-existent users should throw ResourceNotFoundException.
     * 
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    @Label("Feature: shipspace-mvp, Property 21: Following list pagination - Non-existent user")
    void nonExistentUserFollowingList(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String userId,
            @ForAll @IntRange(min = 0, max = 3) int pageNumber,
            @ForAll @IntRange(min = 1, max = 20) int pageSize) {
        
        // Given: User does not exist
        when(userRepository.existsById(userId)).thenReturn(false);
        
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // When/Then: Requesting followers should throw ResourceNotFoundException
        assertThatThrownBy(() -> socialService.getFollowers(userId, pageable))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(userId);
        
        // When/Then: Requesting following should throw ResourceNotFoundException
        assertThatThrownBy(() -> socialService.getFollowing(userId, pageable))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(userId);
    }

    // Helper methods

    private User createMockUser(String userId, String username) {
        return User.builder()
                .id(userId)
                .githubId("github_" + userId)
                .username(username)
                .email(username + "@example.com")
                .avatarUrl("https://example.com/avatar/" + userId)
                .bio("Bio for " + username)
                .location("Location " + userId)
                .websiteUrl("https://example.com/" + username)
                .followerCount(0)
                .followingCount(0)
                .projectCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private List<Following> createFollowingList(int count, String userId, boolean isFollower) {
        List<Following> followingList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Following following = Following.builder()
                    .id("following_" + i)
                    .followerId(isFollower ? userId : "user_" + i)
                    .followeeId(isFollower ? "user_" + i : userId)
                    .createdAt(Instant.now())
                    .build();
            followingList.add(following);
        }
        return followingList;
    }
}
