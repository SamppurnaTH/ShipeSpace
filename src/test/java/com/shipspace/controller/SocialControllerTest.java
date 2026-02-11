package com.shipspace.controller;

import com.shipspace.dto.UserProfileResponse;
import com.shipspace.exception.ForbiddenException;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.service.SocialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SocialController.
 * Tests all social following system endpoints.
 */
@WebMvcTest(SocialController.class)
@AutoConfigureMockMvc
@DisplayName("SocialController Integration Tests")
class SocialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SocialService socialService;

    private String currentUserId;
    private String targetUserId;
    private UserProfileResponse followerProfile;
    private UserProfileResponse followingProfile;

    @BeforeEach
    void setUp() {
        currentUserId = "user123";
        targetUserId = "user456";

        followerProfile = UserProfileResponse.builder()
                .id("follower1")
                .username("follower_user")
                .avatarUrl("https://example.com/follower.jpg")
                .bio("Follower bio")
                .location("Follower City")
                .followerCount(5)
                .followingCount(10)
                .projectCount(2)
                .createdAt(Instant.now())
                .build();

        followingProfile = UserProfileResponse.builder()
                .id("following1")
                .username("following_user")
                .avatarUrl("https://example.com/following.jpg")
                .bio("Following bio")
                .location("Following City")
                .followerCount(15)
                .followingCount(8)
                .projectCount(5)
                .createdAt(Instant.now())
                .build();
    }

    // ========== Follow User Tests ==========

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/social/follow/{userId} - Should follow user successfully")
    void followUser_Success() throws Exception {
        // Arrange
        doNothing().when(socialService).followUser(currentUserId, targetUserId);

        // Act & Assert
        mockMvc.perform(post("/api/social/follow/{userId}", targetUserId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(socialService).followUser(currentUserId, targetUserId);
    }

    @Test
    @DisplayName("POST /api/social/follow/{userId} - Should return 401 when not authenticated")
    void followUser_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/social/follow/{userId}", targetUserId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(socialService, never()).followUser(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/social/follow/{userId} - Should return 403 when trying to follow self")
    void followUser_SelfFollow() throws Exception {
        // Arrange
        doThrow(new ForbiddenException("Users cannot follow themselves"))
                .when(socialService).followUser(currentUserId, currentUserId);

        // Act & Assert
        mockMvc.perform(post("/api/social/follow/{userId}", currentUserId)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Users cannot follow themselves"));

        verify(socialService).followUser(currentUserId, currentUserId);
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/social/follow/{userId} - Should return 404 when target user not found")
    void followUser_TargetNotFound() throws Exception {
        // Arrange
        doThrow(new ResourceNotFoundException("User", "nonexistent"))
                .when(socialService).followUser(currentUserId, "nonexistent");

        // Act & Assert
        mockMvc.perform(post("/api/social/follow/{userId}", "nonexistent")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with id: nonexistent"));

        verify(socialService).followUser(currentUserId, "nonexistent");
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("POST /api/social/follow/{userId} - Should be idempotent for duplicate follows")
    void followUser_Idempotent() throws Exception {
        // Arrange - service handles duplicate prevention silently
        doNothing().when(socialService).followUser(currentUserId, targetUserId);

        // Act & Assert - first follow
        mockMvc.perform(post("/api/social/follow/{userId}", targetUserId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Act & Assert - second follow (should also succeed)
        mockMvc.perform(post("/api/social/follow/{userId}", targetUserId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(socialService, times(2)).followUser(currentUserId, targetUserId);
    }

    // ========== Unfollow User Tests ==========

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("DELETE /api/social/follow/{userId} - Should unfollow user successfully")
    void unfollowUser_Success() throws Exception {
        // Arrange
        doNothing().when(socialService).unfollowUser(currentUserId, targetUserId);

        // Act & Assert
        mockMvc.perform(delete("/api/social/follow/{userId}", targetUserId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(socialService).unfollowUser(currentUserId, targetUserId);
    }

    @Test
    @DisplayName("DELETE /api/social/follow/{userId} - Should return 401 when not authenticated")
    void unfollowUser_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/social/follow/{userId}", targetUserId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(socialService, never()).unfollowUser(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("DELETE /api/social/follow/{userId} - Should return 404 when target user not found")
    void unfollowUser_TargetNotFound() throws Exception {
        // Arrange
        doThrow(new ResourceNotFoundException("User", "nonexistent"))
                .when(socialService).unfollowUser(currentUserId, "nonexistent");

        // Act & Assert
        mockMvc.perform(delete("/api/social/follow/{userId}", "nonexistent")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with id: nonexistent"));

        verify(socialService).unfollowUser(currentUserId, "nonexistent");
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("DELETE /api/social/follow/{userId} - Should be idempotent for non-existent follows")
    void unfollowUser_Idempotent() throws Exception {
        // Arrange - service handles non-existent relationship silently
        doNothing().when(socialService).unfollowUser(currentUserId, targetUserId);

        // Act & Assert - first unfollow
        mockMvc.perform(delete("/api/social/follow/{userId}", targetUserId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Act & Assert - second unfollow (should also succeed)
        mockMvc.perform(delete("/api/social/follow/{userId}", targetUserId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(socialService, times(2)).unfollowUser(currentUserId, targetUserId);
    }

    // ========== Get Followers Tests ==========

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/followers - Should return paginated followers")
    void getFollowers_Success() throws Exception {
        // Arrange
        List<UserProfileResponse> followers = Arrays.asList(followerProfile);
        Page<UserProfileResponse> followersPage = new PageImpl<>(followers, PageRequest.of(0, 20), 1);

        when(socialService.getFollowers(eq(currentUserId), any(Pageable.class)))
                .thenReturn(followersPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("follower1"))
                .andExpect(jsonPath("$.content[0].username").value("follower_user"))
                .andExpect(jsonPath("$.content[0].bio").value("Follower bio"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(socialService).getFollowers(eq(currentUserId), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/followers - Should support custom pagination parameters")
    void getFollowers_CustomPagination() throws Exception {
        // Arrange
        List<UserProfileResponse> followers = Arrays.asList(followerProfile);
        Page<UserProfileResponse> followersPage = new PageImpl<>(followers, PageRequest.of(1, 10), 15);

        when(socialService.getFollowers(eq(currentUserId), any(Pageable.class)))
                .thenReturn(followersPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/followers")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(10));

        verify(socialService).getFollowers(eq(currentUserId), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/followers - Should support sorting parameters")
    void getFollowers_WithSorting() throws Exception {
        // Arrange
        List<UserProfileResponse> followers = Arrays.asList(followerProfile);
        Page<UserProfileResponse> followersPage = new PageImpl<>(followers, PageRequest.of(0, 20), 1);

        when(socialService.getFollowers(eq(currentUserId), any(Pageable.class)))
                .thenReturn(followersPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/followers")
                        .param("sort", "username")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        verify(socialService).getFollowers(eq(currentUserId), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/followers - Should return empty page when no followers")
    void getFollowers_EmptyList() throws Exception {
        // Arrange
        Page<UserProfileResponse> emptyPage = new PageImpl<>(Arrays.asList(), PageRequest.of(0, 20), 0);

        when(socialService.getFollowers(eq(currentUserId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(socialService).getFollowers(eq(currentUserId), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/social/followers - Should return 401 when not authenticated")
    void getFollowers_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/social/followers"))
                .andExpect(status().isUnauthorized());

        verify(socialService, never()).getFollowers(anyString(), any(Pageable.class));
    }

    // ========== Get Following Tests ==========

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/following - Should return paginated following list")
    void getFollowing_Success() throws Exception {
        // Arrange
        List<UserProfileResponse> following = Arrays.asList(followingProfile);
        Page<UserProfileResponse> followingPage = new PageImpl<>(following, PageRequest.of(0, 20), 1);

        when(socialService.getFollowing(eq(currentUserId), any(Pageable.class)))
                .thenReturn(followingPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("following1"))
                .andExpect(jsonPath("$.content[0].username").value("following_user"))
                .andExpect(jsonPath("$.content[0].bio").value("Following bio"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(socialService).getFollowing(eq(currentUserId), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/following - Should support custom pagination parameters")
    void getFollowing_CustomPagination() throws Exception {
        // Arrange
        List<UserProfileResponse> following = Arrays.asList(followingProfile);
        Page<UserProfileResponse> followingPage = new PageImpl<>(following, PageRequest.of(2, 5), 12);

        when(socialService.getFollowing(eq(currentUserId), any(Pageable.class)))
                .thenReturn(followingPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/following")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(socialService).getFollowing(eq(currentUserId), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/following - Should support sorting parameters")
    void getFollowing_WithSorting() throws Exception {
        // Arrange
        List<UserProfileResponse> following = Arrays.asList(followingProfile);
        Page<UserProfileResponse> followingPage = new PageImpl<>(following, PageRequest.of(0, 20), 1);

        when(socialService.getFollowing(eq(currentUserId), any(Pageable.class)))
                .thenReturn(followingPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/following")
                        .param("sort", "username")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        verify(socialService).getFollowing(eq(currentUserId), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/following - Should return empty page when not following anyone")
    void getFollowing_EmptyList() throws Exception {
        // Arrange
        Page<UserProfileResponse> emptyPage = new PageImpl<>(Arrays.asList(), PageRequest.of(0, 20), 0);

        when(socialService.getFollowing(eq(currentUserId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(socialService).getFollowing(eq(currentUserId), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/social/following - Should return 401 when not authenticated")
    void getFollowing_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/social/following"))
                .andExpect(status().isUnauthorized());

        verify(socialService, never()).getFollowing(anyString(), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/social/following - Should handle multiple pages correctly")
    void getFollowing_MultiplePagesNavigation() throws Exception {
        // Arrange - simulate page 1 of 3
        List<UserProfileResponse> following = Arrays.asList(followingProfile);
        Page<UserProfileResponse> followingPage = new PageImpl<>(following, PageRequest.of(1, 10), 25);

        when(socialService.getFollowing(eq(currentUserId), any(Pageable.class)))
                .thenReturn(followingPage);

        // Act & Assert
        mockMvc.perform(get("/api/social/following")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));

        verify(socialService).getFollowing(eq(currentUserId), any(Pageable.class));
    }
}
