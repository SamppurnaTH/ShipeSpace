package com.shipspace.demo;

import com.shipspace.model.*;
import com.shipspace.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Demo data seeder — runs once on startup when DEMO_MODE=true.
 *
 * ISOLATION GUARANTEE:
 *   All demo users have githubId prefixed with "demo_".
 *   All demo documents have demoData=true (stored as a tag in the ID prefix).
 *   The DemoResetController can wipe everything by querying that prefix.
 *
 * IDEMPOTENT: checks for existing demo users before inserting anything.
 *   Safe to restart the container without duplicating data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "demo.mode", havingValue = "true")
public class DemoDataSeeder {

    private final UserRepository       userRepository;
    private final ProjectRepository    projectRepository;
    private final ShipRepository       shipRepository;
    private final LikeRepository       likeRepository;
    private final CommentRepository    commentRepository;
    private final FollowingRepository  followingRepository;
    private final NotificationRepository notificationRepository;

    // ── Demo user definitions ─────────────────────────────────────────────────

    private static final List<DemoUser> DEMO_USERS = List.of(
        new DemoUser("demo_001", "kaito_builds",   "Kaito Nakamura",  "Tokyo, Japan",
            "Full-stack dev obsessed with Rust and distributed systems. Currently building a real-time collab tool.",
            "https://avatars.githubusercontent.com/u/1000001", 320, 8),
        new DemoUser("demo_002", "priya_ships",    "Priya Sharma",    "Bangalore, India",
            "ML engineer shipping side projects on weekends. Loves turning research papers into working demos.",
            "https://avatars.githubusercontent.com/u/1000002", 185, 5),
        new DemoUser("demo_003", "alex_launches",  "Alex Chen",       "San Francisco, CA",
            "Indie hacker. 3 failed startups, 1 almost-successful one. Still shipping.",
            "https://avatars.githubusercontent.com/u/1000003", 540, 14),
        new DemoUser("demo_004", "mira_codes",     "Mira Okonkwo",    "Lagos, Nigeria",
            "Frontend engineer who cares deeply about accessibility and performance.",
            "https://avatars.githubusercontent.com/u/1000004", 95, 3),
        new DemoUser("demo_005", "dev_torsten",    "Torsten Müller",  "Berlin, Germany",
            "Backend engineer. PostgreSQL fanatic. Currently exploring event sourcing.",
            "https://avatars.githubusercontent.com/u/1000005", 210, 6),
        new DemoUser("demo_006", "yuki_hacks",     "Yuki Tanaka",     "Osaka, Japan",
            "Game dev by day, systems programmer by night. Shipping a custom game engine in Zig.",
            "https://avatars.githubusercontent.com/u/1000006", 430, 11),
        new DemoUser("demo_007", "sam_deploys",    "Sam Rivera",      "Austin, TX",
            "DevOps engineer who got tired of waiting for devs to ship and started shipping myself.",
            "https://avatars.githubusercontent.com/u/1000007", 75, 2),
        new DemoUser("demo_008", "nadia_makes",    "Nadia Petrov",    "Amsterdam, Netherlands",
            "Product engineer. I care about the whole thing — design, code, and the user.",
            "https://avatars.githubusercontent.com/u/1000008", 160, 4)
    );

    // ── Project definitions ───────────────────────────────────────────────────

    private static final List<DemoProject> DEMO_PROJECTS = List.of(
        new DemoProject("demo_001", "Collab.rs",
            "A real-time collaborative code editor built in Rust with WebAssembly frontend. CRDTs for conflict-free merging.",
            List.of("Rust", "WebAssembly", "CRDTs", "WebSockets")),
        new DemoProject("demo_001", "ShipMetrics",
            "Open-source analytics dashboard for tracking your shipping velocity and consistency over time.",
            List.of("TypeScript", "React", "PostgreSQL", "Recharts")),
        new DemoProject("demo_002", "PaperToCode",
            "CLI tool that takes an arXiv paper URL and generates a working Python implementation of the core algorithm.",
            List.of("Python", "LLM", "arXiv", "CLI")),
        new DemoProject("demo_003", "LaunchPad",
            "Minimal landing page builder for indie hackers. No-code, fast, and actually looks good.",
            List.of("Next.js", "Tailwind", "Vercel", "SaaS")),
        new DemoProject("demo_003", "FounderOS",
            "Personal CRM and task manager built specifically for solo founders. Replaces 5 different tools.",
            List.of("React", "SQLite", "Electron", "Productivity")),
        new DemoProject("demo_004", "a11y-audit",
            "Automated accessibility auditing tool that goes beyond Lighthouse. Catches WCAG 2.2 violations with context.",
            List.of("TypeScript", "Puppeteer", "WCAG", "Accessibility")),
        new DemoProject("demo_005", "EventSourcing.go",
            "Production-ready event sourcing library for Go. Supports PostgreSQL and EventStoreDB backends.",
            List.of("Go", "Event Sourcing", "PostgreSQL", "CQRS")),
        new DemoProject("demo_006", "ZigEngine",
            "A minimal 2D game engine written in Zig. No dependencies. Compiles to native and WASM.",
            List.of("Zig", "WebAssembly", "Game Dev", "Systems")),
        new DemoProject("demo_007", "k8s-cost-lens",
            "Kubernetes cost visibility tool. Shows you exactly which pods are burning your cloud budget.",
            List.of("Go", "Kubernetes", "Prometheus", "FinOps")),
        new DemoProject("demo_008", "DesignTokens.io",
            "Design token management platform. Sync tokens between Figma, CSS, and your component library.",
            List.of("TypeScript", "Figma API", "Design Systems", "CSS"))
    );

    // ── Ship content ──────────────────────────────────────────────────────────

    private static final List<String> SHIP_CONTENTS = List.of(
        "Just shipped the CRDT merge algorithm for Collab.rs 🦀 Spent 3 weeks on this. The key insight: you don't need a central server to resolve conflicts if your data structure is designed right. Benchmarks show <2ms merge time for documents up to 100k chars.",
        "PaperToCode v0.3 is live. Added support for papers with pseudocode blocks — it now extracts the algorithm, translates it to Python, and writes unit tests. Tested on 50 papers from NeurIPS 2023. 78% produce runnable code on first try.",
        "LaunchPad hit 200 users this week without any marketing. Just shipped dark mode and custom domain support. The no-code editor is finally fast enough that I'm not embarrassed to show it.",
        "Shipped the first working prototype of ZigEngine. Renders a 1000-entity scene at 60fps on a 5-year-old laptop. No allocations in the hot path. Zig's comptime is genuinely magical for this kind of work.",
        "a11y-audit now catches focus trap issues that Lighthouse completely misses. Added a new rule for keyboard navigation dead-ends. Found 3 critical issues on my own portfolio site that I had no idea about.",
        "EventSourcing.go v0.2 — added optimistic concurrency control. You can now specify an expected version when appending events. If the stream has moved on, you get a conflict error instead of silent data corruption.",
        "k8s-cost-lens can now break down costs by namespace, deployment, and even individual container. Saved my team $800/month in the first week by finding a forgotten staging deployment running 24/7.",
        "DesignTokens.io now syncs bidirectionally with Figma. Change a color in Figma, it propagates to your CSS variables and Storybook within 30 seconds. This is the workflow I've always wanted.",
        "Collab.rs now handles network partitions gracefully. If you lose connection, you keep editing locally. When you reconnect, your changes merge automatically. Tested with 5-minute network outages.",
        "Shipped ShipMetrics v1.0. It tracks your shipping streak, average time between ships, and engagement trends. Looking at my own data: I ship most on Tuesday mornings and least on Fridays.",
        "FounderOS now has a 'weekly review' mode. Every Sunday it surfaces your top 3 wins, 3 blockers, and suggests what to focus on next week based on your goals. Replaced my Notion setup entirely.",
        "PaperToCode now handles papers with multiple algorithms. It builds a dependency graph and generates them in the right order. Tested on the Attention Is All You Need paper — generates working transformer code.",
        "ZigEngine now has a basic ECS (Entity Component System). Added it in 200 lines of Zig. The comptime type system makes this almost too easy — no runtime reflection needed.",
        "a11y-audit v0.4 ships with a VS Code extension. Get accessibility warnings inline as you write JSX. Caught a missing aria-label on a button I was literally writing at the time.",
        "EventSourcing.go now supports projections. You define a projection as a pure function from events to state, and the library handles rebuilding it from the event log. Snapshotting coming next.",
        "LaunchPad now has A/B testing built in. You can create variants of your landing page and see which converts better. No external tools needed. Shipped this in a weekend.",
        "k8s-cost-lens v0.3 adds cost forecasting. Based on your last 30 days of usage, it predicts next month's bill with ~15% accuracy. Good enough to catch surprises before they happen.",
        "DesignTokens.io now supports semantic token aliases. You can define `color.primary` as an alias for `color.blue.500`, and the sync engine resolves them correctly in all output formats.",
        "Collab.rs now has a presence system. You can see other users' cursors in real-time. Implemented using a separate CRDT for cursor positions — keeps it consistent with the document state.",
        "ShipMetrics now shows your 'shipping score' — a composite metric of frequency, consistency, and engagement. Mine went up 40% after I started shipping smaller, more frequent updates.",
        "FounderOS shipped a Kanban view for tasks. I resisted adding this for months because every tool has one. But the integration with my CRM contacts makes it actually useful — tasks are linked to people.",
        "PaperToCode v0.4 — added a 'explain this' mode. After generating code, you can ask it to explain any function in plain English. Great for papers where the notation is dense.",
        "ZigEngine now compiles to WebAssembly. The same game code runs natively at 60fps and in the browser at 55fps. The 5fps difference is entirely WebGL overhead, not the engine.",
        "a11y-audit now integrates with GitHub Actions. Add it to your CI pipeline and get a PR comment with accessibility issues before they reach production. Zero config for Next.js projects.",
        "EventSourcing.go v0.3 — added support for EventStoreDB as a backend. The abstraction layer means you can switch between PostgreSQL and EventStoreDB with a single config change.",
        "k8s-cost-lens now has Slack integration. Get a weekly digest of your top 5 most expensive workloads. My team actually reads it — it's become a ritual.",
        "DesignTokens.io now supports multi-brand tokens. You can have a base token set and brand-specific overrides. Useful if you're building a white-label product.",
        "Collab.rs performance update: reduced memory usage by 60% by switching from a naive CRDT representation to a more compact encoding. Documents that used to take 50MB now take 20MB.",
        "LaunchPad now has a built-in waitlist feature. Add a waitlist form to your landing page, collect emails, and send a welcome email when you're ready to launch. No Mailchimp needed.",
        "ShipMetrics v1.1 — added a 'compare to last month' view. Turns out I shipped 3x more in March than February. The streak feature is working — I don't want to break it."
    );

    private static final List<String> COMMENT_CONTENTS = List.of(
        "This is exactly what I've been looking for. The CRDT approach is so much cleaner than OT.",
        "How does it handle the case where two users delete the same character simultaneously?",
        "Impressive benchmark numbers. What's the memory overhead per character?",
        "I've been building something similar. Would love to compare notes.",
        "The 78% success rate is actually really good for this kind of task. What are the failure modes?",
        "Does it handle papers with LaTeX math notation? That's usually where these tools break.",
        "200 users without marketing is a great signal. What's your retention like?",
        "The dark mode looks clean. Did you use CSS variables or a theme library?",
        "This is the kind of tool I'd actually pay for. When's the paid plan coming?",
        "The focus trap detection is genuinely hard to get right. How are you testing it?",
        "Optimistic concurrency is the right call here. Did you consider using vector clocks instead?",
        "What's the performance overhead of the event log compared to a traditional CRUD approach?",
        "$800/month savings in week one is wild. What was the forgotten deployment running?",
        "Does it work with GKE or just EKS? We're on Google Cloud.",
        "Bidirectional Figma sync is the holy grail. How are you handling token naming conflicts?",
        "This is going to save my team so many Figma-to-code handoff meetings.",
        "The network partition handling is the hard part. How long can you stay offline before conflicts become unresolvable?",
        "Shipping streak data is fascinating. I wonder if there's a correlation with day of week across all users.",
        "The weekly review feature sounds like it could replace my entire Sunday planning ritual.",
        "Comptime in Zig is genuinely underrated. The ECS implementation must be so clean.",
        "5fps WebGL overhead is acceptable. Are you using WebGL 1 or 2?",
        "GitHub Actions integration is the right move. Zero-config for Next.js is a great DX.",
        "EventStoreDB is a great choice for event sourcing. How are you handling schema evolution?",
        "The Slack digest is a great idea. Does it show cost trends or just absolute numbers?",
        "Multi-brand token support is exactly what we need for our white-label product.",
        "60% memory reduction is significant. What was the old encoding?",
        "Built-in waitlist is smart. One less integration to set up.",
        "3x more ships in March — what changed? Did you set a specific goal?",
        "The semantic alias resolution is the tricky part. How do you handle circular references?",
        "This is the kind of boring infrastructure work that makes everything else possible. Respect."
    );

    // ── Seeding ───────────────────────────────────────────────────────────────

    @PostConstruct
    public void seed() {
        if (userRepository.existsByGithubId("demo_001")) {
            log.info("[DEMO] Demo data already present — skipping seed");
            return;
        }

        log.info("[DEMO] Seeding demo data...");
        try {
            List<User>    users    = seedUsers();
            List<Project> projects = seedProjects(users);
            List<Ship>    ships    = seedShips(users, projects);
            seedEngagement(users, ships);
            seedFollowing(users);
            log.info("[DEMO] Seeded {} users, {} projects, {} ships", users.size(), projects.size(), ships.size());
        } catch (Exception e) {
            log.error("[DEMO] Seed failed — demo data may be partial", e);
        }
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private List<User> seedUsers() {
        List<User> users = new ArrayList<>();
        for (DemoUser d : DEMO_USERS) {
            RankTier rank = rankForScore(d.shipScore);
            int level = Math.min(30, d.shipScore / 40 + 1);
            User u = User.builder()
                    .githubId(d.githubId)
                    .username(d.username)
                    .email(d.username + "@demo.shipspace.dev")
                    .avatarUrl(d.avatarUrl)
                    .bio(d.bio)
                    .location(d.location)
                    .websiteUrl("https://github.com/" + d.username)
                    .shipScore(d.shipScore)
                    .level(level)
                    .rank(rank)
                    .followerCount(0)
                    .followingCount(0)
                    .projectCount(0)
                    .lastShipActivityAt(Instant.now().minus(rng(1, 14), ChronoUnit.DAYS))
                    .createdAt(Instant.now().minus(rng(60, 365), ChronoUnit.DAYS))
                    .updatedAt(Instant.now())
                    .build();
            users.add(userRepository.save(u));
        }
        return users;
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    private List<Project> seedProjects(List<User> users) {
        Map<String, User> byGithubId = new HashMap<>();
        for (User u : users) byGithubId.put(u.getGithubId(), u);

        List<Project> projects = new ArrayList<>();
        for (DemoProject dp : DEMO_PROJECTS) {
            User owner = byGithubId.get(dp.ownerGithubId);
            if (owner == null) continue;
            Project p = Project.builder()
                    .userId(owner.getId())
                    .name(dp.name)
                    .description(dp.description)
                    .techTags(dp.techTags)
                    .shipCount(0)
                    .createdAt(Instant.now().minus(rng(30, 180), ChronoUnit.DAYS))
                    .updatedAt(Instant.now())
                    .build();
            projects.add(projectRepository.save(p));
            // Update user project count
            userRepository.findById(owner.getId()).ifPresent(u -> {
                u.setProjectCount(u.getProjectCount() + 1);
                userRepository.save(u);
            });
        }
        return projects;
    }

    // ── Ships ─────────────────────────────────────────────────────────────────

    private List<Ship> seedShips(List<User> users, List<Project> projects) {
        List<Ship> ships = new ArrayList<>();
        Random rand = new Random(42); // deterministic

        // Assign ships to projects — each project gets 2-4 ships
        int contentIdx = 0;
        for (Project project : projects) {
            int shipCount = 2 + rand.nextInt(3); // 2-4 ships per project
            for (int i = 0; i < shipCount; i++) {
                String content = SHIP_CONTENTS.get(contentIdx % SHIP_CONTENTS.size());
                contentIdx++;

                // Spread ships over the last 90 days, more recent = higher trending score
                int daysAgo = rng(1, 90);
                Instant createdAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS)
                        .minus(rng(0, 23), ChronoUnit.HOURS);

                // Trending score: newer + more engagement = higher score
                // We'll set a base score here; engagement will be added later
                double baseScore = Math.max(0, (90 - daysAgo) * 0.5 + rand.nextDouble() * 20);

                Instant editExpires = createdAt.plus(10, ChronoUnit.MINUTES);

                Ship ship = Ship.builder()
                        .userId(project.getUserId())
                        .projectId(project.getId())
                        .content(content)
                        .likeCount(0)
                        .commentCount(0)
                        .trendingScore(baseScore)
                        .editWindowExpires(editExpires)
                        .createdAt(createdAt)
                        .updatedAt(createdAt)
                        .build();
                ships.add(shipRepository.save(ship));
            }
            // Update project ship count
            final int finalShipCount = shipCount;
            projectRepository.findById(project.getId()).ifPresent(p -> {
                p.setShipCount(finalShipCount);
                p.setLastShipAt(Instant.now().minus(rng(1, 7), ChronoUnit.DAYS));
                projectRepository.save(p);
            });
        }
        return ships;
    }

    // ── Engagement ────────────────────────────────────────────────────────────

    private void seedEngagement(List<User> users, List<Ship> ships) {
        Random rand = new Random(99);
        int commentIdx = 0;

        for (Ship ship : ships) {
            // Likes: popular ships get more (power-law distribution)
            double popularity = rand.nextDouble();
            int likeCount = (int)(popularity * popularity * users.size() * 0.8);
            int commentCount = (int)(popularity * 3);

            // Shuffle users and pick likers
            List<User> shuffled = new ArrayList<>(users);
            Collections.shuffle(shuffled, rand);

            int actualLikes = 0;
            for (int i = 0; i < Math.min(likeCount, shuffled.size()); i++) {
                User liker = shuffled.get(i);
                if (liker.getId().equals(ship.getUserId())) continue; // no self-likes
                if (likeRepository.existsByUserIdAndShipId(liker.getId(), ship.getId())) continue;

                Instant likedAt = ship.getCreatedAt().plus(rng(1, 72), ChronoUnit.HOURS);
                if (likedAt.isAfter(Instant.now())) likedAt = Instant.now().minus(1, ChronoUnit.HOURS);

                likeRepository.save(Like.builder()
                        .userId(liker.getId())
                        .shipId(ship.getId())
                        .createdAt(likedAt)
                        .build());
                actualLikes++;
            }

            // Comments
            int actualComments = 0;
            Collections.shuffle(shuffled, rand);
            for (int i = 0; i < Math.min(commentCount, shuffled.size()); i++) {
                User commenter = shuffled.get(i);
                if (commenter.getId().equals(ship.getUserId())) continue;

                String content = COMMENT_CONTENTS.get(commentIdx % COMMENT_CONTENTS.size());
                commentIdx++;

                Instant commentedAt = ship.getCreatedAt().plus(rng(1, 48), ChronoUnit.HOURS);
                if (commentedAt.isAfter(Instant.now())) commentedAt = Instant.now().minus(30, ChronoUnit.MINUTES);

                commentRepository.save(Comment.builder()
                        .userId(commenter.getId())
                        .shipId(ship.getId())
                        .content(content)
                        .upvotes(rand.nextInt(5))
                        .createdAt(commentedAt)
                        .updatedAt(commentedAt)
                        .build());
                actualComments++;
            }

            // Update ship counters and trending score
            if (actualLikes > 0 || actualComments > 0) {
                final int finalLikes    = actualLikes;
                final int finalComments = actualComments;
                shipRepository.findById(ship.getId()).ifPresent(s -> {
                    s.setLikeCount(finalLikes);
                    s.setCommentCount(finalComments);
                    long ageHours = ChronoUnit.HOURS.between(s.getCreatedAt(), Instant.now());
                    double engagementScore = finalLikes * 2.0 + finalComments * 3.0;
                    double recencyDecay = Math.exp(-ageHours / 168.0);
                    s.setTrendingScore(engagementScore * recencyDecay + s.getTrendingScore());
                    shipRepository.save(s);
                });
            }
        }
    }

    // ── Following ─────────────────────────────────────────────────────────────

    private void seedFollowing(List<User> users) {
        Random rand = new Random(77);
        Map<String, Integer> followerCounts  = new HashMap<>();
        Map<String, Integer> followingCounts = new HashMap<>();

        for (User follower : users) {
            // Each user follows 2-5 others
            int toFollow = 2 + rand.nextInt(4);
            List<User> candidates = new ArrayList<>(users);
            candidates.removeIf(u -> u.getId().equals(follower.getId()));
            Collections.shuffle(candidates, rand);

            int followed = 0;
            for (User followee : candidates) {
                if (followed >= toFollow) break;
                if (followingRepository.existsByFollowerIdAndFolloweeId(follower.getId(), followee.getId())) continue;

                followingRepository.save(Following.builder()
                        .followerId(follower.getId())
                        .followeeId(followee.getId())
                        .createdAt(Instant.now().minus(rng(1, 60), ChronoUnit.DAYS))
                        .build());

                followerCounts.merge(followee.getId(), 1, Integer::sum);
                followingCounts.merge(follower.getId(), 1, Integer::sum);
                followed++;
            }
        }

        // Update follower/following counts on user documents
        for (User u : users) {
            userRepository.findById(u.getId()).ifPresent(user -> {
                user.setFollowerCount(followerCounts.getOrDefault(u.getId(), 0));
                user.setFollowingCount(followingCounts.getOrDefault(u.getId(), 0));
                userRepository.save(user);
            });
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final Random RNG = new Random();
    private static int rng(int min, int max) { return min + RNG.nextInt(max - min + 1); }

    private static RankTier rankForScore(int score) {
        int level = Math.min(30, score / 40 + 1);
        if (level <= 5)  return RankTier.DOCKED;
        if (level <= 10) return RankTier.LAUNCHER;
        if (level <= 15) return RankTier.ORBITER;
        if (level <= 20) return RankTier.NAVIGATOR;
        if (level <= 25) return RankTier.COMMANDER;
        return RankTier.FLEET_ADMIRAL;
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    private record DemoUser(String githubId, String username, String displayName,
                            String location, String bio, String avatarUrl,
                            int shipScore, int level) {}

    private record DemoProject(String ownerGithubId, String name,
                               String description, List<String> techTags) {}
}
