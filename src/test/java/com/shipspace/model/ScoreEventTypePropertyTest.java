package com.shipspace.model;

import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ScoreEventType enumeration.
 * 
 * <p>These tests verify the correctness properties of the ScoreEventType enum
 * across all event types using property-based testing with jqwik.
 * 
 * <p>Property 3: Score Event Point Values
 * For any ScoreEventType, calling getPoints() should return the correct hardcoded value:
 * PROJECT_CREATED=50, PROJECT_UPDATED=20, COLLAB_ACCEPTED=10, QUALITY_COMMENT=5.
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 2.4, 3.1, 3.2, 3.3, 3.4</a>
 */
class ScoreEventTypePropertyTest {

    /**
     * Property 3: Score Event Point Values
     * 
     * **Validates: Requirements 2.4, 3.1, 3.2, 3.3, 3.4**
     * 
     * For any ScoreEventType, calling getPoints() should return the correct hardcoded value:
     * - PROJECT_CREATED = 50 points
     * - PROJECT_UPDATED = 20 points
     * - COLLAB_ACCEPTED = 10 points
     * - QUALITY_COMMENT = 5 points
     * 
     * This property verifies that:
     * 1. Each event type returns its correct point value
     * 2. Point values are consistent across all invocations
     * 3. The mapping between event types and point values is correct
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 3: Score Event Point Values")
    void scoreEventPointValuesAreCorrect(@ForAll("scoreEventType") ScoreEventType eventType) {
        int points = eventType.getPoints();
        
        // Verify the correct point value for each event type
        switch (eventType) {
            case PROJECT_CREATED:
                assertEquals(50, points,
                        "PROJECT_CREATED should award 50 points");
                break;
            case PROJECT_UPDATED:
                assertEquals(20, points,
                        "PROJECT_UPDATED should award 20 points");
                break;
            case COLLAB_ACCEPTED:
                assertEquals(10, points,
                        "COLLAB_ACCEPTED should award 10 points");
                break;
            case QUALITY_COMMENT:
                assertEquals(5, points,
                        "QUALITY_COMMENT should award 5 points");
                break;
            default:
                fail("Unexpected ScoreEventType: " + eventType);
        }
        
        // Verify all point values are positive
        assertTrue(points > 0,
                String.format("%s should have positive point value, but got %d", eventType, points));
    }

    /**
     * Property 3 (Extended): Point values are immutable
     * 
     * **Validates: Requirements 2.4, 3.1, 3.2, 3.3, 3.4**
     * 
     * For any ScoreEventType, calling getPoints() multiple times should always
     * return the same value (immutability).
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 3: Point values are immutable")
    void pointValuesAreImmutable(@ForAll("scoreEventType") ScoreEventType eventType) {
        int firstCall = eventType.getPoints();
        int secondCall = eventType.getPoints();
        int thirdCall = eventType.getPoints();
        
        assertEquals(firstCall, secondCall,
                String.format("%s point value should be consistent across calls", eventType));
        assertEquals(secondCall, thirdCall,
                String.format("%s point value should be consistent across calls", eventType));
        assertEquals(firstCall, thirdCall,
                String.format("%s point value should be consistent across calls", eventType));
    }

    /**
     * Property 3 (Extended): Point values follow expected ordering
     * 
     * **Validates: Requirements 2.4, 3.1, 3.2, 3.3, 3.4**
     * 
     * Verify that point values follow the expected ordering:
     * PROJECT_CREATED > PROJECT_UPDATED > COLLAB_ACCEPTED > QUALITY_COMMENT
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 3: Point values follow expected ordering")
    void pointValuesFollowExpectedOrdering(@ForAll("scoreEventType") ScoreEventType eventType) {
        // Verify the ordering relationship between event types
        int projectCreatedPoints = ScoreEventType.PROJECT_CREATED.getPoints();
        int projectUpdatedPoints = ScoreEventType.PROJECT_UPDATED.getPoints();
        int collabAcceptedPoints = ScoreEventType.COLLAB_ACCEPTED.getPoints();
        int qualityCommentPoints = ScoreEventType.QUALITY_COMMENT.getPoints();
        
        // PROJECT_CREATED should have the highest point value
        assertTrue(projectCreatedPoints > projectUpdatedPoints,
                "PROJECT_CREATED points should be greater than PROJECT_UPDATED points");
        assertTrue(projectCreatedPoints > collabAcceptedPoints,
                "PROJECT_CREATED points should be greater than COLLAB_ACCEPTED points");
        assertTrue(projectCreatedPoints > qualityCommentPoints,
                "PROJECT_CREATED points should be greater than QUALITY_COMMENT points");
        
        // PROJECT_UPDATED should have more points than COLLAB_ACCEPTED and QUALITY_COMMENT
        assertTrue(projectUpdatedPoints > collabAcceptedPoints,
                "PROJECT_UPDATED points should be greater than COLLAB_ACCEPTED points");
        assertTrue(projectUpdatedPoints > qualityCommentPoints,
                "PROJECT_UPDATED points should be greater than QUALITY_COMMENT points");
        
        // COLLAB_ACCEPTED should have more points than QUALITY_COMMENT
        assertTrue(collabAcceptedPoints > qualityCommentPoints,
                "COLLAB_ACCEPTED points should be greater than QUALITY_COMMENT points");
    }

    /**
     * Property 3 (Extended): All event types have distinct point values
     * 
     * **Validates: Requirements 2.4, 3.1, 3.2, 3.3, 3.4**
     * 
     * For any two different ScoreEventType values, they should have different point values.
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 3: All event types have distinct point values")
    void allEventTypesHaveDistinctPointValues(
            @ForAll("scoreEventType") ScoreEventType eventType1,
            @ForAll("scoreEventType") ScoreEventType eventType2
    ) {
        // If the event types are different, their point values should be different
        if (eventType1 != eventType2) {
            assertNotEquals(eventType1.getPoints(), eventType2.getPoints(),
                    String.format("%s and %s should have different point values", eventType1, eventType2));
        } else {
            // If they're the same event type, point values should be equal
            assertEquals(eventType1.getPoints(), eventType2.getPoints(),
                    String.format("Same event type %s should have same point value", eventType1));
        }
    }

    /**
     * Property 3 (Extended): Point values are within expected range
     * 
     * **Validates: Requirements 2.4, 3.1, 3.2, 3.3, 3.4**
     * 
     * For any ScoreEventType, the point value should be within a reasonable range
     * (between 1 and 100 for MVP).
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 3: Point values are within expected range")
    void pointValuesAreWithinExpectedRange(@ForAll("scoreEventType") ScoreEventType eventType) {
        int points = eventType.getPoints();
        
        // Verify point values are within reasonable bounds
        assertTrue(points >= 1,
                String.format("%s point value should be at least 1, but got %d", eventType, points));
        assertTrue(points <= 100,
                String.format("%s point value should be at most 100, but got %d", eventType, points));
    }

    // ========== Arbitraries (Generators) ==========

    /**
     * Generates arbitrary ScoreEventType values.
     * This will generate all four event types with equal probability.
     */
    @Provide
    Arbitrary<ScoreEventType> scoreEventType() {
        return Arbitraries.of(ScoreEventType.class);
    }
}
