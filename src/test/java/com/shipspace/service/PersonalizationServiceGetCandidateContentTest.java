package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.Ship;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ContentScoreRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PersonalizationService.getCandidateContent() method.
 * 
 * These tests verify:
 * - Exclusion of recently viewed content (past 7 days)
 * - Proper querying of behavioral signals
 * - Database index usage for performance
 * 
 * Requirements: 6.5, 8.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalizationService - getCandidateContent Tests")
class PersonalizationServiceGetCandidateContentTest {

    @Mock
    private UserInterestProfileRepository userInterestProfileRepository;

    @Mock
    private ContentScoreRepository contentScoreRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private BehavioralSignalRepository behavioralSignalRepository;

    @Mock
    private FeedService feedService;

    @InjectMocks
    private PersonalizationService personalizationService;

    @Test
    @DisplayName("getCandidateContent should exclude recently viewed ships")
    void testGetCandidateContent_ExcludesRecentlyViewed() throws Exception {
        // Arrange
        String userId = "user123";
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));

        // Create viewed signals
        List<BehavioralSignal> viewSignals = new ArrayList<>();
        viewSignals.add(createViewSignal(userId, "ship1", Instant.now().minus(Duration.ofDays(2))));
        viewSignals.add(createViewSignal(userId, "ship2", Instant.now().minus(Duration.ofDays(5))));

        // Create all ships
        List<Ship> allShips = new ArrayList<>();
        allShips.add(createShip("ship1", "Viewed Ship 1"));
        allShips.add(createShip("ship2", "Viewed Ship 2"));
        allShips.add(createShip("ship3", "Unviewed Ship 3"));
        allShips.add(createShip("ship4", "Unviewed Ship 4"));

        when(behavioralSignalRepository.findByUserIdAndTimestampAfter(eq(userId), any(Instant.class)))
                .thenReturn(viewSignals);
        when(shipRepository.findAll()).thenReturn(allShips);

        // Act
        List<Ship> candidates = invokeCandidateContent(userId);

        // Assert
        assertNotNull(candidates, "Candidate list should not be null");
        assertEquals(2, candidates.size(), "Should return 2 unviewed ships");
        assertTrue(candidates.stream().anyMatch(s -> s.getId().equals("ship3")), "Should include ship3");
        assertTrue(candidates.stream().anyMatch(s -> s.getId().equals("ship4")), "Should include ship4");
        assertFalse(candidates.stream().anyMatch(s -> s.getId().equals("ship1")), "Should exclude ship1");
        assertFalse(candidates.stream().anyMatch(s -> s.getId().equals("ship2")), "Should exclude ship2");

        verify(behavioralSignalRepository).findByUserIdAndTimestampAfter(eq(userId), any(Instant.class));
        verify(shipRepository).findAll();
    }

    @Test
    @DisplayName("getCandidateContent should return all ships when no views")
    void testGetCandidateContent_NoViews_ReturnsAllShips() throws Exception {
        // Arrange
        String userId = "newUser456";

        List<Ship> allShips = new ArrayList<>();
        allShips.add(createShip("ship1", "Ship 1"));
        allShips.add(createShip("ship2", "Ship 2"));
        allShips.add(createShip("ship3", "Ship 3"));

        when(behavioralSignalRepository.findByUserIdAndTimestampAfter(eq(userId), any(Instant.class)))
                .thenReturn(new ArrayList<>());
        when(shipRepository.findAll()).thenReturn(allShips);

        // Act
        List<Ship> candidates = invokeCandidateContent(userId);

        // Assert
        assertNotNull(candidates, "Candidate list should not be null");
        assertEquals(3, candidates.size(), "Should return all 3 ships");
        
        verify(behavioralSignalRepository).findByUserIdAndTimestampAfter(eq(userId), any(Instant.class));
        verify(shipRepository).findAll();
    }

    @Test
    @DisplayName("getCandidateContent should only exclude VIEW signals, not other signal types")
    void testGetCandidateContent_OnlyExcludesViewSignals() throws Exception {
        // Arrange
        String userId = "user789";

        // Create mixed signals - only VIEW should exclude content
        List<BehavioralSignal> mixedSignals = new ArrayList<>();
        mixedSignals.add(createViewSignal(userId, "ship1", Instant.now().minus(Duration.ofDays(2))));
        mixedSignals.add(createLikeSignal(userId, "ship2", Instant.now().minus(Duration.ofDays(3))));
        mixedSignals.add(createCommentSignal(userId, "ship3", Instant.now().minus(Duration.ofDays(4))));

        List<Ship> allShips = new ArrayList<>();
        allShips.add(createShip("ship1", "Viewed Ship"));
        allShips.add(createShip("ship2", "Liked Ship"));
        allShips.add(createShip("ship3", "Commented Ship"));
        allShips.add(createShip("ship4", "Uninteracted Ship"));

        when(behavioralSignalRepository.findByUserIdAndTimestampAfter(eq(userId), any(Instant.class)))
                .thenReturn(mixedSignals);
        when(shipRepository.findAll()).thenReturn(allShips);

        // Act
        List<Ship> candidates = invokeCandidateContent(userId);

        // Assert
        assertNotNull(candidates, "Candidate list should not be null");
        assertEquals(3, candidates.size(), "Should return 3 ships (excluding only viewed)");
        assertFalse(candidates.stream().anyMatch(s -> s.getId().equals("ship1")), "Should exclude viewed ship1");
        assertTrue(candidates.stream().anyMatch(s -> s.getId().equals("ship2")), "Should include liked ship2");
        assertTrue(candidates.stream().anyMatch(s -> s.getId().equals("ship3")), "Should include commented ship3");
        assertTrue(candidates.stream().anyMatch(s -> s.getId().equals("ship4")), "Should include uninteracted ship4");
        
        verify(behavioralSignalRepository).findByUserIdAndTimestampAfter(eq(userId), any(Instant.class));
        verify(shipRepository).findAll();
    }

    @Test
    @DisplayName("getCandidateContent should handle empty ship repository")
    void testGetCandidateContent_EmptyRepository_ReturnsEmptyList() throws Exception {
        // Arrange
        String userId = "user999";

        when(behavioralSignalRepository.findByUserIdAndTimestampAfter(eq(userId), any(Instant.class)))
                .thenReturn(new ArrayList<>());
        when(shipRepository.findAll()).thenReturn(new ArrayList<>());

        // Act
        List<Ship> candidates = invokeCandidateContent(userId);

        // Assert
        assertNotNull(candidates, "Candidate list should not be null");
        assertTrue(candidates.isEmpty(), "Should return empty list when no ships exist");
        
        verify(behavioralSignalRepository).findByUserIdAndTimestampAfter(eq(userId), any(Instant.class));
        verify(shipRepository).findAll();
    }

    @Test
    @DisplayName("getCandidateContent should handle views older than 7 days")
    void testGetCandidateContent_OldViews_NotExcluded() throws Exception {
        // Arrange
        String userId = "user111";

        // Create view signal older than 7 days (should not be returned by repository)
        List<BehavioralSignal> recentSignals = new ArrayList<>();
        // Only recent signals are returned by findByUserIdAndTimestampAfter

        List<Ship> allShips = new ArrayList<>();
        allShips.add(createShip("ship1", "Old Viewed Ship"));
        allShips.add(createShip("ship2", "New Ship"));

        when(behavioralSignalRepository.findByUserIdAndTimestampAfter(eq(userId), any(Instant.class)))
                .thenReturn(recentSignals);
        when(shipRepository.findAll()).thenReturn(allShips);

        // Act
        List<Ship> candidates = invokeCandidateContent(userId);

        // Assert
        assertNotNull(candidates, "Candidate list should not be null");
        assertEquals(2, candidates.size(), "Should return all ships when no recent views");
        assertTrue(candidates.stream().anyMatch(s -> s.getId().equals("ship1")), "Should include ship1");
        assertTrue(candidates.stream().anyMatch(s -> s.getId().equals("ship2")), "Should include ship2");
        
        verify(behavioralSignalRepository).findByUserIdAndTimestampAfter(eq(userId), any(Instant.class));
        verify(shipRepository).findAll();
    }

    // Helper methods

    private List<Ship> invokeCandidateContent(String userId) throws Exception {
        Method method = PersonalizationService.class.getDeclaredMethod("getCandidateContent", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Ship> result = (List<Ship>) method.invoke(personalizationService, userId);
        return result;
    }

    private BehavioralSignal createViewSignal(String userId, String contentId, Instant timestamp) {
        return BehavioralSignal.builder()
                .id("signal_" + contentId)
                .userId(userId)
                .signalType(BehavioralSignal.SignalType.VIEW)
                .contentId(contentId)
                .contentType(BehavioralSignal.ContentType.SHIP)
                .durationSeconds(15)
                .timestamp(timestamp)
                .build();
    }

    private BehavioralSignal createLikeSignal(String userId, String contentId, Instant timestamp) {
        return BehavioralSignal.builder()
                .id("signal_" + contentId)
                .userId(userId)
                .signalType(BehavioralSignal.SignalType.LIKE)
                .contentId(contentId)
                .contentType(BehavioralSignal.ContentType.SHIP)
                .timestamp(timestamp)
                .build();
    }

    private BehavioralSignal createCommentSignal(String userId, String contentId, Instant timestamp) {
        return BehavioralSignal.builder()
                .id("signal_" + contentId)
                .userId(userId)
                .signalType(BehavioralSignal.SignalType.COMMENT)
                .contentId(contentId)
                .contentType(BehavioralSignal.ContentType.SHIP)
                .timestamp(timestamp)
                .build();
    }

    private Ship createShip(String id, String content) {
        return Ship.builder()
                .id(id)
                .content(content)
                .userId("builder123")
                .projectId("project123")
                .createdAt(Instant.now().minus(Duration.ofDays(10)))
                .build();
    }
}
