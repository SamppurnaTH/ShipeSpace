package com.shipspace.service;

import com.shipspace.exception.AuditLogException;
import com.shipspace.model.AuditLog;
import com.shipspace.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogService service = new AuditLogService(auditLogRepository);

    @Test
    void logAdminUserBanChange_persistsMandatoryAuditSynchronously() {
        service.logAdminUserBanChange("admin-1", "user-1", false, true);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditLogService.ADMIN_USER_BANNED);
        assertThat(event.getUserId()).isEqualTo("admin-1");
        assertThat(event.getEntityId()).isEqualTo("user-1");
        assertThat(event.getPreviousState()).isEqualTo("{\"banned\":false}");
        assertThat(event.getNewState()).isEqualTo("{\"banned\":true}");
        assertThat(event.getMetadata()).isEqualTo("{\"entityType\":\"USER\"}");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void mandatoryAuditFailure_isSurfaced() {
        when(auditLogRepository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
                .thenThrow(new RuntimeException("mongo unavailable"));

        assertThatThrownBy(() -> service.logAdminUserBanChange("admin-1", "user-1", false, true))
                .isInstanceOf(AuditLogException.class)
                .hasMessageContaining(AuditLogService.ADMIN_USER_BANNED);
    }
}
