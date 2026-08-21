package com.shipspace.controller;

import com.shipspace.model.User;
import com.shipspace.repository.AuditLogRepository;
import com.shipspace.repository.ReportRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Admin controller data accuracy tests")
class AdminControllerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AdminController controller = new AdminController(
            userRepository,
            reportRepository,
            auditLogRepository,
            auditLogService);

    @Test
    void banUser_blocksSelfBan() {
        UserDetails admin = org.springframework.security.core.userdetails.User
                .withUsername("admin-1")
                .password("")
                .roles("ADMIN")
                .build();

        assertThatThrownBy(() -> controller.banUser("admin-1", Map.of("banned", "true"), admin))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot ban or unban");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void banUser_updatesTargetUser() {
        UserDetails admin = org.springframework.security.core.userdetails.User
                .withUsername("admin-1")
                .password("")
                .roles("ADMIN")
                .build();
        User target = User.builder().id("user-1").username("builder").banned(false).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));

        var response = controller.banUser("user-1", Map.of("banned", "true"), admin);

        assertThat(response.getBody()).containsEntry("userId", "user-1").containsEntry("banned", true);
        assertThat(target.isBanned()).isTrue();
        verify(userRepository).save(target);
        verify(auditLogService).logAdminUserBanChange("admin-1", "user-1", false, true);
    }

    @Test
    void getUser_returnsAdminVisibleFields() {
        User target = User.builder()
                .id("user-1")
                .githubId("12345")
                .username("builder")
                .roles(java.util.Set.of("USER", "ADMIN"))
                .banned(true)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));

        var response = controller.getUser("user-1");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getGithubId()).isEqualTo("12345");
        assertThat(response.getBody().getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(response.getBody().isBanned()).isTrue();
    }

    @Test
    void getStats_returnsRepositoryCountsWithoutDerivedPlaceholders() {
        when(reportRepository.countByStatus(com.shipspace.model.Report.Status.PENDING)).thenReturn(3L);
        when(userRepository.count()).thenReturn(12L);

        var response = controller.getStats();

        assertThat(response.getBody())
                .containsEntry("pendingReports", 3L)
                .containsEntry("totalUsers", 12L)
                .hasSize(2);
    }

    @Test
    void listUsers_ordersNewestFirstForRecentDashboardPanel() {
        User target = User.builder().id("user-1").username("builder").build();
        when(userRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(target)));

        controller.listUsers(0, 10);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt"))
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listReports_ordersNewestFirstForPendingDashboardPanel() {
        com.shipspace.model.Report report = com.shipspace.model.Report.builder()
                .id("report-1")
                .status(com.shipspace.model.Report.Status.PENDING)
                .build();
        when(reportRepository.findByStatus(
                org.mockito.ArgumentMatchers.eq(com.shipspace.model.Report.Status.PENDING),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));

        controller.listReports(com.shipspace.model.Report.Status.PENDING, 0, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findByStatus(
                org.mockito.ArgumentMatchers.eq(com.shipspace.model.Report.Status.PENDING),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt"))
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }
}
