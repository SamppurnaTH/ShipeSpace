package com.shipspace.controller;

import com.shipspace.dto.FeedItemResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.service.FeedService;
import com.shipspace.service.ProjectService;
import com.shipspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeedController.
 * Tests feed endpoint responses and data enrichment.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedController Tests")
class FeedControllerTest {

    @Mock
    private FeedService feedService;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FeedController feedController;

    private String userId;
    private Ship ship;
    private ProjectResponse projectResponse;
    private User user;

    @BeforeEach
    void setUp() {
        userId = "user123";
        
        ship = Ship.builder()
                .id("ship1")
                .userId(userId)
                .projectId("project1")
                .content("Test ship content")
                .likeCount(10)
                .commentCount(5)
                .trendingScore(5.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        projectResponse = ProjectResponse.builder()
                .id("project1")
                .userId(userId)
                .name("Test Project")
                .description("Test project description")
                .techTags(Arrays.asList("Java", "Spring"))
                .shipCount(5)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        user = User.builder()
                .id(userId)
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .avatarUrl("https://example.com/avatar.jpg")
                .followerCount(10)
                .followingCount(20)
                .projectCount(3)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should get following feed for authenticated user")
    void getFollowingFeed_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship), pageable, 1);

        when(authentication.getName()).thenReturn(userId);
        when(feedService.getFollowingFeed(userId, pageable)).thenReturn(shipsPage);
        when(projectService.getProjectById("project1")).thenReturn(projectResponse);
        when(userService.getUserById(userId)).thenReturn(user);

        // Act
        ResponseEntity<Page<FeedItemResponse>> response = feedController.getFollowingFeed(authentication, 0, 20);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        
        FeedItemResponse feedItem = response.getBody().getContent().get(0);
        assertThat(feedItem.getShip()).isNotNull();
        assertThat(feedItem.getShip().getId()).isEqualTo("ship1");
        assertThat(feedItem.getProject()).isNotNull();
        assertThat(feedItem.getProject().getId()).isEqualTo("project1");
        assertThat(feedItem.getUser()).isNotNull();
        assertThat(feedItem.getUser().getId()).isEqualTo(userId);

        verify(authentication).getName();
        verify(feedService).getFollowingFeed(userId, pageable);
        verify(projectService).getProjectById("project1");
        verify(userService).getUserById(userId);
    }

    @Test
    @DisplayName("Should get trending feed without authentication")
    void getTrendingFeed_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship), pageable, 1);

        when(feedService.getTrendingFeed(pageable)).thenReturn(shipsPage);
        when(projectService.getProjectById("project1")).thenReturn(projectResponse);
        when(userService.getUserById(userId)).thenReturn(user);

        // Act
        ResponseEntity<Page<FeedItemResponse>> response = feedController.getTrendingFeed(0, 20);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        
        FeedItemResponse feedItem = response.getBody().getContent().get(0);
        assertThat(feedItem.getShip()).isNotNull();
        assertThat(feedItem.getShip().getId()).isEqualTo("ship1");
        assertThat(feedItem.getProject()).isNotNull();
        assertThat(feedItem.getProject().getId()).isEqualTo("project1");
        assertThat(feedItem.getUser()).isNotNull();
        assertThat(feedItem.getUser().getId()).isEqualTo(userId);

        verify(feedService).getTrendingFeed(pageable);
        verify(projectService).getProjectById("project1");
        verify(userService).getUserById(userId);
    }

    @Test
    @DisplayName("Should get trending feed with time window")
    void getTrendingFeedWithTimeWindow_Success() {
        // Arrange
        String timeWindow = "24h";
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship), pageable, 1);

        when(feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable)).thenReturn(shipsPage);
        when(projectService.getProjectById("project1")).thenReturn(projectResponse);
        when(userService.getUserById(userId)).thenReturn(user);

        // Act
        ResponseEntity<Page<FeedItemResponse>> response = feedController.getTrendingFeedWithTimeWindow(timeWindow, 0, 20);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        
        FeedItemResponse feedItem = response.getBody().getContent().get(0);
        assertThat(feedItem.getShip()).isNotNull();
        assertThat(feedItem.getShip().getId()).isEqualTo("ship1");

        verify(feedService).getTrendingFeedWithTimeWindow(timeWindow, pageable);
        verify(projectService).getProjectById("project1");
        verify(userService).getUserById(userId);
    }

    @Test
    @DisplayName("Should handle empty following feed")
    void getFollowingFeed_EmptyFeed() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> emptyPage = Page.empty(pageable);

        when(authentication.getName()).thenReturn(userId);
        when(feedService.getFollowingFeed(userId, pageable)).thenReturn(emptyPage);

        // Act
        ResponseEntity<Page<FeedItemResponse>> response = feedController.getFollowingFeed(authentication, 0, 20);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isEqualTo(0);

        verify(authentication).getName();
        verify(feedService).getFollowingFeed(userId, pageable);
        verify(projectService, never()).getProjectById(any());
        verify(userService, never()).getUserById(any());
    }

    @Test
    @DisplayName("Should handle empty trending feed")
    void getTrendingFeed_EmptyFeed() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> emptyPage = Page.empty(pageable);

        when(feedService.getTrendingFeed(pageable)).thenReturn(emptyPage);

        // Act
        ResponseEntity<Page<FeedItemResponse>> response = feedController.getTrendingFeed(0, 20);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isEqualTo(0);

        verify(feedService).getTrendingFeed(pageable);
        verify(projectService, never()).getProjectById(any());
        verify(userService, never()).getUserById(any());
    }

    @Test
    @DisplayName("Should use custom page size")
    void getFollowingFeed_CustomPageSize() {
        // Arrange
        int customPageSize = 50;
        Pageable pageable = PageRequest.of(0, customPageSize);
        Page<Ship> emptyPage = Page.empty(pageable);

        when(authentication.getName()).thenReturn(userId);
        when(feedService.getFollowingFeed(eq(userId), any(Pageable.class))).thenReturn(emptyPage);

        // Act
        ResponseEntity<Page<FeedItemResponse>> response = feedController.getFollowingFeed(authentication, 0, customPageSize);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        
        verify(feedService).getFollowingFeed(eq(userId), any(Pageable.class));
    }

    @Test
    @DisplayName("Should handle pagination for trending feed")
    void getTrendingFeed_Pagination() {
        // Arrange
        int page = 2;
        int size = 10;
        Pageable pageable = PageRequest.of(page, size);
        Page<Ship> emptyPage = Page.empty(pageable);

        when(feedService.getTrendingFeed(any(Pageable.class))).thenReturn(emptyPage);

        // Act
        ResponseEntity<Page<FeedItemResponse>> response = feedController.getTrendingFeed(page, size);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        
        verify(feedService).getTrendingFeed(any(Pageable.class));
    }
}
