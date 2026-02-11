package com.shipspace.service;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PersonalizationService.createHybridFeed() method.
 * 
 * These tests verify:
 * - 50/50 split between personalized and trending content
 * - Interleaving of the two lists
 * - Deduplication by contentId
 * - Minimum feed size handling
 * 
 * Requirements: 6.3, 6.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalizationService - createHybridFeed Tests")
class PersonalizationServiceCreateHybridFeedTest {

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
    @DisplayName("createHybridFeed should interleave personalized and trending content")
    void testCreateHybridFeed_Interleaves_PersonalizedAndTrending() throws Exception {
        // Arrange
        List<Ship> personalizedList = new ArrayList<>();
        personalizedList.add(createShip("p1", "Personalized 1"));
        personalizedList.add(createShip("p2", "Personalized 2"));
        personalizedList.add(createShip("p3", "Personalized 3"));
        personalizedList.add(createShip("p4", "Personalized 4"));

        List<Ship> trendingList = new ArrayList<>();
        trendingList.add(createShip("t1", "Trending 1"));
        trendingList.add(createShip("t2", "Trending 2"));
        trendingList.add(createShip("t3", "Trending 3"));
        trendingList.add(createShip("t4", "Trending 4"));

        int targetSize = 8;

        // Act
        List<Ship> hybridFeed = invokeCreateHybridFeed(personalizedList, trendingList, targetSize);

        // Assert
        assertNotNull(hybridFeed, "Hybrid feed should not be null");
        assertEquals(8, hybridFeed.size(), "Should have 8 items (4 from each list)");
        
        // Verify interleaving pattern: p1, t1, p2, t2, p3, t3, p4, t4
        assertEquals("p1", hybridFeed.get(0).getId(), "First item should be from personalized");
        assertEquals("t1", hybridFeed.get(1).getId(), "Second item should be from trending");
        assertEquals("p2", hybridFeed.get(2).getId(), "Third item should be from personalized");
        assertEquals("t2", hybridFeed.get(3).getId(), "Fourth item should be from trending");
    }

    @Test
    @DisplayName("createHybridFeed should deduplicate content by ID")
    void testCreateHybridFeed_Deduplicates_ByContentId() throws Exception {
        // Arrange - Create lists with overlapping content
        List<Ship> personalizedList = new ArrayList<>();
        personalizedList.add(createShip("ship1", "Ship 1"));
        personalizedList.add(createShip("ship2", "Ship 2"));
        personalizedList.add(createShip("ship3", "Ship 3"));

        List<Ship> trendingList = new ArrayList<>();
        trendingList.add(createShip("ship2", "Ship 2")); // Duplicate
        trendingList.add(createShip("ship4", "Ship 4"));
        trendingList.add(createShip("ship5", "Ship 5"));

        int targetSize = 10;

        // Act
        List<Ship> hybridFeed = invokeCreateHybridFeed(personalizedList, trendingList, targetSize);

        // Assert
        assertNotNull(hybridFeed, "Hybrid feed should not be null");
        assertEquals(5, hybridFeed.size(), "Should have 5 unique items (ship2 deduplicated)");
        
        // Verify no duplicates
        Set<String> uniqueIds = new HashSet<>();
        for (Ship ship : hybridFeed) {
            assertTrue(uniqueIds.add(ship.getId()), 
                    "Each ship ID should appear only once: " + ship.getId());
        }
    }

    @Test
    @DisplayName("createHybridFeed should handle empty personalized list")
    void testCreateHybridFeed_EmptyPersonalized_UsesTrendingOnly() throws Exception {
        // Arrange
        List<Ship> personalizedList = new ArrayList<>(); // Empty

        List<Ship> trendingList = new ArrayList<>();
        trendingList.add(createShip("t1", "Trending 1"));
        trendingList.add(createShip("t2", "Trending 2"));
        trendingList.add(createShip("t3", "Trending 3"));

        int targetSize = 6;

        // Act
        List<Ship> hybridFeed = invokeCreateHybridFeed(personalizedList, trendingList, targetSize);

        // Assert
        assertNotNull(hybridFeed, "Hybrid feed should not be null");
        assertEquals(3, hybridFeed.size(), "Should have 3 items from trending");
        assertTrue(hybridFeed.stream().allMatch(s -> s.getId().startsWith("t")),
                "All items should be from trending list");
    }

    @Test
    @DisplayName("createHybridFeed should handle empty trending list")
    void testCreateHybridFeed_EmptyTrending_UsesPersonalizedOnly() throws Exception {
        // Arrange
        List<Ship> personalizedList = new ArrayList<>();
        personalizedList.add(createShip("p1", "Personalized 1"));
        personalizedList.add(createShip("p2", "Personalized 2"));
        personalizedList.add(createShip("p3", "Personalized 3"));

        List<Ship> trendingList = new ArrayList<>(); // Empty

        int targetSize = 6;

        // Act
        List<Ship> hybridFeed = invokeCreateHybridFeed(personalizedList, trendingList, targetSize);

        // Assert
        assertNotNull(hybridFeed, "Hybrid feed should not be null");
        assertEquals(3, hybridFeed.size(), "Should have 3 items from personalized");
        assertTrue(hybridFeed.stream().allMatch(s -> s.getId().startsWith("p")),
                "All items should be from personalized list");
    }

    @Test
    @DisplayName("createHybridFeed should handle both lists empty")
    void testCreateHybridFeed_BothEmpty_ReturnsEmptyList() throws Exception {
        // Arrange
        List<Ship> personalizedList = new ArrayList<>();
        List<Ship> trendingList = new ArrayList<>();
        int targetSize = 20;

        // Act
        List<Ship> hybridFeed = invokeCreateHybridFeed(personalizedList, trendingList, targetSize);

        // Assert
        assertNotNull(hybridFeed, "Hybrid feed should not be null");
        assertTrue(hybridFeed.isEmpty(), "Should return empty list when both inputs are empty");
    }

    @Test
    @DisplayName("createHybridFeed should take top 50% from each list")
    void testCreateHybridFeed_Takes_Top50Percent() throws Exception {
        // Arrange - Create lists with more items than target
        List<Ship> personalizedList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            personalizedList.add(createShip("p" + i, "Personalized " + i));
        }

        List<Ship> trendingList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            trendingList.add(createShip("t" + i, "Trending " + i));
        }

        int targetSize = 10; // Should take 5 from each

        // Act
        List<Ship> hybridFeed = invokeCreateHybridFeed(personalizedList, trendingList, targetSize);

        // Assert
        assertNotNull(hybridFeed, "Hybrid feed should not be null");
        assertEquals(10, hybridFeed.size(), "Should have 10 items (5 from each)");
        
        // Count items from each source
        long personalizedCount = hybridFeed.stream()
                .filter(s -> s.getId().startsWith("p"))
                .count();
        long trendingCount = hybridFeed.stream()
                .filter(s -> s.getId().startsWith("t"))
                .count();
        
        assertEquals(5, personalizedCount, "Should have 5 personalized items");
        assertEquals(5, trendingCount, "Should have 5 trending items");
    }

    @Test
    @DisplayName("createHybridFeed should handle unequal list sizes")
    void testCreateHybridFeed_UnequalLists_HandlesGracefully() throws Exception {
        // Arrange - Personalized has more items than trending
        List<Ship> personalizedList = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            personalizedList.add(createShip("p" + i, "Personalized " + i));
        }

        List<Ship> trendingList = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            trendingList.add(createShip("t" + i, "Trending " + i));
        }

        int targetSize = 10;

        // Act
        List<Ship> hybridFeed = invokeCreateHybridFeed(personalizedList, trendingList, targetSize);

        // Assert
        assertNotNull(hybridFeed, "Hybrid feed should not be null");
        // Should have 5 from personalized (top 50% of target) + 2 from trending (all available)
        assertEquals(7, hybridFeed.size(), "Should have 7 items total");
    }

    @Test
    @DisplayName("createHybridFeed should maintain order within each source")
    void testCreateHybridFeed_MaintainsOrder_WithinSource() throws Exception {
        // Arrange
        List<Ship> personalizedList = new ArrayList<>();
        personalizedList.add(createShip("p1", "Personalized 1"));
        personalizedList.add(createShip("p2", "Personalized 2"));
        personalizedList.add(createShip("p3", "Personalized 3"));

        List<Ship> trendingList = new ArrayList<>();
        trendingList.add(createShip("t1", "Trending 1"));
        trendingList.add(createShip("t2", "Trending 2"));
        trendingList.add(createShip("t3", "Trending 3"));

        int targetSize = 6;

        // Act
        List<Ship> hybridFeed = invokeCreateHybridFeed(personalizedList, trendingList, targetSize);

        // Assert
        // Verify personalized items appear in order (p1 before p2 before p3)
        int p1Index = -1, p2Index = -1, p3Index = -1;
        for (int i = 0; i < hybridFeed.size(); i++) {
            String id = hybridFeed.get(i).getId();
            if (id.equals("p1")) p1Index = i;
            if (id.equals("p2")) p2Index = i;
            if (id.equals("p3")) p3Index = i;
        }
        
        assertTrue(p1Index < p2Index && p2Index < p3Index,
                "Personalized items should maintain their order");
        
        // Verify trending items appear in order (t1 before t2 before t3)
        int t1Index = -1, t2Index = -1, t3Index = -1;
        for (int i = 0; i < hybridFeed.size(); i++) {
            String id = hybridFeed.get(i).getId();
            if (id.equals("t1")) t1Index = i;
            if (id.equals("t2")) t2Index = i;
            if (id.equals("t3")) t3Index = i;
        }
        
        assertTrue(t1Index < t2Index && t2Index < t3Index,
                "Trending items should maintain their order");
    }

    // Helper methods

    private List<Ship> invokeCreateHybridFeed(List<Ship> personalized, List<Ship> trending, int targetSize) 
            throws Exception {
        Method method = PersonalizationService.class.getDeclaredMethod(
                "createHybridFeed", List.class, List.class, int.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Ship> result = (List<Ship>) method.invoke(
                personalizationService, personalized, trending, targetSize);
        return result;
    }

    private Ship createShip(String id, String content) {
        return Ship.builder()
                .id(id)
                .content(content)
                .userId("builder123")
                .projectId("project123")
                .createdAt(Instant.now())
                .build();
    }
}
