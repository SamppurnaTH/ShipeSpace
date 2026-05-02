package com.shipspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserActivityResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.dto.UserUpdateRequest;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
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
 * Integration tests for UserController.
 * Tests all user profile management endpoints.
 */
@WebMvcTest(controllers = UserController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class
    },
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.shipspace.config.MongoAuditingConfig.class))
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.shipspace.config.MockMvcContextPathCustomizer.class)
@DisplayName("UserController Integration Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private com.shipspace.security.TokenBlacklistService tokenBlacklistService;

    @MockBean
    private com.shipspace.util.JwtUtil jwtUtil;

    private UserProfileResponse testProfile;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        
        testProfile = UserProfileResponse.builder()
                .id(testUserId)
                .username("testuser")
                .avatarUrl("https://example.com/avatar.jpg")
                .bio("Test bio")
                .location("Test City")
                .websiteUrl("https://example.com")
                .followerCount(10)
                .followingCount(5)
                .projectCount(3)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/users/{userId} - Should return user profile")
    void getUserProfile_Success() throws Exception {
        // Arrange
        when(userService.getPublicProfile(testUserId)).thenReturn(testProfile);

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}", testUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testUserId))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.bio").value("Test bio"))
                .andExpect(jsonPath("$.location").value("Test City"))
                .andExpect(jsonPath("$.followerCount").value(10))
                .andExpect(jsonPath("$.followingCount").value(5))
                .andExpect(jsonPath("$.projectCount").value(3));

        verify(userService).getPublicProfile(testUserId);
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/users/{userId} - Should return 404 when user not found")
    void getUserProfile_NotFound() throws Exception {
        // Arrange
        when(userService.getPublicProfile(anyString()))
                .thenThrow(new ResourceNotFoundException("User", "nonexistent"));

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with id: nonexistent"));

        verify(userService).getPublicProfile("nonexistent");
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/users/me - Should update current user profile")
    void updateCurrentUserProfile_Success() throws Exception {
        // Arrange
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .bio("Updated bio")
                .location("New City")
                .build();

        CurrentUserResponse updatedUser = CurrentUserResponse.builder()
                .id(testUserId)
                .username("testuser")
                .bio("Updated bio")
                .location("New City")
                .build();

        when(userService.updateUserProfile(eq(testUserId), any(UserUpdateRequest.class)))
                .thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Updated bio"))
                .andExpect(jsonPath("$.location").value("New City"));

        verify(userService).updateUserProfile(eq(testUserId), any(UserUpdateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/users/me - Should return 401 when not authenticated")
    void updateCurrentUserProfile_Unauthorized() throws Exception {
        // Arrange
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .bio("Updated bio")
                .build();

        // Act & Assert
        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).updateUserProfile(anyString(), any(UserUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("PUT /api/users/me - Should validate email format")
    void updateCurrentUserProfile_InvalidEmail() throws Exception {
        // Arrange
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .email("invalid-email")
                .build();

        // Act & Assert
        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(userService, never()).updateUserProfile(anyString(), any(UserUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/users/me/projects - Should return current user's projects")
    void getCurrentUserProjects_Success() throws Exception {
        // Arrange
        List<ProjectResponse> projects = Arrays.asList(
                ProjectResponse.builder().id("proj1").userId(testUserId).name("Project 1").build(),
                ProjectResponse.builder().id("proj2").userId(testUserId).name("Project 2").build()
        );

        Page<ProjectResponse> projectPage = new PageImpl<>(projects);
        when(userService.getUserProjects(eq(testUserId), any(Pageable.class))).thenReturn(projectPage);

        // Act & Assert
        mockMvc.perform(get("/api/users/me/projects"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value("proj1"))
                .andExpect(jsonPath("$.content[0].name").value("Project 1"))
                .andExpect(jsonPath("$.content[1].id").value("proj2"))
                .andExpect(jsonPath("$.content[1].name").value("Project 2"));

        verify(userService).getUserProjects(eq(testUserId), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/users/me/projects - Should return 401 when not authenticated")
    void getCurrentUserProjects_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/users/me/projects"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getUserProjects(anyString());
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/users/me/activity - Should return current user's activity")
    void getCurrentUserActivity_Success() throws Exception {
        // Arrange
        List<ShipResponse> recentShips = Arrays.asList(
                ShipResponse.builder()
                        .id("ship1")
                        .userId(testUserId)
                        .content("Ship 1")
                        .likeCount(5)
                        .commentCount(3)
                        .build(),
                ShipResponse.builder()
                        .id("ship2")
                        .userId(testUserId)
                        .content("Ship 2")
                        .likeCount(10)
                        .commentCount(2)
                        .build()
        );

        UserActivityResponse activity = UserActivityResponse.builder()
                .recentShips(recentShips)
                .totalShips(15L)
                .totalEngagement(20L)
                .build();

        when(userService.getUserActivity(testUserId)).thenReturn(activity);

        // Act & Assert
        mockMvc.perform(get("/api/users/me/activity"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.recentShips", hasSize(2)))
                .andExpect(jsonPath("$.totalShips").value(15))
                .andExpect(jsonPath("$.totalEngagement").value(20))
                .andExpect(jsonPath("$.recentShips[0].id").value("ship1"))
                .andExpect(jsonPath("$.recentShips[0].content").value("Ship 1"))
                .andExpect(jsonPath("$.recentShips[1].id").value("ship2"));

        verify(userService).getUserActivity(testUserId);
    }

    @Test
    @DisplayName("GET /api/users/me/activity - Should return 401 when not authenticated")
    void getCurrentUserActivity_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/users/me/activity"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getUserActivity(anyString());
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/users/me/projects - Should return empty list when no projects")
    void getCurrentUserProjects_EmptyList() throws Exception {
        // Arrange
        when(userService.getUserProjects(eq(testUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Arrays.asList()));

        // Act & Assert
        mockMvc.perform(get("/api/users/me/projects"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(0)));

        verify(userService).getUserProjects(eq(testUserId), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user123")
    @DisplayName("GET /api/users/me/activity - Should return empty activity when no ships")
    void getCurrentUserActivity_EmptyActivity() throws Exception {
        // Arrange
        UserActivityResponse emptyActivity = UserActivityResponse.builder()
                .recentShips(Arrays.asList())
                .totalShips(0L)
                .totalEngagement(0L)
                .build();

        when(userService.getUserActivity(testUserId)).thenReturn(emptyActivity);

        // Act & Assert
        mockMvc.perform(get("/api/users/me/activity"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.recentShips", hasSize(0)))
                .andExpect(jsonPath("$.totalShips").value(0))
                .andExpect(jsonPath("$.totalEngagement").value(0));

        verify(userService).getUserActivity(testUserId);
    }
}
