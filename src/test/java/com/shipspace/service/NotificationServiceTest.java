package com.shipspace.service;

import com.shipspace.dto.NotificationResponse;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.Notification;
import com.shipspace.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private NotificationService notificationService;

    private Notification unreadNotification;
    private Notification readNotification;

    @BeforeEach
    void setUp() {
        unreadNotification = Notification.builder()
                .id("notification-1")
                .userId("user-1")
                .type("FOLLOW")
                .text("alex started following you")
                .actorId("actor-1")
                .actorAvatarUrl("https://example.com/avatar.png")
                .targetId("actor-1")
                .targetView("/profile/actor-1")
                .read(false)
                .createdAt(Instant.parse("2026-08-12T00:00:00Z"))
                .build();
        readNotification = Notification.builder()
                .id("notification-2")
                .userId("user-1")
                .type("LIKE")
                .text("alex liked your ship")
                .actorId("actor-1")
                .targetId("ship-1")
                .targetView("/ships/ship-1")
                .read(true)
                .createdAt(Instant.parse("2026-08-12T00:01:00Z"))
                .build();
    }

    @Test
    @DisplayName("getNotifications: lists only the current user's notifications with capped size and stable sort")
    void getNotifications_UsesCurrentUserAndStablePagination() {
        when(notificationRepository.findByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(readNotification, unreadNotification)));

        Page<NotificationResponse> response = notificationService.getNotifications("user-1", -1, 500);

        assertThat(response.getContent()).hasSize(2);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserId(eq("user-1"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().toString()).contains("createdAt: DESC", "id: DESC");
    }

    @Test
    @DisplayName("getUnreadCount: counts only current user's unread notifications")
    void getUnreadCount_UsesCurrentUser() {
        when(notificationRepository.countByUserIdAndReadFalse("user-1")).thenReturn(3L);

        assertThat(notificationService.getUnreadCount("user-1")).isEqualTo(3);
        verify(notificationRepository).countByUserIdAndReadFalse("user-1");
    }

    @Test
    @DisplayName("markAsRead: persists unread notification as read")
    void markAsRead_UnreadNotification_PersistsRead() {
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(unreadNotification));
        when(notificationRepository.save(unreadNotification)).thenReturn(unreadNotification);

        NotificationResponse response = notificationService.markAsRead("notification-1", "user-1");

        assertThat(response.isRead()).isTrue();
        verify(notificationRepository).save(unreadNotification);
    }

    @Test
    @DisplayName("markAsRead: repeated read is safe and does not save again")
    void markAsRead_AlreadyRead_IsIdempotent() {
        when(notificationRepository.findById("notification-2")).thenReturn(Optional.of(readNotification));

        NotificationResponse response = notificationService.markAsRead("notification-2", "user-1");

        assertThat(response.isRead()).isTrue();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("markAsRead: cannot mark another user's notification")
    void markAsRead_OtherUserNotification_NotFound() {
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(unreadNotification));

        assertThatThrownBy(() -> notificationService.markAsRead("notification-1", "user-2"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found");
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("markAsRead: missing notification returns controlled not found")
    void markAsRead_MissingNotification_NotFound() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("missing", "user-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found");
    }

    @Test
    @DisplayName("markAllAsRead: updates only current user's unread notifications")
    void markAllAsRead_OnlyCurrentUserUnread() {
        notificationService.markAllAsRead("user-1");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(queryCaptor.capture(), updateCaptor.capture(), eq(Notification.class));
        String query = queryCaptor.getValue().toString();
        assertThat(query).contains("userId", "user-1", "read", "false");
        assertThat(updateCaptor.getValue().getUpdateObject().toJson()).contains("\"read\": true");
    }

    @Test
    @DisplayName("createNotification: persists unread notification with structured target")
    void createNotification_PersistsStructuredTarget() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId("notification-3");
            return notification;
        });

        Notification saved = notificationService.createNotification(
                "user-1",
                "COMMENT",
                "alex commented on your ship",
                "actor-1",
                "https://example.com/avatar.png",
                "ship-1",
                "/ships/ship-1");

        assertThat(saved.getId()).isEqualTo("notification-3");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getTargetView()).isEqualTo("/ships/ship-1");
        assertThat(saved.getTargetId()).isEqualTo("ship-1");
    }
}
