package com.shipspace.service;

import com.shipspace.model.Following;
import com.shipspace.model.Ship;
import com.shipspace.repository.FollowingRepository;
import com.shipspace.repository.ShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeedService.
 * Tests following feed generation, trending feed generation, and engagement scoring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedService Tests")
class FeedServiceTest {

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private FollowingRepository followingRepository;

    @InjectMocks
    private FeedService feedService;

    private String userId;
    private Ship ship1;
    private Ship ship2;
    private Ship ship3;
    private Instant now;

    @BeforeEach
    void setUp() {
        userId = "user123";
        now = Instant.now();

        ship1 = Ship.builder()
                .id("ship1")
                .userId("user456")
                .projectId("project1")
                .content("First ship content")
                .likeCount(10)
                .commentCount(5)
                .trendingScore(5.0)
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .updatedAt(now.minus(1, ChronoUnit.HOURS))
                .build();

        ship2 = Ship.builder()
                .id("ship2")
                .userId("user789")
                .projectId("project2")
                .content("Second ship content")
                .likeCount(20)
                .commentCount(10)
                .trendingScore(10.0)
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .updatedAt(now.minus(2, ChronoUnit.HOURS))
                .build();

        ship3 = Ship.builder()
                .id("ship3")
                .userId("user101")
                .projectId("project3")
                .content("Third ship content")
                .likeCount(5)
                .commentCount(2)
                .trendingScore(2.0)
                .createdAt(now.minus(3, ChronoUnit.HOURS))
                .updatedAt(now.minus(3, ChronoUnit.HOURS))
                .build();
    }

    @Test
    @DisplayName("Should generate following feed with ships from followed users")
    void getFollowingFeed_Success() {
        // Arrange
        Following following1 = Following.builder()
                .followerId(userId)
                .followeeId("user456")
                .build();
        Following following2 = Following.builder()
                .followerId(userId)
                .followeeId("user789")
                .build();

        List<Following> followingList = Arrays.asList(following1, following2);
        List<String> followeeIds = Arrays.asList("user456", "user789");

        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Arrays.asList(ship1, ship2), pageable, 2);

        when(followingRepository.findByFollowerId(userId)).thenReturn(followingList);
        when(shipRepository.findByUserIdIn(eq(followeeIds), any(Pageable.class))).thenReturn(shipsPage);

        // Act
        Page<Ship> result = feedService.getFollowingFeed(userId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).containsExactly(ship1, ship2);
        assertThat(result.getTotalElements()).isEqualTo(2);

        verify(followingRepository).findByFollowerId(userId);
        
        // Verify that the pageable passed to shipRepository has DESC ordering by createdAt
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(shipRepository).findByUserIdIn(eq(followeeIds), pageableCaptor.capture());
        
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("Should return empty page when user doesn't follow anyone")
    void getFollowingFeed_NoFollowing_ReturnsEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(followingRepository.findByFollowerId(userId)).thenReturn(Collections.emptyList());

        // Act
        Page<Ship> result = feedService.getFollowingFeed(userId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);

        verify(followingRepository).findByFollowerId(userId);
        verify(shipRepository, never()).findByUserIdIn(any(), any());
    }

    @Test
    @DisplayName("Should return empty page when followed users have no ships")
    void getFollowingFeed_NoShips_ReturnsEmptyPage() {
        // Arrange
        Following following = Following.builder()
                .followerId(userId)
                .followeeId("user456")
                .build();

        List<Following> followingList = Collections.singletonList(following);
        List<String> followeeIds = Collections.singletonList("user456");

        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> emptyPage = Page.empty(pageable);

        when(followingRepository.findByFollowerId(userId)).thenReturn(followingList);
        when(shipRepository.findByUserIdIn(eq(followeeIds), any(Pageable.class))).thenReturn(emptyPage);

        // Act
        Page<Ship> result = feedService.getFollowingFeed(userId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);

        verify(followingRepository).findByFollowerId(userId);
        verify(shipRepository).findByUserIdIn(eq(followeeIds), any(Pageable.class));
    }

    @Test
    @DisplayName("Should generate trending feed ordered by trending score")
    void getTrendingFeed_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Arrays.asList(ship2, ship1, ship3), pageable, 3);

        when(shipRepository.findAll(any(Pageable.class))).thenReturn(shipsPage);

        // Act
        Page<Ship> result = feedService.getTrendingFeed(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).containsExactly(ship2, ship1, ship3);
        assertThat(result.getTotalElements()).isEqualTo(3);

        // Verify that the pageable has DESC ordering by trendingScore
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(shipRepository).findAll(pageableCaptor.capture());
        
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getSort().getOrderFor("trendingScore")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("trendingScore").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("Should generate trending feed with time window")
    void getTrendingFeedWithTimeWindow_Success() {
        // Arrange
        String timeWindow = "24h";
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Arrays.asList(ship2, ship1), pageable, 2);

        when(shipRepository.findByCreatedAtAfter(any(Instant.class), any(Pageable.class))).thenReturn(shipsPage);

        // Act
        Page<Ship> result = feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).containsExactly(ship2, ship1);

        // Verify that the start time is approximately 24 hours ago
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(shipRepository).findByCreatedAtAfter(instantCaptor.capture(), pageableCaptor.capture());
        
        Instant capturedStartTime = instantCaptor.getValue();
        Instant expectedStartTime = now.minus(24, ChronoUnit.HOURS);
        assertThat(capturedStartTime).isBetween(
                expectedStartTime.minus(1, ChronoUnit.SECONDS),
                expectedStartTime.plus(1, ChronoUnit.SECONDS)
        );

        // Verify sorting
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getSort().getOrderFor("trendingScore")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("trendingScore").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("Should parse time window with days")
    void getTrendingFeedWithTimeWindow_Days() {
        // Arrange
        String timeWindow = "7d";
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = new PageImpl<>(Collections.singletonList(ship1), pageable, 1);

        when(shipRepository.findByCreatedAtAfter(any(Instant.class), any(Pageable.class))).thenReturn(shipsPage);

        // Act
        Page<Ship> result = feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(shipRepository).findByCreatedAtAfter(instantCaptor.capture(), any(Pageable.class));
        
        Instant capturedStartTime = instantCaptor.getValue();
        Instant expectedStartTime = now.minus(7, ChronoUnit.DAYS);
        assertThat(capturedStartTime).isBetween(
                expectedStartTime.minus(1, ChronoUnit.SECONDS),
                expectedStartTime.plus(1, ChronoUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("Should parse time window with weeks")
    void getTrendingFeedWithTimeWindow_Weeks() {
        // Arrange
        String timeWindow = "2w";
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = Page.empty(pageable);

        when(shipRepository.findByCreatedAtAfter(any(Instant.class), any(Pageable.class))).thenReturn(shipsPage);

        // Act
        Page<Ship> result = feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable);

        // Assert
        assertThat(result).isNotNull();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(shipRepository).findByCreatedAtAfter(instantCaptor.capture(), any(Pageable.class));
        
        Instant capturedStartTime = instantCaptor.getValue();
        Instant expectedStartTime = now.minus(2, ChronoUnit.WEEKS);
        assertThat(capturedStartTime).isBetween(
                expectedStartTime.minus(1, ChronoUnit.SECONDS),
                expectedStartTime.plus(1, ChronoUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("Should parse time window with months")
    void getTrendingFeedWithTimeWindow_Months() {
        // Arrange
        String timeWindow = "1m";
        Pageable pageable = PageRequest.of(0, 20);
        Page<Ship> shipsPage = Page.empty(pageable);

        when(shipRepository.findByCreatedAtAfter(any(Instant.class), any(Pageable.class))).thenReturn(shipsPage);

        // Act
        Page<Ship> result = feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable);

        // Assert
        assertThat(result).isNotNull();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(shipRepository).findByCreatedAtAfter(instantCaptor.capture(), any(Pageable.class));
        
        Instant capturedStartTime = instantCaptor.getValue();
        Instant expectedStartTime = now.minus(30, ChronoUnit.DAYS);
        assertThat(capturedStartTime).isBetween(
                expectedStartTime.minus(1, ChronoUnit.SECONDS),
                expectedStartTime.plus(1, ChronoUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid time window format")
    void getTrendingFeedWithTimeWindow_InvalidFormat_ThrowsException() {
        // Arrange
        String timeWindow = "invalid";
        Pageable pageable = PageRequest.of(0, 20);

        // Act & Assert
        assertThatThrownBy(() -> feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid time window format");

        verify(shipRepository, never()).findByCreatedAtAfter(any(), any());
    }

    @Test
    @DisplayName("Should throw exception for invalid time window unit")
    void getTrendingFeedWithTimeWindow_InvalidUnit_ThrowsException() {
        // Arrange
        String timeWindow = "24x";
        Pageable pageable = PageRequest.of(0, 20);

        // Act & Assert
        assertThatThrownBy(() -> feedService.getTrendingFeedWithTimeWindow(timeWindow, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid time window unit");

        verify(shipRepository, never()).findByCreatedAtAfter(any(), any());
    }

    @Test
    @DisplayName("Should throw exception for null time window")
    void getTrendingFeedWithTimeWindow_NullTimeWindow_ThrowsException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);

        // Act & Assert
        assertThatThrownBy(() -> feedService.getTrendingFeedWithTimeWindow(null, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time window cannot be null or empty");

        verify(shipRepository, never()).findByCreatedAtAfter(any(), any());
    }

    @Test
    @DisplayName("Should throw exception for empty time window")
    void getTrendingFeedWithTimeWindow_EmptyTimeWindow_ThrowsException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);

        // Act & Assert
        assertThatThrownBy(() -> feedService.getTrendingFeedWithTimeWindow("", pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time window cannot be null or empty");

        verify(shipRepository, never()).findByCreatedAtAfter(any(), any());
    }

    @Test
    @DisplayName("Should calculate trending scores for all ships")
    void calculateTrendingScores_Success() {
        // Arrange
        Ship oldShip = Ship.builder()
                .id("oldShip")
                .userId("user1")
                .projectId("project1")
                .content("Old ship")
                .likeCount(100)
                .commentCount(50)
                .trendingScore(0.0)
                .createdAt(now.minus(48, ChronoUnit.HOURS))
                .build();

        Ship newShip = Ship.builder()
                .id("newShip")
                .userId("user2")
                .projectId("project2")
                .content("New ship")
                .likeCount(10)
                .commentCount(5)
                .trendingScore(0.0)
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .build();

        List<Ship> allShips = Arrays.asList(oldShip, newShip);

        when(shipRepository.findAll()).thenReturn(allShips);
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        feedService.calculateTrendingScores();

        // Assert
        verify(shipRepository).findAll();
        verify(shipRepository, times(2)).save(any(Ship.class));

        // Verify that scores were calculated (new ship should have higher score due to recency)
        ArgumentCaptor<Ship> shipCaptor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository, times(2)).save(shipCaptor.capture());
        
        List<Ship> savedShips = shipCaptor.getAllValues();
        assertThat(savedShips).hasSize(2);
        
        // Both ships should have non-zero scores
        assertThat(savedShips.get(0).getTrendingScore()).isGreaterThan(0.0);
        assertThat(savedShips.get(1).getTrendingScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should not update ships with unchanged scores")
    void calculateTrendingScores_UnchangedScores_NoUpdate() {
        // Arrange
        Ship shipWithCorrectScore = Ship.builder()
                .id("ship1")
                .userId("user1")
                .projectId("project1")
                .content("Ship content")
                .likeCount(0)
                .commentCount(0)
                .trendingScore(0.0)
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findAll()).thenReturn(Collections.singletonList(shipWithCorrectScore));

        // Act
        feedService.calculateTrendingScores();

        // Assert
        verify(shipRepository).findAll();
        verify(shipRepository, never()).save(any(Ship.class));
    }

    @Test
    @DisplayName("Should handle ships with null engagement counts")
    void calculateTrendingScores_NullEngagementCounts() {
        // Arrange
        Ship shipWithNulls = Ship.builder()
                .id("ship1")
                .userId("user1")
                .projectId("project1")
                .content("Ship content")
                .likeCount(null)
                .commentCount(null)
                .trendingScore(5.0)
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .build();

        when(shipRepository.findAll()).thenReturn(Collections.singletonList(shipWithNulls));
        when(shipRepository.save(any(Ship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        feedService.calculateTrendingScores();

        // Assert
        verify(shipRepository).findAll();
        verify(shipRepository).save(any(Ship.class));

        ArgumentCaptor<Ship> shipCaptor = ArgumentCaptor.forClass(Ship.class);
        verify(shipRepository).save(shipCaptor.capture());
        
        Ship savedShip = shipCaptor.getValue();
        assertThat(savedShip.getTrendingScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle empty ship list")
    void calculateTrendingScores_EmptyList() {
        // Arrange
        when(shipRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        feedService.calculateTrendingScores();

        // Assert
        verify(shipRepository).findAll();
        verify(shipRepository, never()).save(any(Ship.class));
    }
}
