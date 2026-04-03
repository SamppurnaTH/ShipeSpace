package com.shipspace.service;

import com.shipspace.dto.NotificationResponse;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Notification;
import com.shipspace.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for notification operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MongoTemplate mongoTemplate;

    public Page<NotificationResponse> getNotifications(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification", notificationId);
        }
        notification.setRead(true);
        notification = notificationRepository.save(notification);
        return toResponse(notification);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        // Single bulk updateMany — no heap allocation, no N individual saves
        Query query = new Query(Criteria.where("userId").is(userId).and("read").is(false));
        Update update = new Update().set("read", true);
        mongoTemplate.updateMulti(query, update, Notification.class);
        log.debug("Bulk marked all notifications as read for user {}", userId);
    }

    /**
     * Creates a notification. Called when a ship is liked, commented, or user is followed.
     */
    @Transactional
    public Notification createNotification(String userId, String type, String text,
            String actorId, String actorAvatarUrl, String targetId, String targetView) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .text(text)
                .actorId(actorId)
                .actorAvatarUrl(actorAvatarUrl)
                .targetId(targetId)
                .targetView(targetView)
                .read(false)
                .build();
        notification = notificationRepository.save(notification);
        log.debug("Created notification {} for user {}", notification.getId(), userId);
        return notification;
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .text(n.getText())
                .actorId(n.getActorId())
                .actorAvatarUrl(n.getActorAvatarUrl())
                .targetId(n.getTargetId())
                .targetView(n.getTargetView())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
