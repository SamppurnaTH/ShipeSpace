package com.shipspace.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Audit logging service for tracking important system events.
 * 
 * <p>This service provides asynchronous audit logging for events such as:
 * <ul>
 *   <li>Rank changes</li>
 *   <li>Level changes</li>
 *   <li>Score applications</li>
 *   <li>User authentication events</li>
 *   <li>System operations</li>
 * </ul>
 * 
 * <p>Audit logs are stored asynchronously to minimize impact on
 * main application performance.
 */
@Slf4j
@Service
public class AuditLogService {

    // In-memory buffer for audit events (in production, use Kafka/RabbitMQ)
    private final ConcurrentLinkedQueue<AuditEvent> eventBuffer = new ConcurrentLinkedQueue<>();

    /**
     * Audit event types for the rank progression system.
     */
    public enum AuditEventType {
        RANK_CHANGED,
        LEVEL_CHANGED,
        SCORE_APPLIED,
        SCORE_DECAY_APPLIED,
        USER_LOGIN,
        USER_REGISTER,
        PROJECT_CREATED,
        PROJECT_UPDATED,
        COLLAB_ACCEPTED,
        QUALITY_COMMENT
    }

    /**
     * Data class representing an audit event.
     */
    @Data
    @Builder
    public static class AuditEvent {
        private String eventId;
        private AuditEventType eventType;
        private String userId;
        private String entityId;
        private Object previousState;
        private Object newState;
        private String metadata;
        private Instant timestamp;
        private String sourceIp;
        private String userAgent;
    }

    /**
     * Logs a rank change event asynchronously.
     * 
     * @param userId the user whose rank changed
     * @param previousRank the previous rank tier
     * @param newRank the new rank tier
     * @param level the current level
     * @param score the current score
     */
    @Async
    public void logRankChange(
            String userId,
            String previousRank,
            String newRank,
            int level,
            int score
    ) {
        AuditEvent event = AuditEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType(AuditEventType.RANK_CHANGED)
                .userId(userId)
                .previousState(previousRank)
                .newState(newRank)
                .metadata(String.format("{\"level\": %d, \"score\": %d}", level, score))
                .timestamp(Instant.now())
                .build();
        
        bufferEvent(event);
        log.info("AUDIT: Rank changed for user {} from {} to {} (Level: {}, Score: {})",
                userId, previousRank, newRank, level, score);
    }

    /**
     * Logs a level change event asynchronously.
     * 
     * @param userId the user whose level changed
     * @param previousLevel the previous level
     * @param newLevel the new level
     * @param score the current score
     */
    @Async
    public void logLevelChange(
            String userId,
            int previousLevel,
            int newLevel,
            int score
    ) {
        AuditEvent event = AuditEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType(AuditEventType.LEVEL_CHANGED)
                .userId(userId)
                .previousState(String.valueOf(previousLevel))
                .newState(String.valueOf(newLevel))
                .metadata(String.format("{\"score\": %d}", score))
                .timestamp(Instant.now())
                .build();
        
        bufferEvent(event);
        log.info("AUDIT: Level changed for user {} from {} to {} (Score: {})",
                userId, previousLevel, newLevel, score);
    }

    /**
     * Logs a score application event asynchronously.
     * 
     * @param userId the user who received the score
     * @param eventType the type of score event
     * @param points the points awarded
     * @param entityId the related entity ID
     * @param success whether the application was successful
     */
    @Async
    public void logScoreApplication(
            String userId,
            String eventType,
            int points,
            String entityId,
            boolean success
    ) {
        AuditEvent event = AuditEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType(AuditEventType.SCORE_APPLIED)
                .userId(userId)
                .entityId(entityId)
                .newState(String.format("{\"eventType\": \"%s\", \"points\": %d, \"success\": %b}",
                        eventType, points, success))
                .timestamp(Instant.now())
                .build();
        
        bufferEvent(event);
        log.debug("AUDIT: Score applied to user {} - {}: {} points (success: {})",
                userId, eventType, points, success);
    }

    /**
     * Logs a score decay application event asynchronously.
     * 
     * @param userId the user whose score was decayed
     * @param previousScore the score before decay
     * @param newScore the score after decay
     * @param decayPercentage the percentage applied
     */
    @Async
    public void logScoreDecay(
            String userId,
            int previousScore,
            int newScore,
            double decayPercentage
    ) {
        AuditEvent event = AuditEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType(AuditEventType.SCORE_DECAY_APPLIED)
                .userId(userId)
                .previousState(String.valueOf(previousScore))
                .newState(String.valueOf(newScore))
                .metadata(String.format("{\"decayPercentage\": %.2f}", decayPercentage))
                .timestamp(Instant.now())
                .build();
        
        bufferEvent(event);
        log.info("AUDIT: Score decay applied to user {}: {} -> {} ({:.1f}%)",
                userId, previousScore, newScore, decayPercentage);
    }

    /**
     * Buffers an audit event for later persistence.
     * 
     * <p>In production, this would send events to a message queue
     * (Kafka, RabbitMQ) or write to a dedicated audit log store.
     * 
     * @param event the audit event to buffer
     */
    private void bufferEvent(AuditEvent event) {
        eventBuffer.offer(event);
        
        // Flush buffer if it gets too large (prevent memory issues)
        if (eventBuffer.size() > 1000) {
            flushEvents();
        }
    }

    /**
     * Flushes buffered events to persistent storage.
     * 
     * <p>This method should be called periodically or on shutdown.
     * In production, implement actual persistence to database/queue.
     */
    public void flushEvents() {
        AuditEvent event;
        while ((event = eventBuffer.poll()) != null) {
            // In production: persist to database, Elasticsearch, or send to message queue
            log.debug("Flushing audit event: {}", event.getEventId());
        }
    }

    /**
     * Gets buffered event count.
     * 
     * @return the number of events currently in the buffer
     */
    public int getBufferedEventCount() {
        return eventBuffer.size();
    }
}
