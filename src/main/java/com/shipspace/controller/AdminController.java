package com.shipspace.controller;

import com.shipspace.dto.AdminUserResponse;
import com.shipspace.dto.AdminAuditLogResponse;
import com.shipspace.model.Report;
import com.shipspace.model.User;
import com.shipspace.repository.AuditLogRepository;
import com.shipspace.repository.ReportRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AdminAuditLogResponse>> listAuditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AdminAuditLogResponse> logs = (eventType == null || eventType.isBlank()
                ? auditLogRepository.findAll(pageable)
                : auditLogRepository.findByEventType(eventType, pageable))
                .map(AdminAuditLogResponse::from);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/reports")
    public ResponseEntity<Page<Report>> listReports(
            @RequestParam(defaultValue = "PENDING") Report.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Report> reports = reportRepository.findByStatus(
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(reports);
    }

    @PatchMapping("/reports/{reportId}/resolve")
    public ResponseEntity<Report> resolveReport(
            @PathVariable String reportId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new com.shipspace.exception.ResourceNotFoundException("Report not found: " + reportId));

        Report.Status resolution = parseReportResolutionStatus(body.getOrDefault("status", "DISMISSED"));
        String note = body.getOrDefault("note", "");

        Report.Status previousStatus = report.getStatus();
        if (previousStatus != Report.Status.PENDING) {
            if (previousStatus == resolution) {
                return ResponseEntity.ok(report);
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(report);
        }

        report.setStatus(resolution);
        report.setResolvedBy(userDetails.getUsername());
        report.setResolutionNote(note);
        report.setResolvedAt(Instant.now());

        reportRepository.save(report);
        auditLogService.logAdminReportResolution(
                userDetails.getUsername(),
                reportId,
                previousStatus.name(),
                resolution.name(),
                note);
        log.info("Admin {} resolved report {} with status {}", userDetails.getUsername(), reportId, resolution);
        return ResponseEntity.ok(report);
    }

    private Report.Status parseReportResolutionStatus(String value) {
        try {
            Report.Status status = Report.Status.valueOf(value);
            if (status == Report.Status.PENDING) {
                throw new IllegalArgumentException("Reports cannot be resolved back to PENDING");
            }
            return status;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported report status: " + value);
        }
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AdminUserResponse> users = userRepository.findAll(
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(AdminUserResponse::from);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.shipspace.exception.ResourceNotFoundException("User not found: " + userId));
        return ResponseEntity.ok(AdminUserResponse.from(user));
    }

    @PatchMapping("/users/{userId}/ban")
    public ResponseEntity<Map<String, Object>> banUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails adminDetails) {
        if (adminDetails != null && userId.equals(adminDetails.getUsername())) {
            throw new AccessDeniedException("Admins cannot ban or unban their own account");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.shipspace.exception.ResourceNotFoundException("User not found: " + userId));

        boolean previousBanned = user.isBanned();
        boolean ban = Boolean.parseBoolean(body.getOrDefault("banned", "true"));
        user.setBanned(ban);
        userRepository.save(user);
        auditLogService.logAdminUserBanChange(adminDetails.getUsername(), userId, previousBanned, ban);
        log.info("Admin {} {} user {}", adminDetails.getUsername(), ban ? "banned" : "unbanned", userId);
        return ResponseEntity.ok(Map.of("userId", userId, "banned", ban));
    }

    @PatchMapping("/users/{userId}/roles")
    public ResponseEntity<Map<String, Object>> updateRoles(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails adminDetails) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.shipspace.exception.ResourceNotFoundException("User not found: " + userId));

        @SuppressWarnings("unchecked")
        java.util.List<String> roles = (java.util.List<String>) body.get("roles");
        if (roles != null) {
            Set<String> previousRoles = user.getRoles();
            Set<String> requestedRoles = Set.copyOf(roles);
            boolean removesOwnAdminRole = adminDetails != null
                    && userId.equals(adminDetails.getUsername())
                    && hasAdminRole(previousRoles)
                    && !hasAdminRole(requestedRoles);
            if (removesOwnAdminRole) {
                throw new AccessDeniedException("Admins cannot remove their own ADMIN role");
            }

            user.setRoles(requestedRoles);
            userRepository.save(user);
            auditLogService.logAdminRoleChange(
                    adminDetails.getUsername(),
                    userId,
                    previousRoles.toString(),
                    user.getRoles().toString());
            log.info("Admin {} updated roles for user {} to {}", adminDetails.getUsername(), userId, roles);
        }
        return ResponseEntity.ok(Map.of("userId", userId, "roles", user.getRoles()));
    }

    private boolean hasAdminRole(Set<String> roles) {
        return roles != null && (roles.contains("ADMIN") || roles.contains("ROLE_ADMIN"));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        long pendingReports = reportRepository.countByStatus(Report.Status.PENDING);
        long totalUsers = userRepository.count();
        return ResponseEntity.ok(Map.of(
                "pendingReports", pendingReports,
                "totalUsers", totalUsers
        ));
    }
}
