package com.shipspace.service;

import com.shipspace.model.BehavioralSignal;
import com.shipspace.model.BehavioralSignal.ContentType;
import com.shipspace.model.BehavioralSignal.SignalType;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import com.shipspace.model.UserInterestProfile;
import com.shipspace.repository.BehavioralSignalRepository;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserInterestProfileRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for BehavioralTrackingService.
 * 
 * These tests verify the correctness properties of the behavioral tracking system
 * across a wide range of inputs using property-based testing with jqwik.
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4, 7.1, 7.2, 7.3, 7.4
 */
class BehavioralTrackingPropertyTest {

    @Mock
    private BehavioralSignalRepository behavioralSignalRepository;

    @Mock
    private UserInterestProfileRepository userInterestProfileRepository;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private BehavioralTrackingService behavioralTrackingService;

    private AutoCloseable mocks;

    @BeforeProperty
    void setUp() {
        // Initialize mocks before each property test
        mocks = MockitoAnnotations.openMocks(this);
    }

    /**
     * Property 2: Builder follows are recorded in profiles
     * 
     * **Validates: Requirements 1.4**
     * 
     * Test that for any user and builder, when user follows the builder,
     * the builder ID appears in followedBuilders set.
     */
    @Property(tries = 100)
    @Label("Feature: personalized-feed-algorithm, Property 2: Builder follows are recorded in profiles")
    void builderFollowsAreRecordedInProfiles(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String builderId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String contentId
    ) throws InterruptedException {
        // Ensure userId and builderId are different
        Assume.that(!userId.equals(builderId));

        // Arrange - Create test ship with the builder
        Ship testShip = Ship.builder()
                .id(contentId)
                .userId(builderId)
                .projectId("project" + contentId)
                .content("Test ship content")
                .createdAt(Instant.now())
                .build();

        // Create test project with tech tags
        Project testProject = Project.builder()
                .id("project" + contentId)
                .userId(builderId)
                .name("Test Project")
                .description("Test project description")
                .techTags(List.of("Java", "Spring", "MongoDB"))
                .createdAt(Instant.now())
                .build();

        // Mock repository responses
        when(shipRepository.findById(contentId)).thenReturn(Optional.of(testShip));
        when(projectRepository.findById("project" + contentId)).thenReturn(Optional.of(testProject));

        // Create an AtomicReference to hold the current profile state
        final UserInterestProfile[] currentProfile = {null};

        // Mock profile repository to maintain state across calls
        when(userInterestProfileRepository.findByUserId(userId)).thenAnswer(invocation -> 
            currentProfile[0] == null ? Optional.empty() : Optional.of(currentProfile[0])
        );
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> {
            currentProfile[0] = invocation.getArgument(0);
            return currentProfile[0];
        });
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Track follow action
        behavioralTrackingService.trackFollow(userId, builderId, contentId);

        // Give async method time to complete
        Thread.sleep(200);

        // Assert - Verify builder was added to followedBuilders
        assertNotNull(currentProfile[0], "User interest profile should be created");
        assertEquals(userId, currentProfile[0].getUserId(), "Profile should belong to the correct user");
        
        Set<String> followedBuilders = currentProfile[0].getFollowedBuilders();
        assertNotNull(followedBuilders, "Followed builders set should not be null");
        assertTrue(followedBuilders.contains(builderId), 
                   "Builder ID should be present in followedBuilders set after follow action");
        
        // Verify the profile was saved
        verify(userInterestProfileRepository, atLeast(1)).save(any(UserInterestProfile.class));
        
        // Verify the signal was persisted
        verify(behavioralSignalRepository, atLeast(1)).save(any(BehavioralSignal.class));
    }

    /**
     * Property 2 (Extended): Multiple follows accumulate in followedBuilders
     * 
     * **Validates: Requirements 1.4**
     * 
     * Test that when a user follows multiple builders, all builder IDs
     * appear in the followedBuilders set.
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property 2: Multiple builder follows accumulate")
    void multipleBuilderFollowsAccumulate(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String userId,
            @ForAll("builderList") List<String> builderIds
    ) throws InterruptedException {
        // Ensure we have at least 2 builders and they're all unique
        Assume.that(builderIds.size() >= 2);
        Assume.that(builderIds.stream().distinct().count() == builderIds.size());
        Assume.that(builderIds.stream().noneMatch(b -> b.equals(userId)));

        // Create an AtomicReference to hold the current profile state
        final UserInterestProfile[] currentProfile = {null};

        // Mock profile repository to maintain state across calls
        when(userInterestProfileRepository.findByUserId(userId)).thenAnswer(invocation -> 
            currentProfile[0] == null ? Optional.empty() : Optional.of(currentProfile[0])
        );
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> {
            currentProfile[0] = invocation.getArgument(0);
            return currentProfile[0];
        });
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Track follow actions for each builder
        for (int i = 0; i < builderIds.size(); i++) {
            String builderId = builderIds.get(i);
            String contentId = "content" + i;

            // Create test ship and project for each follow
            Ship testShip = Ship.builder()
                    .id(contentId)
                    .userId(builderId)
                    .projectId("project" + contentId)
                    .content("Test ship content")
                    .createdAt(Instant.now())
                    .build();

            Project testProject = Project.builder()
                    .id("project" + contentId)
                    .userId(builderId)
                    .name("Test Project")
                    .description("Test project description")
                    .techTags(List.of("Java", "Spring"))
                    .createdAt(Instant.now())
                    .build();

            when(shipRepository.findById(contentId)).thenReturn(Optional.of(testShip));
            when(projectRepository.findById("project" + contentId)).thenReturn(Optional.of(testProject));

            behavioralTrackingService.trackFollow(userId, builderId, contentId);
            Thread.sleep(150);
        }

        // Assert - All builders should be in followedBuilders set
        assertNotNull(currentProfile[0], "User interest profile should be created");
        Set<String> followedBuilders = currentProfile[0].getFollowedBuilders();
        
        assertEquals(builderIds.size(), followedBuilders.size(), 
                     "All followed builders should be in the set");
        
        for (String builderId : builderIds) {
            assertTrue(followedBuilders.contains(builderId), 
                       "Builder " + builderId + " should be in followedBuilders set");
        }
    }

    /**
     * Property 2 (Idempotency): Following the same builder multiple times
     * 
     * **Validates: Requirements 1.4**
     * 
     * Test that following the same builder multiple times results in
     * the builder appearing only once in the followedBuilders set.
     */
    @Property(tries = 50)
    @Label("Feature: personalized-feed-algorithm, Property 2: Follow is idempotent")
    void followIsIdempotent(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String userId,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String builderId,
            @ForAll("smallPositiveInt") int followCount
    ) throws InterruptedException {
        // Ensure userId and builderId are different
        Assume.that(!userId.equals(builderId));
        Assume.that(followCount >= 2 && followCount <= 5);

        // Arrange
        String contentId = "content123";
        Ship testShip = Ship.builder()
                .id(contentId)
                .userId(builderId)
                .projectId("project123")
                .content("Test ship content")
                .createdAt(Instant.now())
                .build();

        Project testProject = Project.builder()
                .id("project123")
                .userId(builderId)
                .name("Test Project")
                .description("Test project description")
                .techTags(List.of("Java", "Spring"))
                .createdAt(Instant.now())
                .build();

        when(shipRepository.findById(contentId)).thenReturn(Optional.of(testShip));
        when(projectRepository.findById("project123")).thenReturn(Optional.of(testProject));

        // Create an AtomicReference to hold the current profile state
        final UserInterestProfile[] currentProfile = {null};

        when(userInterestProfileRepository.findByUserId(userId)).thenAnswer(invocation -> 
            currentProfile[0] == null ? Optional.empty() : Optional.of(currentProfile[0])
        );
        when(userInterestProfileRepository.save(any())).thenAnswer(invocation -> {
            currentProfile[0] = invocation.getArgument(0);
            return currentProfile[0];
        });
        when(behavioralSignalRepository.save(any(BehavioralSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Follow the same builder multiple times
        for (int i = 0; i < followCount; i++) {
            behavioralTrackingService.trackFollow(userId, builderId, contentId);
            Thread.sleep(150);
        }

        // Assert - Builder should appear only once in followedBuilders
        assertNotNull(currentProfile[0], "User interest profile should be created");
        Set<String> followedBuilders = currentProfile[0].getFollowedBuilders();
        
        assertEquals(1, followedBuilders.size(), 
                     "Following the same builder multiple times should result in only one entry");
        assertTrue(followedBuilders.contains(builderId), 
                   "Builder should be in followedBuilders set");
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<List<String>> builderList() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .list()
                .ofMinSize(2)
                .ofMaxSize(5);
    }

    @Provide
    Arbitrary<Integer> smallPositiveInt() {
        return Arbitraries.integers().between(2, 5);
    }
}
