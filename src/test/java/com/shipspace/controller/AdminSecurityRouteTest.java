package com.shipspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipspace.config.BannedUserWriteFilter;
import com.shipspace.config.MockMvcContextPathCustomizer;
import com.shipspace.config.SecurityConfig;
import com.shipspace.dto.CurrentUserResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.model.AuditLog;
import com.shipspace.model.Report;
import com.shipspace.model.User;
import com.shipspace.repository.AuditLogRepository;
import com.shipspace.repository.ReportRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.security.JwtAuthenticationFilter;
import com.shipspace.security.OAuthStateService;
import com.shipspace.security.TokenBlacklistService;
import com.shipspace.service.AuthService;
import com.shipspace.service.AuditLogService;
import com.shipspace.service.RateLimitingService;
import com.shipspace.service.SocialService;
import com.shipspace.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminController.class, AuthController.class, SocialController.class},
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
                org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class
        },
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = com.shipspace.config.MongoAuditingConfig.class))
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        AdminSecurityRouteTest.BannedFilterTestConfig.class,
        JwtUtil.class,
        MockMvcContextPathCustomizer.class
})
@TestPropertySource(properties = {
        "jwt.secret=admin-security-route-test-secret-minimum-32-chars",
        "jwt.expiration=900000",
        "jwt.refresh-expiration=604800000"
})
@DisplayName("Admin route security tests")
class AdminSecurityRouteTest {

    private static final String JWT_SECRET = "admin-security-route-test-secret-minimum-32-chars";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ReportRepository reportRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private AuthService authService;

    @MockBean
    private RateLimitingService rateLimitingService;

    @MockBean
    private OAuthStateService oAuthStateService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private SocialService socialService;

    @TestConfiguration
    static class BannedFilterTestConfig {
        @Bean
        BannedUserWriteFilter bannedUserWriteFilter(UserRepository userRepository) {
            return new BannedUserWriteFilter(userRepository);
        }
    }

    @Test
    void adminRead_deniesMissingMalformedExpiredAndUserJwt() throws Exception {
        when(reportRepository.countByStatus(Report.Status.PENDING)).thenReturn(0L);
        when(userRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer garbage"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", bearer(expiredToken("user-1", "builder", Set.of("USER")))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", bearer(userToken("user-1"))))
                .andExpect(status().isForbidden());

        verify(reportRepository, never()).countByStatus(any());
        verify(userRepository, never()).count();
    }

    @Test
    void adminRead_allowsAdminJwtThroughSecurity() throws Exception {
        when(reportRepository.countByStatus(Report.Status.PENDING)).thenReturn(2L);
        when(userRepository.count()).thenReturn(7L);

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", bearer(adminToken("admin-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReports").value(2))
                .andExpect(jsonPath("$.totalUsers").value(7));
    }

    @Test
    void adminMutation_deniesUserJwtAndAllowsAdminJwtThroughSecurity() throws Exception {
        User target = User.builder()
                .id("user-1")
                .username("builder")
                .roles(Set.of("USER"))
                .banned(false)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/admin/users/user-1/ban")
                        .header("Authorization", bearer(userToken("user-2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("banned", "true"))))
                .andExpect(status().isForbidden());

        assertThat(target.isBanned()).isFalse();

        mockMvc.perform(patch("/api/admin/users/user-1/ban")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("banned", "true"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.banned").value(true));

        assertThat(target.isBanned()).isTrue();
    }

    @Test
    void allCurrentAdminRoutesDenyUserJwt() throws Exception {
        String userJwt = bearer(userToken("user-1"));

        mockMvc.perform(get("/api/admin/users").header("Authorization", userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users/user-2").header("Authorization", userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports").header("Authorization", userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/reports/report-1/resolve")
                        .header("Authorization", userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DISMISSED"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/user-2/roles")
                        .header("Authorization", userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roles", List.of("USER", "ADMIN")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void representativeAdminRoutesAllowAdminJwtThroughSecurity() throws Exception {
        User target = User.builder()
                .id("user-2")
                .username("builder")
                .roles(Set.of("USER"))
                .banned(false)
                .build();
        Report report = Report.builder()
                .id("report-1")
                .status(Report.Status.PENDING)
                .build();

        when(userRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(target), PageRequest.of(0, 20), 1));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findByStatus(eq(Report.Status.PENDING), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String adminJwt = bearer(adminToken("admin-1"));

        mockMvc.perform(get("/api/admin/users").header("Authorization", adminJwt))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users/user-2").header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-2"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/api/admin/reports").header("Authorization", adminJwt))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/reports/report-1/resolve")
                        .header("Authorization", adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DISMISSED", "note", "Reviewed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
        mockMvc.perform(patch("/api/admin/users/user-2/roles")
                        .header("Authorization", adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roles", List.of("USER", "ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray());
        verify(auditLogService).logAdminRoleChange(
                eq("admin-1"),
                eq("user-2"),
                eq(Set.of("USER").toString()),
                anyString());
    }

    @Test
    void auditLogsRoute_deniesUserJwtAndAllowsAdminJwtWithDtoResponse() throws Exception {
        AuditLog event = AuditLog.builder()
                .id("audit-1")
                .eventType(AuditLogService.ADMIN_USER_BANNED)
                .userId("admin-1")
                .entityId("user-1")
                .previousState("{\"banned\":false}")
                .newState("{\"banned\":true}")
                .metadata("{\"entityType\":\"USER\"}")
                .build();
        when(auditLogRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1));
        when(auditLogRepository.findByEventType(eq(AuditLogService.ADMIN_USER_BANNED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", bearer(userToken("user-1"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", bearer(adminToken("admin-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("audit-1"))
                .andExpect(jsonPath("$.content[0].eventType").value(AuditLogService.ADMIN_USER_BANNED))
                .andExpect(jsonPath("$.content[0].userId").value("admin-1"))
                .andExpect(jsonPath("$.content[0].entityId").value("user-1"))
                .andExpect(jsonPath("$.content[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.content[0].refreshToken").doesNotExist())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .queryParam("eventType", AuditLogService.ADMIN_USER_BANNED)
                        .header("Authorization", bearer(adminToken("admin-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value(AuditLogService.ADMIN_USER_BANNED));
    }

    @Test
    void adminReportResolve_persistsSupportedResolutionStatuses() throws Exception {
        String adminJwt = bearer(adminToken("admin-1"));

        for (Report.Status status : List.of(Report.Status.REVIEWED, Report.Status.DISMISSED, Report.Status.ACTION_TAKEN)) {
            Report report = Report.builder()
                    .id("report-" + status.name())
                    .status(Report.Status.PENDING)
                    .build();
            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

            mockMvc.perform(patch("/api/admin/reports/{reportId}/resolve", report.getId())
                            .header("Authorization", adminJwt)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "status", status.name(),
                                    "note", "Reviewed as " + status.name()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(status.name()))
                    .andExpect(jsonPath("$.resolvedBy").value("admin-1"))
                    .andExpect(jsonPath("$.resolutionNote").value("Reviewed as " + status.name()))
                    .andExpect(jsonPath("$.resolvedAt").exists());

        assertThat(report.getStatus()).isEqualTo(status);
        assertThat(report.getResolvedBy()).isEqualTo("admin-1");
        assertThat(report.getResolvedAt()).isNotNull();
        verify(auditLogService).logAdminReportResolution(
                "admin-1",
                report.getId(),
                Report.Status.PENDING.name(),
                status.name(),
                "Reviewed as " + status.name());
        }
    }

    @Test
    void adminReportResolve_rejectsPendingAsResolutionTarget() throws Exception {
        Report report = Report.builder()
                .id("report-1")
                .status(Report.Status.PENDING)
                .build();
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));

        mockMvc.perform(patch("/api/admin/reports/report-1/resolve")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PENDING"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        assertThat(report.getStatus()).isEqualTo(Report.Status.PENDING);
        verify(reportRepository, never()).save(report);
    }

    @Test
    void adminReportResolve_handlesTerminalDuplicatesPredictably() throws Exception {
        Report report = Report.builder()
                .id("report-1")
                .status(Report.Status.DISMISSED)
                .resolvedBy("admin-previous")
                .resolutionNote("Already handled")
                .build();
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));

        mockMvc.perform(patch("/api/admin/reports/report-1/resolve")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DISMISSED", "note", "Duplicate"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.resolvedBy").value("admin-previous"))
                .andExpect(jsonPath("$.resolutionNote").value("Already handled"));

        mockMvc.perform(patch("/api/admin/reports/report-1/resolve")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTION_TAKEN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        verify(reportRepository, never()).save(report);
    }

    @Test
    void adminBanRoute_persistsAndBannedUserCanReadAndLogoutButCannotWrite() throws Exception {
        User admin = User.builder().id("admin-1").username("admin").roles(Set.of("USER", "ADMIN")).build();
        User target = User.builder().id("user-1").username("builder").roles(Set.of("USER")).banned(false).build();

        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(socialService.getFollowing(eq("user-1"), any()))
                .thenReturn(new PageImpl<UserProfileResponse>(List.of(), PageRequest.of(0, 20), 0));
        doNothing().when(authService).logout(any(), any());

        mockMvc.perform(patch("/api/admin/users/user-1/ban")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("banned", "true"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.banned").value(true));

        assertThat(target.isBanned()).isTrue();
        verify(auditLogService).logAdminUserBanChange("admin-1", "user-1", false, true);

        mockMvc.perform(get("/api/social/following")
                        .header("Authorization", bearer(userToken("user-1"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", bearer(userToken("user-1"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/social/follow/user-2")
                        .header("Authorization", bearer(userToken("user-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BANNED_USER"));

        verify(socialService, never()).followUser("user-1", "user-2");
    }

    @Test
    void adminCannotSelfBanOrSelfUnbanThroughRoute() throws Exception {
        User admin = User.builder().id("admin-1").username("admin").roles(Set.of("USER", "ADMIN")).banned(false).build();
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        mockMvc.perform(patch("/api/admin/users/admin-1/ban")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("banned", "true"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/admin-1/ban")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("banned", "false"))))
                .andExpect(status().isForbidden());

        assertThat(admin.isBanned()).isFalse();
        verify(userRepository, never()).save(admin);
    }

    @Test
    void adminCannotRemoveOwnAdminRoleThroughRoute() throws Exception {
        User admin = User.builder().id("admin-1").username("admin").roles(Set.of("USER", "ADMIN")).banned(false).build();
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        mockMvc.perform(patch("/api/admin/users/admin-1/roles")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roles", List.of("USER")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(admin.getRoles()).contains("ADMIN");
        verify(userRepository, never()).save(admin);
        verify(auditLogService, never()).logAdminRoleChange(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void adminUnbanRouteRestoresWriteAccess() throws Exception {
        User admin = User.builder().id("admin-1").username("admin").roles(Set.of("USER", "ADMIN")).build();
        User target = User.builder().id("user-1").username("builder").roles(Set.of("USER")).banned(false).build();

        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/admin/users/user-1/ban")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("banned", "true"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banned").value(true));

        assertThat(target.isBanned()).isTrue();
        verify(auditLogService).logAdminUserBanChange("admin-1", "user-1", false, true);

        mockMvc.perform(patch("/api/admin/users/user-1/ban")
                        .header("Authorization", bearer(adminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("banned", "false"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banned").value(false));

        assertThat(target.isBanned()).isFalse();
        verify(auditLogService).logAdminUserBanChange("admin-1", "user-1", true, false);

        mockMvc.perform(post("/api/social/follow/user-2")
                        .header("Authorization", bearer(userToken("user-1"))))
                .andExpect(status().isNoContent());

        verify(socialService).followUser("user-1", "user-2");
    }

    @Test
    void authMeReturnsCurrentUserRolesWithoutSensitiveTokens() throws Exception {
        when(authService.getCurrentUserResponse("user-1"))
                .thenReturn(CurrentUserResponse.builder()
                        .id("user-1")
                        .username("builder")
                        .roles(Set.of("USER"))
                        .build());
        when(authService.getCurrentUserResponse("admin-1"))
                .thenReturn(CurrentUserResponse.builder()
                        .id("admin-1")
                        .username("admin")
                        .roles(Set.of("USER", "ADMIN"))
                        .build());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(userToken("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(adminToken("admin-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    private String userToken(String userId) {
        return jwtUtil.generateToken(userId, "builder", Set.of("USER"));
    }

    private String adminToken(String userId) {
        return jwtUtil.generateToken(userId, "admin", Set.of("USER", "ADMIN"));
    }

    private String expiredToken(String userId, String username, Set<String> roles) {
        JwtUtil expiring = new JwtUtil();
        ReflectionTestUtils.setField(expiring, "secret", JWT_SECRET);
        ReflectionTestUtils.setField(expiring, "expiration", -1000L);
        ReflectionTestUtils.setField(expiring, "refreshExpiration", 604800000L);
        expiring.init();
        return expiring.generateToken(userId, username, roles);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
