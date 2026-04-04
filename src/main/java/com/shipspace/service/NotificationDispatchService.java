package com.shipspace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Fire-and-forget notification dispatcher.
 *
 * WHY THIS EXISTS:
 * Spring @Async only works when called through the Spring proxy — i.e., from a
 * DIFFERENT bean. Calling this.notifyAsync(...) inside EngagementService or
 * SocialService bypasses the proxy entirely and runs synchronously on the HTTP
 * thread, defeating the purpose of async processing.
 *
 * By extracting this into its own @Service bean and injecting it, every call
 * goes through the proxy and executes on the taskExecutor thread pool.
 *
 * Failures are swallowed intentionally — notification delivery is best-effort.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationService notificationService;

    /**
     * Asynchronously create a notification.
     * Runs on the taskExecutor thread pool.
     * Any exception is caught and logged — never propagates to the caller.
     */
    @Async("taskExecutor")
    public void dispatch(String recipientId, String type, String text,
                         String actorId, String actorAvatarUrl,
                         String targetId, String targetView) {
        try {
            notificationService.createNotification(
                    recipientId, type, text, actorId, actorAvatarUrl, targetId, targetView);
        } catch (Exception e) {
            log.error("Async notification dispatch failed [type={}, recipient={}]: {}",
                    type, recipientId, e.getMessage());
        }
    }
}
