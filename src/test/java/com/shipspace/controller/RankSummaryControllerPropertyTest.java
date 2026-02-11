package com.shipspace.controller;

import com.shipspace.dto.RankSummaryResponse;
import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.ShipScoreService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for RankSummaryController.
 * 
 * <p>These tests verify the correctness properties of the rank summary endpoint
 * across a wide range of inputs using property-based testing with jqwik.
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 12.2</a>
 */
@ExtendWith(MockitoExtension.class)
class RankSummaryControllerPropertyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShipScoreService shipScoreService;

    @BeforeProperty
    void initMocks() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Property 18: Rank Summary Response Completeness
     * 
     * **Validates: Requirements 12.2**
     * 
     * For any valid user, the rank summary response should contain all four required fields:
     * shipScore (integer), level (integer), rank (string), and nextLevelScore (integer or null).
     * 
     * This property verifies that:
     * 1. The response always contains a non-null shipScore field
     * 2. The response always contains a non-null level field
     * 3. The response always contains a non-null rank field (as a string)
     * 4. The response always contains a nextLevelScore field (may be null for level 30)
     * 5. All fields have the correct data types
     * 6. The response structure is complete and consistent
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 18: Rank Summary Response Completeness")
    void rankSummaryResponseContainsAllRequiredFields(
            @ForAll("validUser") User user,
            @ForAll @IntRange(min = 0, max = 2000) Integer nextLevelScore
    ) {
        // Arrange - Create controller instance
        RankSummaryController controller = new RankSummaryController(userRepository, shipScoreService);
        
        // Mock repository to return the user
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        
        // Mock service to return nextLevelScore (null for level 30, otherwise a value)
        Integer expectedNextLevelScore = user.getLevel() >= 30 ? null : nextLevelScore;
        when(shipScoreService.calculateNextLevelScore(user.getLevel(), user.getShipScore()))
                .thenReturn(expectedNextLevelScore);

        // Act - Call the endpoint
        ResponseEntity<RankSummaryResponse> responseEntity = controller.getRankSummary(user.getId());

        // Assert - Verify response is not null
        assertNotNull(responseEntity, "ResponseEntity should not be null");
        
        RankSummaryResponse response = responseEntity.getBody();
        assertNotNull(response, "Response body should not be null");

        // Property 1: shipScore field is present and has correct type
        assertEquals(user.getShipScore(), response.getShipScore(), 
            "shipScore should match user's shipScore");

        // Property 2: level field is present and has correct type
        assertEquals(user.getLevel(), response.getLevel(), 
            "level should match user's level");

        // Property 3: rank field is present and is a string
        assertNotNull(response.getRank(), "rank should not be null");
        assertEquals(user.getRank().name(), response.getRank(), 
            "rank should match user's rank as string");

        // Property 4: nextLevelScore field is present
        assertEquals(expectedNextLevelScore, response.getNextLevelScore(), 
            "nextLevelScore should match expected value");

        // Property 5: nextLevelScore is null when user is at level 30
        if (user.getLevel() >= 30) {
            assertNull(response.getNextLevelScore(), 
                "nextLevelScore should be null for level 30 users");
        }
    }

    /**
     * Property 19: Rank Serialization Format
     * 
     * **Validates: Requirements 12.6**
     * 
     * For any valid user, the rank field in the response should be serialized
     * as the uppercase enum name (e.g., "DOCKED", "LAUNCHER", "ORBITER", etc.).
     * 
     * This property verifies that:
     * 1. The rank is always serialized as a String
     * 2. The rank string matches the enum name exactly
     * 3. The rank string is in UPPERCASE
     * 4. All possible rank tiers are correctly serialized
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 19: Rank Serialization Format")
    void rankFieldIsSerializedAsUppercaseEnumName(
            @ForAll("validUserWithRank") User user
    ) {
        // Arrange - Create controller instance
        RankSummaryController controller = new RankSummaryController(userRepository, shipScoreService);
        
        // Mock repository to return the user
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        
        // Mock service to return a nextLevelScore
        when(shipScoreService.calculateNextLevelScore(anyInt(), anyInt()))
                .thenReturn(40);

        // Act - Call the endpoint
        ResponseEntity<RankSummaryResponse> responseEntity = controller.getRankSummary(user.getId());
        RankSummaryResponse response = responseEntity.getBody();

        // Assert - Verify rank serialization format
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getRank(), "Rank should not be null");

        // Property 1: Rank is a String
        assertTrue(response.getRank() instanceof String, 
            "Rank should be serialized as a String");

        // Property 2: Rank matches the enum name exactly
        String expectedRankName = user.getRank().name();
        assertEquals(expectedRankName, response.getRank(), 
            "Rank should match the enum name exactly");

        // Property 3: Rank is in UPPERCASE
        assertEquals(expectedRankName.toUpperCase(), response.getRank(), 
            "Rank should be in UPPERCASE");
        
        // Property 4: Rank is one of the valid enum values
        assertDoesNotThrow(() -> RankTier.valueOf(response.getRank()), 
            "Serialized rank should be a valid RankTier enum value");
        
        // Property 5: The deserialized enum matches the original
        assertEquals(user.getRank(), RankTier.valueOf(response.getRank()),
            "Deserialized rank should match the original user rank");
    }

    /**
     * Generator for valid users with various rank tiers.
     * 
     * @return a User with a random valid rank tier
     */
    @Provide
    Arbitrary<User> validUserWithRank() {
        return Combinators.combine(
            Arbitaries.of(RankTier.values()).flat(),
            Arbitaries.integers().between(1, 30)
        ).flatMap((rankTier, level) -> {
            // Calculate appropriate shipScore for the level
            int shipScore = (level - 1) * 40 + Arbitaries.integers().between(0, 39).sample();
            
            return Arbitaries.strings().alpha().ofMinLength(10).map(id -> {
                User user = new User();
                user.setId(id);
                user.setShipScore(shipScore);
                user.setLevel(level);
                user.setRank(rankTier);
                user.setLastShipActivityAt(Instant.now());
                return user;
            });
        });
    }

    /**
     * Generator for valid users with valid shipScore/level/rank combinations.
     * 
     * @return a User with consistent shipScore, level, and rank
     */
    @Provide
    Arbitrary<User> validUser() {
        return Combinators.combine(
            Arbitaries.integers().between(0, 2000),
            Arbitaries.integers().between(1, 30)
        ).flatMap((shipScore, level) -> {
            // Calculate expected level from shipScore
            int expectedLevel = Math.min(30, (shipScore / 40) + 1);
            
            // Calculate appropriate rank from level
            RankTier rank = RankTier.fromLevel(expectedLevel);
            
            return Arbitaries.strings().alpha().ofMinLength(10).map(id -> {
                User user = new User();
                user.setId(id);
                user.setShipScore(shipScore);
                user.setLevel(expectedLevel);
                user.setRank(rank);
                user.setLastShipActivityAt(Instant.now());
                return user;
            });
        });
    }
}
