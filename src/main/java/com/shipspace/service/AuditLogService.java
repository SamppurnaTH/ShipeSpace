package com.shipspace.service;

import com.shipspace.model.AuditLog;
import com.shipspace.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent audit logging service.
 * All writes are async (fire-and-forget) to avoid blocking the caller.
 * Events are stored in MongoDB with a 365-day TTL index.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async("taskExecutor")
    public void logRankChange(String userId, String previousRank, String newRank, int level, int score) {
        persist(AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .eventType("RANK_CHANGED")
                .userId(userId)
                .previousState(previousRank)
                .newState(newRank)
                .metadata(String.format("{\"level\":%d,\"score\":%d}", level, score))
                .timestamp(Instant.now())
                .build());
        log.info("AUDIT: Rank changed for user {} from {} to {} (level={}, score={})",
                userId, previousRank, newRank, level, score);
    }

    @Async("taskExecutor")
    public void logLevelChange(String userId, int previousLevel, int newLevel, int score) {
        persist(AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .eventType("LEVEL_CHANGED")
                .userId(userId)
                .previousState(String.valueOf(previousLevel))
                .newState(String.valueOf(newLevel))
                .metadata(String.format("{\"score\":%d}", score))
                .timestamp(Instant.now())
                .build());
        log.info("AUDIT: Level changed for user {} from {} to {} (score={})",
                userId, previousLevel, newLevel, score);
    }

    @Async("taskExecutor")
    public void logScoreApplication(String userId, String eventType, int points,
                                    String entityId, boolean success) {
        persist(AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .eventType("SCORE_APPLIED")
                .userId(userId)
                .entityId(entityId)
                .newState(String.format("{\"eventType\":\"%s\",\"points\":%d,\"success\":%b}",
                        eventType, points, success))
                .timestamp(Instant.now())
                .build());
    }

    @Async("taskExecutor")
    public void logScoreDecay(String userId, int previousScore, int newScore, double decayPercentage) {
        persist(AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .eventType("SCORE_DECAY_APPLIED")
                .userId(userId)
                .previousState(String.valueOf(previousScore))
                .newState(String.valueOf(newScore))
                .metadata(String.format("{\"decayPercentage\":%.2f}", decayPercentage))
                .timestamp(Instant.now())
                .build());
        log.info("AUDIT: Score decay for user {}: {} → {}", userId, previousScore, newScore);
    }

    private void persist(AuditLog event) {
        try {
            auditLogRepository.save(event);
        } catch (Exception e) {
            // Log but never propagate — audit failure must not break the caller
            log.error("Failed to persist audit event type={} userId={}: {}",
                    event.getEventType(), event.getUserId(), e.getMessage());
        }
    }
}
