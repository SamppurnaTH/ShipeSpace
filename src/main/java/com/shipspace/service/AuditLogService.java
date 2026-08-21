package com.shipspace.service;

import com.shipspace.exception.AuditLogException;
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

    public static final String ADMIN_USER_BANNED = "ADMIN_USER_BANNED";
    public static final String ADMIN_USER_UNBANNED = "ADMIN_USER_UNBANNED";
    public static final String ADMIN_USER_ROLES_UPDATED = "ADMIN_USER_ROLES_UPDATED";
    public static final String ADMIN_REPORT_RESOLVED = "ADMIN_REPORT_RESOLVED";

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

    public void logAdminUserBanChange(String adminUserId, String targetUserId, boolean previousBanned, boolean newBanned) {
        String eventType = newBanned ? ADMIN_USER_BANNED : ADMIN_USER_UNBANNED;
        persistStrict(AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .eventType(eventType)
                .userId(adminUserId)
                .entityId(targetUserId)
                .previousState(String.format("{\"banned\":%b}", previousBanned))
                .newState(String.format("{\"banned\":%b}", newBanned))
                .metadata("{\"entityType\":\"USER\"}")
                .timestamp(Instant.now())
                .build());
    }

    public void logAdminRoleChange(String adminUserId, String targetUserId, String previousRoles, String newRoles) {
        persistStrict(AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .eventType(ADMIN_USER_ROLES_UPDATED)
                .userId(adminUserId)
                .entityId(targetUserId)
                .previousState(previousRoles)
                .newState(newRoles)
                .metadata("{\"entityType\":\"USER\"}")
                .timestamp(Instant.now())
                .build());
    }

    public void logAdminReportResolution(
            String adminUserId,
            String reportId,
            String previousStatus,
            String newStatus,
            String note) {
        persistStrict(AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .eventType(ADMIN_REPORT_RESOLVED)
                .userId(adminUserId)
                .entityId(reportId)
                .previousState(previousStatus)
                .newState(newStatus)
                .metadata(String.format("{\"entityType\":\"REPORT\",\"note\":\"%s\"}", escapeJson(note)))
                .timestamp(Instant.now())
                .build());
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

    private void persistStrict(AuditLog event) {
        try {
            auditLogRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to persist mandatory audit event type={} userId={} entityId={}: {}",
                    event.getEventType(), event.getUserId(), event.getEntityId(), e.getMessage(), e);
            throw new AuditLogException("Failed to persist mandatory audit event " + event.getEventType(), e);
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
