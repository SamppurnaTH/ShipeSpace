package com.shipspace.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScoreEventType enumeration.
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>Each event type returns the correct point value</li>
 *   <li>PROJECT_UPDATED has a daily cap of 5</li>
 *   <li>Other event types have no daily cap</li>
 * </ul>
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 2.4, 3.1, 3.2, 3.3, 3.4</a>
 */
class ScoreEventTypeTest {

    // Test each event type returns correct point value
    
    @Test
    @DisplayName("PROJECT_CREATED should award 50 points")
    void testProjectCreatedPoints() {
        assertEquals(50, ScoreEventType.PROJECT_CREATED.getPoints());
    }
    
    @Test
    @DisplayName("PROJECT_UPDATED should award 20 points")
    void testProjectUpdatedPoints() {
        assertEquals(20, ScoreEventType.PROJECT_UPDATED.getPoints());
    }
    
    @Test
    @DisplayName("COLLAB_ACCEPTED should award 10 points")
    void testCollabAcceptedPoints() {
        assertEquals(10, ScoreEventType.COLLAB_ACCEPTED.getPoints());
    }
    
    @Test
    @DisplayName("QUALITY_COMMENT should award 5 points")
    void testQualityCommentPoints() {
        assertEquals(5, ScoreEventType.QUALITY_COMMENT.getPoints());
    }
    
    // Test PROJECT_UPDATED has daily cap of 5
    
    @Test
    @DisplayName("PROJECT_UPDATED should have a daily cap")
    void testProjectUpdatedHasDailyCap() {
        assertTrue(ScoreEventType.PROJECT_UPDATED.hasDailyCap());
    }
    
    @Test
    @DisplayName("PROJECT_UPDATED daily cap should be 5")
    void testProjectUpdatedDailyCapValue() {
        assertEquals(5, ScoreEventType.PROJECT_UPDATED.getDailyCap());
    }
    
    // Test other event types have no daily cap
    
    @Test
    @DisplayName("PROJECT_CREATED should not have a daily cap")
    void testProjectCreatedNoDailyCap() {
        assertFalse(ScoreEventType.PROJECT_CREATED.hasDailyCap());
    }
    
    @Test
    @DisplayName("PROJECT_CREATED daily cap should be Integer.MAX_VALUE")
    void testProjectCreatedDailyCapValue() {
        assertEquals(Integer.MAX_VALUE, ScoreEventType.PROJECT_CREATED.getDailyCap());
    }
    
    @Test
    @DisplayName("COLLAB_ACCEPTED should not have a daily cap")
    void testCollabAcceptedNoDailyCap() {
        assertFalse(ScoreEventType.COLLAB_ACCEPTED.hasDailyCap());
    }
    
    @Test
    @DisplayName("COLLAB_ACCEPTED daily cap should be Integer.MAX_VALUE")
    void testCollabAcceptedDailyCapValue() {
        assertEquals(Integer.MAX_VALUE, ScoreEventType.COLLAB_ACCEPTED.getDailyCap());
    }
    
    @Test
    @DisplayName("QUALITY_COMMENT should not have a daily cap")
    void testQualityCommentNoDailyCap() {
        assertFalse(ScoreEventType.QUALITY_COMMENT.hasDailyCap());
    }
    
    @Test
    @DisplayName("QUALITY_COMMENT daily cap should be Integer.MAX_VALUE")
    void testQualityCommentDailyCapValue() {
        assertEquals(Integer.MAX_VALUE, ScoreEventType.QUALITY_COMMENT.getDailyCap());
    }
    
    // Test enum completeness
    
    @Test
    @DisplayName("ScoreEventType should have exactly 4 values")
    void testEnumValueCount() {
        ScoreEventType[] eventTypes = ScoreEventType.values();
        assertEquals(4, eventTypes.length);
    }
    
    @Test
    @DisplayName("ScoreEventType should contain all expected event types")
    void testEnumContainsAllEventTypes() {
        ScoreEventType[] eventTypes = ScoreEventType.values();
        assertEquals(ScoreEventType.PROJECT_CREATED, eventTypes[0]);
        assertEquals(ScoreEventType.PROJECT_UPDATED, eventTypes[1]);
        assertEquals(ScoreEventType.COLLAB_ACCEPTED, eventTypes[2]);
        assertEquals(ScoreEventType.QUALITY_COMMENT, eventTypes[3]);
    }
    
    // Test point values are positive
    
    @Test
    @DisplayName("All event types should have positive point values")
    void testAllPointValuesArePositive() {
        for (ScoreEventType eventType : ScoreEventType.values()) {
            assertTrue(eventType.getPoints() > 0, 
                eventType.name() + " should have positive points");
        }
    }
    
    // Test daily cap consistency
    
    @Test
    @DisplayName("hasDailyCap() should be consistent with getDailyCap()")
    void testDailyCapConsistency() {
        for (ScoreEventType eventType : ScoreEventType.values()) {
            if (eventType.hasDailyCap()) {
                assertTrue(eventType.getDailyCap() < Integer.MAX_VALUE,
                    eventType.name() + " with daily cap should have finite cap value");
            } else {
                assertEquals(Integer.MAX_VALUE, eventType.getDailyCap(),
                    eventType.name() + " without daily cap should return Integer.MAX_VALUE");
            }
        }
    }
}
