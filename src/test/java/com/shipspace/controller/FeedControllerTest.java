package com.shipspace.controller;

import com.shipspace.dto.FeedItemResponse;
import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.service.*;
import com.shipspace.util.DtoMapper;
import com.shipspace.config.SloEnforcementService;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedController Tests")
class FeedControllerTest {

    @Mock
    private FeedService feedService;

    @Mock
    private PersonalizationService personalizationService;

    @Mock
    private BehavioralTrackingService behavioralTrackingService;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserService userService;

    @Mock
    private DtoMapper dtoMapper;

    @Mock
    private SloEnforcementService sloEnforcementService;

    @Mock
    private EngagementService engagementService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FeedController feedController;

    private String userId;
    private Ship ship;
    private Project project;
    private User user;
    private ShipResponse shipResponse;
    private ProjectResponse projectResponse;

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

        project = new Project();
        project.setId("project1");
        project.setName("Test Project");

        projectResponse = ProjectResponse.builder()
                .id("project1")
                .name("Test Project")
                .build();

        user = User.builder()
                .id(userId)
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .build();

        shipResponse = ShipResponse.builder()
                .id("ship1")
                .build();
    }

    @Test
    @DisplayName("Should get following feed for authenticated user")
    void getFollowingFeed_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship), pageable, 1);

        when(authentication.getName()).thenReturn(userId);
        when(feedService.getFollowingFeed(userId, pageable)).thenReturn(shipsPage);

        when(projectService.findAllByIds(Set.of("project1"))).thenReturn(List.of(project));
        when(userService.findAllByIds(Set.of(userId))).thenReturn(List.of(user));
        when(engagementService.getLikedShipIds(userId, Set.of("ship1"))).thenReturn(Set.of("ship1"));

        when(dtoMapper.toShipResponse(ship)).thenReturn(shipResponse);
        when(dtoMapper.toProjectResponse(project)).thenReturn(projectResponse);

        ResponseEntity<Page<FeedItemResponse>> response = feedController.getFollowingFeed(authentication, 0, 20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        
        FeedItemResponse feedItem = response.getBody().getContent().get(0);
        assertThat(feedItem.getShip().getId()).isEqualTo("ship1");
        assertThat(feedItem.getProject().getId()).isEqualTo("project1");
        assertThat(feedItem.getUser().getId()).isEqualTo(userId);
        assertThat(feedItem.getLiked()).isTrue();

        verify(feedService).getFollowingFeed(userId, pageable);
    }

    @Test
    @DisplayName("Should get trending feed without authentication")
    void getTrendingFeed_Success_Unauthenticated() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship), pageable, 1);

        when(feedService.getTrendingFeed(pageable)).thenReturn(shipsPage);
        when(projectService.findAllByIds(Set.of("project1"))).thenReturn(List.of(project));
        when(userService.findAllByIds(Set.of(userId))).thenReturn(List.of(user));

        when(dtoMapper.toShipResponse(ship)).thenReturn(shipResponse);
        when(dtoMapper.toProjectResponse(project)).thenReturn(projectResponse);

        // Act - null authentication
        ResponseEntity<Page<FeedItemResponse>> response = feedController.getTrendingFeed(null, 0, 20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        
        FeedItemResponse feedItem = response.getBody().getContent().get(0);
        assertThat(feedItem.getLiked()).isNull();

        verify(feedService).getTrendingFeed(pageable);
        verify(engagementService, never()).getLikedShipIds(anyString(), anySet());
    }

    @Test
    @DisplayName("Should get trending feed with time window")
    void getTrendingFeedWithTimeWindow_Success() {
        String timeWindow = "24h";
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship), pageable, 1);

        when(authentication.getName()).thenReturn(userId);
        when(feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable)).thenReturn(shipsPage);

        when(projectService.findAllByIds(Set.of("project1"))).thenReturn(List.of(project));
        when(userService.findAllByIds(Set.of(userId))).thenReturn(List.of(user));
        when(engagementService.getLikedShipIds(userId, Set.of("ship1"))).thenReturn(Set.of("ship1"));

        when(dtoMapper.toShipResponse(ship)).thenReturn(shipResponse);
        when(dtoMapper.toProjectResponse(project)).thenReturn(projectResponse);

        ResponseEntity<Page<FeedItemResponse>> response = feedController.getTrendingFeedWithTimeWindow(authentication, timeWindow, 0, 20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);

        verify(feedService).getTrendingFeedWithTimeWindow(timeWindow, pageable);
    }

    @Test
    @DisplayName("Should handle empty following feed")
    void getFollowingFeed_EmptyFeed() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> emptyPage = Page.empty(pageable);

        when(authentication.getName()).thenReturn(userId);
        when(feedService.getFollowingFeed(userId, pageable)).thenReturn(emptyPage);

        ResponseEntity<Page<FeedItemResponse>> response = feedController.getFollowingFeed(authentication, 0, 20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();

        verify(projectService, never()).findAllByIds(any());
        verify(userService, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("Should handle empty trending feed")
    void getTrendingFeed_EmptyFeed() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> emptyPage = Page.empty(pageable);

        when(authentication.getName()).thenReturn(userId);
        when(feedService.getTrendingFeed(pageable)).thenReturn(emptyPage);

        ResponseEntity<Page<FeedItemResponse>> response = feedController.getTrendingFeed(authentication, 0, 20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();

        verify(projectService, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("Should handle personalized feed with degradation")
    void getPersonalizedFeed_Degradation_ServesTrending() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship), pageable, 1);

        when(authentication.getName()).thenReturn(userId);
        when(sloEnforcementService.isPersonalizationDisabled()).thenReturn(true);
        when(feedService.getTrendingFeed(pageable)).thenReturn(shipsPage);

        when(projectService.findAllByIds(anySet())).thenReturn(List.of(project));
        when(userService.findAllByIds(anySet())).thenReturn(List.of(user));
        when(engagementService.getLikedShipIds(anyString(), anySet())).thenReturn(Set.of("ship1"));

        when(dtoMapper.toShipResponse(ship)).thenReturn(shipResponse);
        when(dtoMapper.toProjectResponse(project)).thenReturn(projectResponse);

        ResponseEntity<Page<FeedItemResponse>> response = feedController.getPersonalizedFeed(authentication, 0, 20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst("X-Feed-Mode")).isEqualTo("trending-fallback");

        verify(feedService).getTrendingFeed(pageable);
        verify(personalizationService, never()).getPersonalizedFeed(anyString(), any());
        verify(behavioralTrackingService).trackView(userId, "ship1");
    }

    @Test
    @DisplayName("Should serve true personalized feed without degradation")
    void getPersonalizedFeed_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship), pageable, 1);

        when(authentication.getName()).thenReturn(userId);
        when(sloEnforcementService.isPersonalizationDisabled()).thenReturn(false);
        when(personalizationService.getPersonalizedFeed(userId, pageable)).thenReturn(shipsPage);

        when(projectService.findAllByIds(anySet())).thenReturn(List.of(project));
        when(userService.findAllByIds(anySet())).thenReturn(List.of(user));
        when(engagementService.getLikedShipIds(anyString(), anySet())).thenReturn(Set.of("ship1"));

        when(dtoMapper.toShipResponse(ship)).thenReturn(shipResponse);
        when(dtoMapper.toProjectResponse(project)).thenReturn(projectResponse);

        ResponseEntity<Page<FeedItemResponse>> response = feedController.getPersonalizedFeed(authentication, 0, 20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst("X-Feed-Mode")).isEqualTo("personalized");

        verify(personalizationService).getPersonalizedFeed(userId, pageable);
        verify(behavioralTrackingService).trackView(userId, "ship1");
    }
}
