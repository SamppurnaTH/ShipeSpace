package com.shipspace.model;

import org.junit.jupiter.api.Test;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and property-based tests for BehavioralSignal model.
 * Tests model structure, enums, and correctness properties.
 */
class BehavioralSignalTest {

    @Test
    void testBehavioralSignalCreationWithBuilder() {
        // Given
        Instant now = Instant.now();
        List<String> techTags = Arrays.asList("React", "TypeScript");

        // When
        BehavioralSignal signal = BehavioralSignal.builder()
                .id("signal123")
                .userId("user789")
                .signalType(BehavioralSignal.SignalType.LIKE)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(techTags)
                .builderId("builder123")
                .durationSeconds(null)
                .timestamp(now)
                .build();

        // Then
        assertNotNull(signal);
        assertEquals("signal123", signal.getId());
        assertEquals("user789", signal.getUserId());
        assertEquals(BehavioralSignal.SignalType.LIKE, signal.getSignalType());
        assertEquals("ship456", signal.getContentId());
        assertEquals(BehavioralSignal.ContentType.SHIP, signal.getContentType());
        assertEquals(2, signal.getTechTags().size());
        assertTrue(signal.getTechTags().contains("React"));
        assertTrue(signal.getTechTags().contains("TypeScript"));
        assertEquals("builder123", signal.getBuilderId());
        assertNull(signal.getDurationSeconds());
        assertEquals(now, signal.getTimestamp());
    }

    @Test
    void testBehavioralSignalDefaultValues() {
        // When
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.VIEW)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .build();

        // Then
        assertNotNull(signal.getTechTags());
        assertTrue(signal.getTechTags().isEmpty());
        assertNotNull(signal.getTimestamp());
    }

    @Test
    void testSignalType_AllValues() {
        // Test that all expected signal types exist
        BehavioralSignal.SignalType[] types = BehavioralSignal.SignalType.values();
        
        assertEquals(5, types.length);
        assertTrue(Arrays.asList(types).contains(BehavioralSignal.SignalType.LIKE));
        assertTrue(Arrays.asList(types).contains(BehavioralSignal.SignalType.COMMENT));
        assertTrue(Arrays.asList(types).contains(BehavioralSignal.SignalType.VIEW));
        assertTrue(Arrays.asList(types).contains(BehavioralSignal.SignalType.FOLLOW));
        assertTrue(Arrays.asList(types).contains(BehavioralSignal.SignalType.SKIP));
    }

    @Test
    void testContentType_AllValues() {
        // Test that all expected content types exist
        BehavioralSignal.ContentType[] types = BehavioralSignal.ContentType.values();
        
        assertEquals(2, types.length);
        assertTrue(Arrays.asList(types).contains(BehavioralSignal.ContentType.SHIP));
        assertTrue(Arrays.asList(types).contains(BehavioralSignal.ContentType.PROJECT));
    }

    @Test
    void testLikeSignal() {
        // Given
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.LIKE)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(Arrays.asList("React", "Node.js"))
                .builderId("builder789")
                .build();

        // Then
        assertEquals(BehavioralSignal.SignalType.LIKE, signal.getSignalType());
        assertNull(signal.getDurationSeconds()); // Duration not applicable for LIKE
    }

    @Test
    void testCommentSignal() {
        // Given
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.COMMENT)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(Arrays.asList("Python", "Django"))
                .builderId("builder789")
                .build();

        // Then
        assertEquals(BehavioralSignal.SignalType.COMMENT, signal.getSignalType());
        assertNull(signal.getDurationSeconds()); // Duration not applicable for COMMENT
    }

    @Test
    void testViewSignal() {
        // Given
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.VIEW)
                .contentId("project789")
                .contentType(BehavioralSignal.ContentType.PROJECT)
                .techTags(Arrays.asList("AI", "Machine Learning"))
                .builderId("builder456")
                .durationSeconds(45)
                .build();

        // Then
        assertEquals(BehavioralSignal.SignalType.VIEW, signal.getSignalType());
        assertEquals(45, signal.getDurationSeconds());
    }

    @Test
    void testFollowSignal() {
        // Given
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.FOLLOW)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(Arrays.asList("Go", "Kubernetes"))
                .builderId("builder789")
                .build();

        // Then
        assertEquals(BehavioralSignal.SignalType.FOLLOW, signal.getSignalType());
        assertEquals("builder789", signal.getBuilderId());
    }

    @Test
    void testSkipSignal() {
        // Given
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.SKIP)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(Arrays.asList("Java", "Spring"))
                .build();

        // Then
        assertEquals(BehavioralSignal.SignalType.SKIP, signal.getSignalType());
    }

    @Test
    void testShipContentType() {
        // Given
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.LIKE)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .build();

        // Then
        assertEquals(BehavioralSignal.ContentType.SHIP, signal.getContentType());
    }

    @Test
    void testProjectContentType() {
        // Given
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.VIEW)
                .contentId("project789")
                .contentType(BehavioralSignal.ContentType.PROJECT)
                .durationSeconds(30)
                .build();

        // Then
        assertEquals(BehavioralSignal.ContentType.PROJECT, signal.getContentType());
    }

    @Test
    void testTechTagsEmptyList() {
        // Given
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.LIKE)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(new ArrayList<>())
                .build();

        // Then
        assertNotNull(signal.getTechTags());
        assertTrue(signal.getTechTags().isEmpty());
    }

    @Test
    void testTechTagsMultipleTags() {
        // Given
        List<String> tags = Arrays.asList("React", "TypeScript", "Node.js", "MongoDB");
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.COMMENT)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(tags)
                .build();

        // Then
        assertEquals(4, signal.getTechTags().size());
        assertTrue(signal.getTechTags().containsAll(tags));
    }

    @Test
    void testTimestampDefaultValue() {
        // Given
        Instant before = Instant.now();
        
        // When
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.LIKE)
                .contentId("ship456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .build();
        
        Instant after = Instant.now();

        // Then
        assertNotNull(signal.getTimestamp());
        assertTrue(!signal.getTimestamp().isBefore(before));
        assertTrue(!signal.getTimestamp().isAfter(after));
    }

    // Property-Based Tests

    @Property
    void allSignalTypes_canBeCreated(
            @ForAll("signalTypes") BehavioralSignal.SignalType signalType,
            @ForAll("contentTypes") BehavioralSignal.ContentType contentType) {
        // When
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(signalType)
                .contentId("content456")
                .contentType(contentType)
                .build();

        // Then
        assertNotNull(signal);
        assertEquals(signalType, signal.getSignalType());
        assertEquals(contentType, signal.getContentType());
        assertNotNull(signal.getTechTags());
        assertNotNull(signal.getTimestamp());
    }

    @Property
    void techTags_preserveAllElements(
            @ForAll("techTagLists") List<String> techTags) {
        // When
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.LIKE)
                .contentId("content456")
                .contentType(BehavioralSignal.ContentType.SHIP)
                .techTags(techTags)
                .build();

        // Then
        assertEquals(techTags.size(), signal.getTechTags().size());
        assertTrue(signal.getTechTags().containsAll(techTags));
    }

    @Property
    void viewSignals_canHaveAnyPositiveDuration(
            @ForAll("positiveDurations") int durationSeconds) {
        // When
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(BehavioralSignal.SignalType.VIEW)
                .contentId("content456")
                .contentType(BehavioralSignal.ContentType.PROJECT)
                .durationSeconds(durationSeconds)
                .build();

        // Then
        assertNotNull(signal);
        assertEquals(durationSeconds, signal.getDurationSeconds());
        assertTrue(signal.getDurationSeconds() > 0);
    }

    @Property
    void signals_alwaysHaveTimestamp(
            @ForAll("signalTypes") BehavioralSignal.SignalType signalType,
            @ForAll("contentTypes") BehavioralSignal.ContentType contentType) {
        // When
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId("user123")
                .signalType(signalType)
                .contentId("content456")
                .contentType(contentType)
                .build();

        // Then
        assertNotNull(signal.getTimestamp());
        assertTrue(!signal.getTimestamp().isAfter(Instant.now()));
    }

    @Property
    void signals_preserveAllFields(
            @ForAll("userIds") String userId,
            @ForAll("signalTypes") BehavioralSignal.SignalType signalType,
            @ForAll("contentIds") String contentId,
            @ForAll("contentTypes") BehavioralSignal.ContentType contentType,
            @ForAll("builderIds") String builderId) {
        // When
        BehavioralSignal signal = BehavioralSignal.builder()
                .userId(userId)
                .signalType(signalType)
                .contentId(contentId)
                .contentType(contentType)
                .builderId(builderId)
                .build();

        // Then
        assertEquals(userId, signal.getUserId());
        assertEquals(signalType, signal.getSignalType());
        assertEquals(contentId, signal.getContentId());
        assertEquals(contentType, signal.getContentType());
        assertEquals(builderId, signal.getBuilderId());
    }

    // Arbitraries for property-based tests

    @Provide
    Arbitrary<BehavioralSignal.SignalType> signalTypes() {
        return Arbitraries.of(BehavioralSignal.SignalType.values());
    }

    @Provide
    Arbitrary<BehavioralSignal.ContentType> contentTypes() {
        return Arbitraries.of(BehavioralSignal.ContentType.values());
    }

    @Provide
    Arbitrary<List<String>> techTagLists() {
        return Arbitraries.of("React", "Python", "Java", "TypeScript", "Go", 
                "Node.js", "MongoDB", "PostgreSQL", "Docker", "Kubernetes")
                .list()
                .ofMinSize(0)
                .ofMaxSize(10);
    }

    @Provide
    Arbitrary<Integer> positiveDurations() {
        return Arbitraries.integers().between(11, 3600); // 11 seconds to 1 hour
    }

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(24);
    }

    @Provide
    Arbitrary<String> contentIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(24);
    }

    @Provide
    Arbitrary<String> builderIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(24);
    }
}
