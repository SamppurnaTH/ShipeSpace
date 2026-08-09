package com.shipspace.controller;

import com.shipspace.model.Report;
import com.shipspace.model.User;
import com.shipspace.repository.ReportRepository;
import com.shipspace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @GetMapping("/reports")
    public ResponseEntity<Page<Report>> listReports(
            @RequestParam(defaultValue = "PENDING") Report.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Report> reports = reportRepository.findByStatus(status, PageRequest.of(page, size));
        return ResponseEntity.ok(reports);
    }

    @PatchMapping("/reports/{reportId}/resolve")
    public ResponseEntity<Report> resolveReport(
            @PathVariable String reportId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new com.shipspace.exception.ResourceNotFoundException("Report not found: " + reportId));

        String resolution = body.getOrDefault("status", "DISMISSED");
        String note = body.getOrDefault("note", "");

        report.setStatus(Report.Status.valueOf(resolution));
        report.setResolvedBy(userDetails.getUsername());
        report.setResolutionNote(note);
        report.setResolvedAt(Instant.now());

        reportRepository.save(report);
        log.info("Admin {} resolved report {} with status {}", userDetails.getUsername(), reportId, resolution);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/users")
    public ResponseEntity<Page<User>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<User> getUser(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.shipspace.exception.ResourceNotFoundException("User not found: " + userId));
        return ResponseEntity.ok(user);
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

        boolean ban = Boolean.parseBoolean(body.getOrDefault("banned", "true"));
        user.setBanned(ban);
        userRepository.save(user);
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
            user.setRoles(Set.copyOf(roles));
            userRepository.save(user);
            log.info("Admin {} updated roles for user {} to {}", adminDetails.getUsername(), userId, roles);
        }
        return ResponseEntity.ok(Map.of("userId", userId, "roles", user.getRoles()));
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
