package com.shipspace.demo;

import com.shipspace.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Demo reset endpoint — only active when demo.mode=true.
 *
 * POST /api/demo/reset  — wipes all demo data and re-seeds
 * DELETE /api/demo/data — wipes demo data only (no re-seed)
 * GET /api/demo/status  — returns demo mode status and counts
 *
 * SECURITY: This controller only exists when demo.mode=true.
 * In production (demo.mode=false), these endpoints return 404.
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "demo.mode", havingValue = "true")
public class DemoResetController {

    private final UserRepository       userRepository;
    private final ProjectRepository    projectRepository;
    private final ShipRepository       shipRepository;
    private final LikeRepository       likeRepository;
    private final CommentRepository    commentRepository;
    private final FollowingRepository  followingRepository;
    private final NotificationRepository notificationRepository;
    private final MongoTemplate        mongoTemplate;
    private final DemoDataSeeder       seeder;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        long demoUsers = countDemoUsers();
        long demoShips = demoUsers > 0 ? shipRepository.count() : 0;
        return ResponseEntity.ok(Map.of(
                "demoMode", true,
                "demoUsers", demoUsers,
                "totalShips", demoShips,
                "totalProjects", projectRepository.count()
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        log.warn("[DEMO] Reset triggered — wiping and re-seeding demo data");
        wipeDemoData();
        seeder.seed();
        return ResponseEntity.ok(Map.of("status", "reset complete"));
    }

    @DeleteMapping("/data")
    public ResponseEntity<Map<String, String>> wipe() {
        log.warn("[DEMO] Wipe triggered — removing all demo data");
        wipeDemoData();
        return ResponseEntity.ok(Map.of("status", "demo data wiped"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void wipeDemoData() {
        // Find all demo user IDs (githubId starts with "demo_")
        Query demoUserQuery = new Query(Criteria.where("githubId").regex("^demo_"));
        List<com.shipspace.model.User> demoUsers =
                mongoTemplate.find(demoUserQuery, com.shipspace.model.User.class);

        List<String> demoUserIds = demoUsers.stream()
                .map(com.shipspace.model.User::getId).toList();

        if (demoUserIds.isEmpty()) {
            log.info("[DEMO] No demo data found to wipe");
            return;
        }

        // Find demo project IDs
        Query projectQuery = new Query(Criteria.where("userId").in(demoUserIds));
        List<com.shipspace.model.Project> demoProjects =
                mongoTemplate.find(projectQuery, com.shipspace.model.Project.class);
        List<String> demoProjectIds = demoProjects.stream()
                .map(com.shipspace.model.Project::getId).toList();

        // Find demo ship IDs
        Query shipQuery = new Query(Criteria.where("userId").in(demoUserIds));
        List<com.shipspace.model.Ship> demoShips =
                mongoTemplate.find(shipQuery, com.shipspace.model.Ship.class);
        List<String> demoShipIds = demoShips.stream()
                .map(com.shipspace.model.Ship::getId).toList();

        // Delete in dependency order
        if (!demoShipIds.isEmpty()) {
            mongoTemplate.remove(new Query(Criteria.where("shipId").in(demoShipIds)),
                    com.shipspace.model.Like.class);
            mongoTemplate.remove(new Query(Criteria.where("shipId").in(demoShipIds)),
                    com.shipspace.model.Comment.class);
        }
        if (!demoUserIds.isEmpty()) {
            mongoTemplate.remove(new Query(Criteria.where("followerId").in(demoUserIds)),
                    com.shipspace.model.Following.class);
            mongoTemplate.remove(new Query(Criteria.where("followeeId").in(demoUserIds)),
                    com.shipspace.model.Following.class);
            mongoTemplate.remove(new Query(Criteria.where("userId").in(demoUserIds)),
                    com.shipspace.model.Notification.class);
        }
        if (!demoShipIds.isEmpty()) {
            mongoTemplate.remove(new Query(Criteria.where("_id").in(demoShipIds)),
                    com.shipspace.model.Ship.class);
        }
        if (!demoProjectIds.isEmpty()) {
            mongoTemplate.remove(new Query(Criteria.where("_id").in(demoProjectIds)),
                    com.shipspace.model.Project.class);
        }
        mongoTemplate.remove(demoUserQuery, com.shipspace.model.User.class);

        log.info("[DEMO] Wiped {} users, {} projects, {} ships",
                demoUserIds.size(), demoProjectIds.size(), demoShipIds.size());
    }

    private long countDemoUsers() {
        Query q = new Query(Criteria.where("githubId").regex("^demo_"));
        return mongoTemplate.count(q, com.shipspace.model.User.class);
    }
}
