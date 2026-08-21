package com.shipspace.service;

import com.shipspace.dto.UserProfileResponse;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Following;
import com.shipspace.model.User;
import com.shipspace.repository.FollowingRepository;
import com.shipspace.repository.UserCountRepository;
import com.shipspace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SocialService.
 * Tests follow/unfollow operations, follower/following list retrieval, and duplicate prevention.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SocialService Tests")
class SocialServiceTest {

    @Mock
    private FollowingRepository followingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCountRepository userCountRepository;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private SocialService socialService;

    private User followerUser;
    private User followeeUser;
    private String followerId;
    private String followeeId;

    @BeforeEach
    void setUp() {
        followerId = "user123";
        followeeId = "user456";

        followerUser = User.builder()
                .id(followerId)
                .githubId("github123")
                .username("follower")
                .email("follower@example.com")
                .followerCount(5)
                .followingCount(10)
                .projectCount(3)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        followeeUser = User.builder()
                .id(followeeId)
                .githubId("github456")
                .username("followee")
                .email("followee@example.com")
                .followerCount(20)
                .followingCount(15)
                .projectCount(5)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        lenient().when(userRepository.existsById(followerId)).thenReturn(true);
        lenient().when(userRepository.existsById(followeeId)).thenReturn(true);
    }

    @Test
    @DisplayName("Should successfully create a following relationship")
    void followUser_Success() {
        // Arrange
        when(userRepository.findById(followerId)).thenReturn(Optional.of(followerUser));
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(false);
        when(followingRepository.save(any(Following.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        socialService.followUser(followerId, followeeId);

        // Assert
        verify(userRepository).existsById(followerId);
        verify(userRepository).existsById(followeeId);
        verify(userRepository).findById(followerId);
        verify(followingRepository).existsByFollowerIdAndFolloweeId(followerId, followeeId);
        verify(followingRepository).save(any(Following.class));
        verify(userCountRepository).incrementFollowingCount(followerId, 1);
        verify(userCountRepository).incrementFollowerCount(followeeId, 1);
        verify(notificationDispatchService).dispatch(eq(followeeId), eq("FOLLOW"), anyString(),
                eq(followerId), any(), eq(followerId), eq("/profile/" + followerId));
    }

    @Test
    @DisplayName("Should prevent user from following themselves")
    void followUser_SelfFollow_ThrowsForbiddenException() {
        // Arrange
        String userId = "user123";

        // Act & Assert
        assertThatThrownBy(() -> socialService.followUser(userId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Users cannot follow themselves");

        verify(userRepository, never()).findById(anyString());
        verify(followingRepository, never()).save(any(Following.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when follower user not found")
    void followUser_FollowerNotFound_ThrowsException() {
        // Arrange
        when(userRepository.existsById(followerId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> socialService.followUser(followerId, followeeId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).existsById(followerId);
        verify(followingRepository, never()).save(any(Following.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when followee user not found")
    void followUser_FolloweeNotFound_ThrowsException() {
        // Arrange
        when(userRepository.existsById(followeeId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> socialService.followUser(followerId, followeeId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).existsById(followerId);
        verify(userRepository).existsById(followeeId);
        verify(followingRepository, never()).save(any(Following.class));
    }

    @Test
    @DisplayName("Should handle duplicate follow attempts gracefully (idempotent)")
    void followUser_DuplicateFollow_Idempotent() {
        // Arrange
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(true);

        // Act
        socialService.followUser(followerId, followeeId);

        // Assert
        verify(userRepository).existsById(followerId);
        verify(userRepository).existsById(followeeId);
        verify(followingRepository).existsByFollowerIdAndFolloweeId(followerId, followeeId);
        verify(followingRepository, never()).save(any(Following.class));
        verify(userCountRepository, never()).incrementFollowingCount(anyString(), anyInt());
        verify(userCountRepository, never()).incrementFollowerCount(anyString(), anyInt());
    }

    @Test
    @DisplayName("Should successfully remove a following relationship")
    void unfollowUser_Success() {
        // Arrange
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(true);
        when(followingRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(1L);

        // Act
        socialService.unfollowUser(followerId, followeeId);

        // Assert
        verify(userRepository).existsById(followerId);
        verify(userRepository).existsById(followeeId);
        verify(followingRepository).existsByFollowerIdAndFolloweeId(followerId, followeeId);
        verify(followingRepository).deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        verify(userCountRepository).incrementFollowingCount(followerId, -1);
        verify(userCountRepository).incrementFollowerCount(followeeId, -1);
    }

    @Test
    @DisplayName("Should handle concurrent duplicate follow without count drift")
    void followUser_ConcurrentDuplicateFollow_Idempotent() {
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(false);
        when(followingRepository.save(any(Following.class))).thenThrow(new DuplicateKeyException("duplicate"));

        socialService.followUser(followerId, followeeId);

        verify(userCountRepository, never()).incrementFollowingCount(anyString(), anyInt());
        verify(userCountRepository, never()).incrementFollowerCount(anyString(), anyInt());
        verify(notificationDispatchService, never()).dispatch(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle concurrent unfollow without count drift")
    void unfollowUser_ConcurrentDelete_Idempotent() {
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(true);
        when(followingRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(0L);

        socialService.unfollowUser(followerId, followeeId);

        verify(userCountRepository, never()).incrementFollowingCount(anyString(), anyInt());
        verify(userCountRepository, never()).incrementFollowerCount(anyString(), anyInt());
    }

    @Test
    @DisplayName("Should handle unfollow when relationship doesn't exist (idempotent)")
    void unfollowUser_NoRelationship_Idempotent() {
        // Arrange
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(false);

        // Act
        socialService.unfollowUser(followerId, followeeId);

        // Assert
        verify(userRepository).existsById(followerId);
        verify(userRepository).existsById(followeeId);
        verify(followingRepository).existsByFollowerIdAndFolloweeId(followerId, followeeId);
        verify(followingRepository, never()).deleteByFollowerIdAndFolloweeId(anyString(), anyString());
        verify(userCountRepository, never()).incrementFollowingCount(anyString(), anyInt());
        verify(userCountRepository, never()).incrementFollowerCount(anyString(), anyInt());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when unfollowing non-existent user")
    void unfollowUser_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.existsById(followeeId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> socialService.unfollowUser(followerId, followeeId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).existsById(followerId);
        verify(userRepository).existsById(followeeId);
        verify(followingRepository, never()).deleteByFollowerIdAndFolloweeId(anyString(), anyString());
    }

    @Test
    @DisplayName("Should retrieve paginated list of followers")
    void getFollowers_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        
        Following following1 = Following.builder()
                .id("follow1")
                .followerId("user789")
                .followeeId(followeeId)
                .createdAt(Instant.now())
                .build();
        
        Following following2 = Following.builder()
                .id("follow2")
                .followerId("user101")
                .followeeId(followeeId)
                .createdAt(Instant.now())
                .build();

        User follower1 = User.builder()
                .id("user789")
                .username("follower1")
                .avatarUrl("https://example.com/avatar1.jpg")
                .followerCount(5)
                .followingCount(10)
                .projectCount(2)
                .createdAt(Instant.now())
                .build();

        User follower2 = User.builder()
                .id("user101")
                .username("follower2")
                .avatarUrl("https://example.com/avatar2.jpg")
                .followerCount(8)
                .followingCount(12)
                .projectCount(4)
                .createdAt(Instant.now())
                .build();

        Page<Following> followingPage = new PageImpl<>(Arrays.asList(following1, following2), pageable, 2);

        when(userRepository.existsById(followeeId)).thenReturn(true);
        when(followingRepository.findByFolloweeId(followeeId, pageable)).thenReturn(followingPage);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(follower1, follower2));

        // Act
        Page<UserProfileResponse> result = socialService.getFollowers(followeeId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("follower1");
        assertThat(result.getContent().get(1).getUsername()).isEqualTo("follower2");
        verify(userRepository).existsById(followeeId);
        verify(followingRepository).findByFolloweeId(followeeId, pageable);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when getting followers for non-existent user")
    void getFollowers_UserNotFound_ThrowsException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.existsById(followeeId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> socialService.getFollowers(followeeId, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).existsById(followeeId);
        verify(followingRepository, never()).findByFolloweeId(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("Should retrieve paginated list of following")
    void getFollowing_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        
        Following following1 = Following.builder()
                .id("follow1")
                .followerId(followerId)
                .followeeId("user789")
                .createdAt(Instant.now())
                .build();
        
        Following following2 = Following.builder()
                .id("follow2")
                .followerId(followerId)
                .followeeId("user101")
                .createdAt(Instant.now())
                .build();

        User followee1 = User.builder()
                .id("user789")
                .username("followee1")
                .avatarUrl("https://example.com/avatar1.jpg")
                .followerCount(15)
                .followingCount(20)
                .projectCount(6)
                .createdAt(Instant.now())
                .build();

        User followee2 = User.builder()
                .id("user101")
                .username("followee2")
                .avatarUrl("https://example.com/avatar2.jpg")
                .followerCount(25)
                .followingCount(30)
                .projectCount(8)
                .createdAt(Instant.now())
                .build();

        Page<Following> followingPage = new PageImpl<>(Arrays.asList(following1, following2), pageable, 2);

        when(userRepository.existsById(followerId)).thenReturn(true);
        when(followingRepository.findByFollowerId(followerId, pageable)).thenReturn(followingPage);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(followee1, followee2));

        // Act
        Page<UserProfileResponse> result = socialService.getFollowing(followerId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("followee1");
        assertThat(result.getContent().get(1).getUsername()).isEqualTo("followee2");
        verify(userRepository).existsById(followerId);
        verify(followingRepository).findByFollowerId(followerId, pageable);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when getting following for non-existent user")
    void getFollowing_UserNotFound_ThrowsException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.existsById(followerId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> socialService.getFollowing(followerId, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).existsById(followerId);
        verify(followingRepository, never()).findByFollowerId(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("Should retrieve list of following user IDs")
    void getFollowingUserIds_Success() {
        // Arrange
        Following following1 = Following.builder()
                .id("follow1")
                .followerId(followerId)
                .followeeId("user789")
                .build();
        
        Following following2 = Following.builder()
                .id("follow2")
                .followerId(followerId)
                .followeeId("user101")
                .build();

        List<Following> followingList = Arrays.asList(following1, following2);

        when(followingRepository.findByFollowerId(eq(followerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(followingList));

        // Act
        List<String> result = socialService.getFollowingUserIds(followerId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly("user789", "user101");
        verify(followingRepository).findByFollowerId(eq(followerId), any(Pageable.class));
    }

    @Test
    @DisplayName("Should return empty list when user is not following anyone")
    void getFollowingUserIds_EmptyList() {
        // Arrange
        when(followingRepository.findByFollowerId(eq(followerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Arrays.asList()));

        // Act
        List<String> result = socialService.getFollowingUserIds(followerId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(followingRepository).findByFollowerId(eq(followerId), any(Pageable.class));
    }

    @Test
    @DisplayName("Should check if user is following another user")
    void isFollowing_ReturnsTrue() {
        // Arrange
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(true);

        // Act
        boolean result = socialService.isFollowing(followerId, followeeId);

        // Assert
        assertThat(result).isTrue();
        verify(followingRepository).existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Test
    @DisplayName("Should check if user is not following another user")
    void isFollowing_ReturnsFalse() {
        // Arrange
        when(followingRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(false);

        // Act
        boolean result = socialService.isFollowing(followerId, followeeId);

        // Assert
        assertThat(result).isFalse();
        verify(followingRepository).existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Test
    @DisplayName("Should handle empty followers list")
    void getFollowers_EmptyList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Following> emptyPage = new PageImpl<>(Arrays.asList(), pageable, 0);

        when(userRepository.existsById(followeeId)).thenReturn(true);
        when(followingRepository.findByFolloweeId(followeeId, pageable)).thenReturn(emptyPage);

        // Act
        Page<UserProfileResponse> result = socialService.getFollowers(followeeId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        verify(userRepository).existsById(followeeId);
        verify(followingRepository).findByFolloweeId(followeeId, pageable);
    }

    @Test
    @DisplayName("Should handle empty following list")
    void getFollowing_EmptyList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Following> emptyPage = new PageImpl<>(Arrays.asList(), pageable, 0);

        when(userRepository.existsById(followerId)).thenReturn(true);
        when(followingRepository.findByFollowerId(followerId, pageable)).thenReturn(emptyPage);

        // Act
        Page<UserProfileResponse> result = socialService.getFollowing(followerId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        verify(userRepository).existsById(followerId);
        verify(followingRepository).findByFollowerId(followerId, pageable);
    }
}
