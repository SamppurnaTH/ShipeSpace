# ShipSpace MVP Tracking Document

Date: 2026-08-12

## 1. Purpose

This document tracks the ShipSpace Minimum Viable Product across product features, frontend experience, backend capabilities, infrastructure, verification, and remaining gaps.

ShipSpace MVP is a builder-focused social shipping platform where developers, indie hackers, students, makers, and technical builders can create projects, publish project-linked updates called ships, discover other builders, engage with ships, collaborate, and build reputation through ShipScore.

## 2. Current MVP Decision

**Overall MVP status: NOT READY FOR FULL PRODUCTION BETA - CODE COMPLETE WITH BLOCKED EXTERNAL MANUAL GATES**

The core product workflows are implemented across backend and frontend:

- GitHub OAuth and JWT authentication
- USER and ADMIN role-based access control
- Admin bootstrap through `ADMIN_GITHUB_IDS`
- Banned-user write blocking
- Public read routes for discovery
- Project creation and management
- Ship creation and media upload validation
- Like, comment, follow, and collaboration actions
- Trending, following, and personalized feed surfaces
- ShipScore and rank summary
- User persona UI
- Admin persona UI
- Docker runtime smoke path

Student/User implementation phases 0-8 are complete, and Admin implementation phases 0-7 are closed at code/runtime-verification level. Those phase decisions describe implementation readiness inside their local scopes; they are not the final production-release verdict. The final release decision is recorded in **Section 18: Final KREVO MVP Production Readiness Decision**, and it overrides phase-level wording when evaluating beta readiness.

The active release lane is no longer feature development. It is external manual acceptance: real GitHub OAuth, real two-account browser journeys, configured Multibase upload acceptance, final responsive browser QA, and production-environment sign-off. Until those gates are completed, ShipSpace/KREVO should be treated as code-complete for MVP QA, not ready for a full production beta announcement.

## 3. Status Legend

| Status | Meaning |
| --- | --- |
| Completed | Implemented and wired for MVP use. |
| In Progress | Partially implemented or usable with clear limitations. |
| Accepted Debt | Known non-blocking debt accepted for MVP release readiness. |
| Pending | Required for MVP but not yet complete. |
| Post-MVP | Valuable future work, not required for the current MVP. |

For active phase execution, use these exact status labels: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `COMPLETED`, `ACCEPTED DEBT`, and `POST-MVP`. Older inventory tables may retain readable historical labels; the phase task list below is the execution source of truth.

## 4. Architecture Snapshot

| Layer | Implementation | Key Dependencies | MVP Status |
| --- | --- | --- | --- |
| Backend API | Spring Boot application under `src/main/java/com/shipspace` | Java 17, Spring Boot 3.2, Spring Security, Spring Data MongoDB, Validation, WebFlux | Completed |
| Frontend app | React/Vite app under `web/src` | React 19, React Router, TypeScript, Axios, Vite | Completed |
| Data store | MongoDB repositories and indexed collections | Spring Data MongoDB, MongoDB runtime | Completed |
| Cache/rate infrastructure | Redis-backed services with local cache fallbacks | Redis, Caffeine, Resilience4j | Completed |
| Authentication | GitHub OAuth, OAuth state, JWT access/refresh tokens | GitHub OAuth App, JWT secret, Redis token blacklist/state | Completed |
| Authorization | USER/ADMIN RBAC and banned-user write filter | Spring Security, method security, `ADMIN_GITHUB_IDS` | Completed |
| Media storage | Multibase image upload service | Multibase configuration, upload validation | Completed with manual gates |
| Observability | Actuator, metrics, logs, SLO/load-shedding components | Micrometer, Prometheus registry, structured logging | Completed with production validation debt |
| Runtime | Docker Compose and frontend shell | Docker, Nginx/frontend container, backend container | Completed |
| Testing | Unit, slice, focused integration, property, smoke tests | JUnit, Spring test, jqwik, Testcontainers, npm lint/build | Completed with accepted integration-harness debt |

## 5. Core Feature Tracker

| Feature Area | Description | Implementation Surface | Status | Dependencies | Gaps or Notes |
| --- | --- | --- | --- | --- | --- |
| Public landing page | Anonymous users understand ShipSpace and can start auth or browse public content. | `web/src/pages/LandingPage.tsx`, `web/src/components/home/PublicHomePage.tsx`, `web/src/index.css` | Completed | GitHub OAuth start, trending feed | Continue visual QA on real devices as polish. |
| Public discovery | Anonymous users can browse trending ships and search builders/ships. | `/trending`, `/search`, `/people`, `FeedPage`, `SearchPage`, `PeoplePage`, `FeedController`, `SearchController`, `PeopleController` | Completed | `GET /feeds/trending`, `GET /search`, `GET /users/discover` | Advanced filters and richer discovery are post-MVP. |
| GitHub authentication | Users sign in with GitHub through state-protected OAuth. | `AuthController`, `GitHubOAuthService`, `OAuthStateService`, `AuthPage`, `AuthCallback`, `githubLogin.ts` | Completed | GitHub OAuth App, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI` | Real browser OAuth remains a manual release gate. |
| Credential auth | Email/password style auth. | No backend contract | Post-MVP | New backend APIs, password storage, validation, reset flow | Not part of frozen MVP backend. UI should not claim support until backend exists. |
| JWT session lifecycle | Access token, refresh token, logout, blacklist, current user. | `AuthService`, `JwtUtil`, `TokenBlacklistService`, `AuthContext.tsx`, `api/client.ts` | Completed | JWT secret, Redis for blacklist/state | Refresh behavior is implemented; expired-session UX exists. |
| USER role | Normal authenticated users can perform builder actions. | `SecurityConfig`, JWT roles, frontend protected routes | Completed | Auth token roles | Manual normal-user check remains useful. |
| ADMIN role | Admins can access moderation console only when roles include ADMIN. | `AdminController`, `AdminRoute`, `AdminConsole`, top navigation | Completed | `ADMIN_GITHUB_IDS`, `/auth/me.roles` | Manual admin login/access check remains required. |
| Banned-user write blocking | Banned users can read and logout but cannot write. | `BannedUserWriteFilter`, frontend write CTA guards | Completed | User banned flag from backend | Manual end-to-end banned-write verification remains required. |
| User profile | Public profile read, settings update, rank display, user ships. | `UserController`, `ProfilePage`, `SettingsPage`, `RankSummaryController` | Completed | User repository, rank summary | Profile enrichment beyond bio/location/website is post-MVP. |
| Projects | Builders create, view, update, delete, and list projects. | `ProjectController`, `ProjectService`, `ProjectListPage`, `ProjectDetailPage`, `ProjectModal` | Completed | Auth, MongoDB | Public project reads are available; write actions require auth and non-banned user. |
| Ships | Builders publish project-linked ships and read ship details. | `ShipController`, `ShipService`, `CreateShipModal`, `ShipDetailPage`, `ShipCard` | Completed | Existing project, auth, MongoDB | Every ship belongs to a project for MVP. |
| Ship media upload | Upload validated images for ships. | `ShipController`, `MultibaseService`, upload UI | Completed with manual gates | Multibase env/config, multipart upload | Valid and invalid file matrix remains a manual release gate. |
| Likes | Authenticated users can like and unlike ships. | `EngagementController`, `EngagementService`, `ShipCard`, `ShipDetailPage` | Completed | Auth, banned-write filter | Anonymous users can read but cannot like. |
| Comments | Authenticated users can comment, read comments, delete own/admin-allowed comments. | `EngagementController`, `CommentThread`, `ShipDetailPage` | Completed | Auth, notification/scoring services | Rich text and moderation tooling are post-MVP. |
| Comment upvotes | Users can upvote and remove upvotes on comments. | `EngagementController`, `EngagementService` | Completed | Auth | Frontend exposure is basic compared with core like/comment paths. |
| Following network | Users can follow/unfollow builders and view following/followers. | `SocialController`, `SocialService`, `ProfilePage` | Completed | Auth, user records | Network recommendation quality can be improved post-MVP. |
| Collaborations | Users can request, accept, decline, and review collaboration requests. | `CollaborationController`, `CollaborationService`, `CollaborationsPage` | Completed | Auth, projects, notifications, ShipScore | Older docs mention pending implementation, but current code contains controller, service, UI, and tests. |
| Notifications | Users can view notifications, unread count, read one, and mark all read. | `NotificationController`, `NotificationService`, `NotificationDispatchService`, `NotificationsPage`, `useUnreadCount` | Completed | Auth, backend notification events | Real-time push notifications are post-MVP. |
| Trending feed | Public feed ordered by trending signals and cache refresh. | `FeedController`, `FeedService`, `FeedBuilderService` | Completed | MongoDB, cache, scheduled refresh | Trending calibration is accepted tuning debt. |
| Following feed | Authenticated feed based on followed users. | `FeedController`, `FeedService`, `useFeed` | Completed | Auth, following graph | Empty states exist for users with no follows. |
| Personalized feed | Authenticated personalized/hybrid feed. | `FeedController`, `PersonalizationService`, `BehavioralTrackingService`, `InterestProfileService` | Completed with accepted debt | Behavioral signals, interest profiles, fallbacks | Personalization quality tuning and explanation richness are post-MVP. |
| Search | Public search across users and ships. | `SearchController`, `SearchService`, `SearchPage` | Completed | MongoDB indexes | Advanced facets and ranking are post-MVP. |
| ShipScore | Points and ranks from meaningful project activity. | `ShipScoreService`, `ScoreEventLog`, `RankTier`, `ScoreDecayJob`, `RankSummaryController` | Completed | Score events, scheduled decay | Score calibration and anti-gaming review are accepted/post-MVP debt. |
| Admin dashboard | Admin overview for users and reports. | `AdminController`, `AdminConsole` | Completed | ADMIN role, admin APIs | Metrics are intentionally limited to real data. |
| Admin user management | List users, view detail, ban/unban, role/status badges. | `AdminController`, `AdminUsersPage`, `AdminUserDetailPage` | Completed with accepted debt | ADMIN role | Search/filter is client-side on loaded page; server-side admin search is post-MVP. |
| Admin reports | Review reports and resolve/dismiss/action them. | `AdminController`, `AdminReportsPage` | Completed | Report repository, ADMIN role | Deep report detail screen is post-MVP. |
| Admin audit logs | Admin-readable audit history. | `AuditLogService` exists, no admin-readable controller UI | Post-MVP | New API/UI contract | Not faked in MVP UI. |
| Health and smoke | Runtime readiness checks for API, frontend shell, public reads. | `HealthController`, `ops/mvp-smoke-test.ps1` | Completed | Docker runtime, backend/frontend URLs | Manual gates still separate from smoke. |
| Demo controls | Demo status/reset/data endpoints for controlled environments. | `DemoResetController`, `DemoDataSeeder` | Completed | Demo environment controls | Ensure disabled or controlled appropriately in production. |

## 6. Backend Component Inventory

### Controllers

| Controller | Responsibility | MVP Status |
| --- | --- | --- |
| `AuthController` | OAuth state, GitHub login, token refresh, current user, logout | Completed |
| `UserController` | Public profile, user ships, profile update, current-user projects/activity | Completed |
| `ProjectController` | Project create/read/update/delete and project ships | Completed |
| `ShipController` | Ship create/read/update/delete and image upload | Completed |
| `EngagementController` | Ship likes, comments, comment upvotes, like status | Completed |
| `FeedController` | Following, trending, time-window trending, personalized feeds | Completed |
| `SearchController` | Public user/ship search | Completed |
| `PeopleController` | Public builder discovery and people-specific search | Completed |
| `SocialController` | Follow, unfollow, followers, following | Completed |
| `CollaborationController` | Collaboration request, incoming/sent lists, accept/decline | Completed |
| `NotificationController` | Notifications, unread count, read one, read all | Completed |
| `RankSummaryController` | Public rank summary and admin/cache support endpoint | Completed |
| `AdminController` | Admin stats, users, user detail, ban/unban, roles, reports | Completed |
| `HealthController` | Runtime health endpoint | Completed |
| `DemoResetController` | Demo status/reset/data cleanup | Completed for controlled use |

### Services

| Service Group | Components | MVP Status | Notes |
| --- | --- | --- | --- |
| Auth and identity | `AuthService`, `GitHubOAuthService`, `OAuthStateService`, `TokenBlacklistService`, `JwtUtil` | Completed | GitHub OAuth is the supported MVP auth path. |
| User/project/ship | `UserService`, `ProjectService`, `ShipService`, `CascadeDeleteService` | Completed | Core builder workflow. |
| Engagement/social | `EngagementService`, `SocialService`, `CollaborationService` | Completed | Includes scoring and notifications integration. |
| Feed/search/personalization | `FeedService`, `FeedBuilderService`, `SearchService`, `PersonalizationService`, `BehavioralTrackingService`, `InterestProfileService` | Completed with accepted tuning debt | Fallbacks keep MVP usable when personalized data is sparse. |
| Reputation | `ShipScoreService`, `ContentScoringService`, `CommentQualificationService`, `ScoreDecayJob` | Completed with calibration debt | Rank semantics are usable but should be tuned with real usage. |
| Notifications | `NotificationService`, `NotificationDispatchService` | Completed | Polling/unread-count UX; real-time push is post-MVP. |
| Media | `MultibaseService` | Completed with manual gates | Upload safety matrix and private-data exposure policy need manual sign-off. |
| Operations | `RateLimitingService`, `DistributedLockService`, `CounterReconciliationJob`, SLO/load-shedding components | Completed | Production configuration and alert validation remain operations work. |
| Audit | `AuditLogService` | Partial/Post-MVP | Service exists; admin-readable audit UI/API is not present. |

### Security and Runtime Config

| Component | Responsibility | MVP Status |
| --- | --- | --- |
| `SecurityConfig` | Route authorization, method security, public read policy, admin protection | Completed |
| `JwtAuthenticationFilter` | Bearer token parsing and security context population | Completed |
| `BannedUserWriteFilter` | Blocks banned users from write requests while allowing reads/logout | Completed |
| `CorsConfig` | Allowed frontend origins | Completed |
| `SecurityHeadersConfig` | HTTP security headers | Completed |
| `MongoIndexInitializer` and `SearchIndexVerifier` | Mongo index setup and verification | Completed |
| `CacheConfig` and `RedisConfig` | Cache and Redis setup | Completed |
| `MetricsConfig`, `RequestLoggingConfig`, `SloConfig` | Metrics, logging, and SLO components | Completed |

## 7. Frontend Route Inventory

| Route | Persona | Page or Component | Access | MVP Status | Notes |
| --- | --- | --- | --- | --- | --- |
| `/` | Public/Auth | `LandingPage`, `PublicHomePage`, `AuthenticatedHomePage` | Public or authenticated | Completed | Public landing for anonymous users; authenticated home after login. |
| `/auth` | Public | `AuthPage` | Public | Completed | GitHub-only auth to match backend contract. |
| `/signup` | Public | `AuthPage` | Public | Completed | Same supported GitHub flow as sign in. |
| `/auth/callback` | Public | `AuthCallback` | Public callback | Completed | Exchanges code/state with backend. |
| `/feed` | User | `AuthenticatedHomePage` | Authenticated | Completed | Personalized home and builder actions. |
| `/feed/personalized` | User | `FeedPage` | Authenticated | Completed | Personalized feed with fallback behavior. |
| `/trending` | Public | `FeedPage` | Public read | Completed | Public trending feed. |
| `/search` | Public/User | `SearchPage` | Public read, auth writes | Completed | Search users and ships. |
| `/people` | Public/User | `PeoplePage` | Public read, auth follow | Completed | Builder discovery with sort modes and search. |
| `/ships/new` | User | `CreateShipModal` route | Authenticated, non-banned writes | Completed | Requires a project. |
| `/ships/:id` | Public/User | `ShipDetailPage` | Public read, auth writes | Completed | Likes/comments guarded by auth and banned state. |
| `/content/:id` | Public | Redirect to ship detail | Public | Completed | Canonicalizes old content links. |
| `/profile/:id` | Public/User | `ProfilePage` | Public read, auth follow | Completed | Includes rank summary and user ships. |
| `/projects` | User | `ProjectListPage` | Authenticated, non-banned writes | Completed | Project create/edit/list. |
| `/projects/:id` | Public/User | `ProjectDetailPage` | Public read | Completed | Shows project ships. |
| `/notifications` | User | `NotificationsPage` | Authenticated | Completed | Read/unread flows. |
| `/collaborations` | User | `CollaborationsPage` | Authenticated | Completed | Incoming/sent requests. |
| `/settings` | User | `SettingsPage` | Authenticated | Completed | Profile settings. |
| `/admin` | Admin | `AdminDashboardPage` | ADMIN | Completed | Role-aware route guard. |
| `/admin/users` | Admin | `AdminUsersPage` | ADMIN | Completed with accepted debt | Client-side search/filter on loaded page. |
| `/admin/users/:userId` | Admin | `AdminUserDetailPage` | ADMIN | Completed | Admin-protected user review. |
| `/admin/reports` | Admin | `AdminReportsPage` | ADMIN | Completed | Real report APIs. |
| `/error/:code` | Utility | `ErrorPage` | Public | Completed | Error state page. |
| `/empty` | Utility | `EmptyStatePage` | Public | Completed | Empty state page. |

## 8. API Contract Summary

| Route Group | Anonymous | USER | ADMIN | MVP Status |
| --- | --- | --- | --- | --- |
| Health | Allowed | Allowed | Allowed | Completed |
| Auth bootstrap | Allowed | Allowed | Allowed | Completed |
| Public trending/search | Allowed | Allowed | Allowed | Completed |
| Public builder discovery | Allowed | Allowed (with isFollowing) | Allowed (with isFollowing) | Completed |
| Public profile/project/ship reads | Allowed | Allowed | Allowed | Completed |
| Create/update/delete project | Denied | Allowed unless banned | Allowed unless banned/self-rule applies | Completed |
| Create/update/delete ship | Denied | Allowed unless banned and owner rules pass | Allowed unless banned and owner rules pass | Completed |
| Like/comment/follow | Denied | Allowed unless banned | Allowed unless banned | Completed |
| Upload ship image | Denied | Allowed owner unless banned | Allowed owner unless banned | Completed with manual file gates |
| Notifications/collaborations/settings | Denied | Allowed unless write action banned | Allowed unless write action banned | Completed |
| Admin users/reports/stats | Denied | Denied | Allowed | Completed |
| Ban/unban user | Denied | Denied | Allowed, self-ban blocked | Completed |

## 9. Key Environment and Runtime Dependencies

| Dependency | Required For | Required Variables or Setup | Status |
| --- | --- | --- | --- |
| GitHub OAuth App | Login and admin bootstrap | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI` | Required |
| JWT secret | Token signing | `JWT_SECRET` | Required |
| Admin GitHub IDs | ADMIN role assignment | `ADMIN_GITHUB_IDS` with numeric GitHub IDs | Required for admin |
| CORS origin | Frontend to backend browser calls | `CORS_ALLOWED_ORIGINS` matching frontend URL | Required |
| MongoDB | Core persistence | Mongo connection settings / Docker service | Required |
| Redis | OAuth state, blacklist, rate/cache support | Redis connection settings / Docker service | Required |
| Multibase | Ship image delivery | Multibase endpoint/credentials/policy | Required for uploads |
| Frontend API base | Browser API routing | `VITE_API_BASE_URL` or Docker proxy `/api` | Required |
| Docker Compose | Local MVP runtime | `docker compose up --build` | Required for local integrated smoke |

## 10. Verification and Test Coverage

| Verification Lane | Coverage | Last Known Status | Notes |
| --- | --- | --- | --- |
| Frontend lint | ESLint across `web/src` | Passed with warnings | Existing non-blocking warnings remain. |
| Frontend build | TypeScript and Vite production build | Passed | Prior readiness reports show successful build. |
| Backend compile | Java compilation | Passed | Prior admin report shows compile green. |
| Focused backend MVP test slice | Auth/RBAC, banned filter, indexes, Multibase, OAuth, JWT, interest repo, engagement, ship creation | Passed | Used as the fast MVP gate. |
| Runtime smoke | Backend health, frontend shell, public trending, public search | Passed | `ops/mvp-smoke-test.ps1 -SkipBuildChecks`. |
| Full integration bucket | Broad Testcontainers/integration lane | Accepted Debt | Remaining debt is classified as integration-harness/test-profile debt, not core MVP behavior failure. |

## 11. MVP Acceptance Checklist

| Requirement | Status | Evidence |
| --- | --- | --- |
| Anonymous users see a polished public home page | Completed | Public landing route and home components. |
| GitHub login is the supported MVP auth path | Completed | OAuth state, callback, JWT exchange. |
| Authenticated users get a useful builder home/feed | Completed | `/feed`, `AuthenticatedHomePage`, feed APIs. |
| Users can create projects | Completed | Project controller/service/UI. |
| Users can publish ships linked to projects | Completed | Ship controller/service/create modal. |
| Users can browse public ships/projects/profiles | Completed | Public routes and SecurityConfig public reads. |
| Users can like/comment/follow | Completed | Engagement/social APIs and UI. |
| Users can collaborate | Completed | Collaboration API/UI. |
| ShipScore/ranks are visible | Completed | Rank summary, score service, profile UI. |
| Banned users cannot write | Completed with manual gate | Backend filter and frontend guards. |
| Admin users can moderate users/reports | Completed | Admin routes, APIs, console. |
| Normal users cannot access admin APIs/UI | Completed with manual gate | Spring Security, route guards. |
| Runtime smoke passes | Completed | Existing smoke report. |
| No fake production data is used for unsupported capabilities | Completed | Admin audit logs/server search not faked. |

## 12. Identified Gaps and Missing Elements

### Active Scope Note

Student/User implementation phases 0-8 are complete. The active release lane should now focus on proving the Student/User persona journey in real browsers:

`Login -> Home/Feed -> Create Project -> Post Ship -> Engage -> Follow Builders -> Following Feed -> Profile -> ShipScore/Rank -> Notifications -> Settings/Logout`

Admin persona work, production-hardening work, email delivery, real-time notifications, audit dashboards, advanced moderation, and analytics should remain out of the active scope unless a blocker is found during manual Student/User acceptance.

### MVP Manual Gates

These should be completed before a production-facing MVP announcement:

1. Complete real GitHub OAuth login in a browser.
2. Confirm a normal GitHub account receives USER only.
3. Confirm a configured numeric `ADMIN_GITHUB_IDS` account receives ADMIN and can access `/admin`.
4. Confirm a normal USER direct visit to `/admin` renders forbidden UX and backend APIs remain denied.
5. Ban a non-self user and verify reads/logout still work while create, update, like, comment, follow, collaboration request, and upload writes are blocked.
6. Upload valid PNG, JPEG, GIF, and WebP files.
7. Verify oversized, invalid MIME, and renamed executable uploads are rejected cleanly.
8. Confirm Multibase delivery policy does not expose private data unintentionally.
9. Spot-check responsive UI at mobile, tablet, and desktop widths after final content/assets are stable.

### Accepted Non-Blocking Debt

| Gap | Impact | Recommended Next Step |
| --- | --- | --- |
| Full integration bucket still has harness/profile debt | Does not block focused MVP gate, but reduces confidence in broad CI | Create separate integration stabilization lane. |
| Frontend lint warnings remain | Does not block build, but creates noise | Triage warnings after MVP freeze. |
| Personalized/trending quality calibration | Product relevance can improve with real data | Add analytics-driven tuning after launch. |
| Admin responsive visual spot check remains manual | UI may need small breakpoint polish | Run browser QA at 390, 768, 1024, 1440 widths. |
| Admin search/filter is client-side on loaded page | Search does not cover all users globally | Add server-side admin search endpoint post-MVP. |
| Upload security matrix is manual | Production media confidence depends on manual sign-off | Add automated upload contract tests. |
| Documentation encoding artifacts in older docs/README | Reduces documentation polish | Normalize docs encoding in a cleanup pass. |

### Post-MVP Product Work

| Area | Candidate Work |
| --- | --- |
| Auth | Credentials login, password reset, account linking, email verification. |
| Discovery | Topic pages, advanced filters, better recommendations, builder directories. |
| Collaboration | Rich project/team invitations, roles, accepted-collaboration workspace views. |
| Notifications | Real-time push, preferences, email digest. |
| Admin | Audit log API/UI, server-side search/filter/sort, report detail pages, advanced analytics, role management UI. |
| Moderation | Content reporting from user surfaces, automated risk flags, image scanning. |
| Media | Signed delivery, transformations, CDN integration, image optimization. |
| Reputation | Anti-gaming review, configurable rank thresholds, score explanations. |
| Observability | Production dashboards, alert validation, SLO error budget reporting. |

## 13. Student/User MVP Phase-Based Task List

This task list is the active MVP execution plan. It is intentionally scoped to the Student/User persona and avoids expanding the MVP into admin or production-hardening work.

### Phase 0 - Scope Freeze and Evidence Review

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| 0.1 | Read attached Student/User MVP scope guidance | COMPLETED | `C:\Users\venut\.codex\attachments\3e181979-a9be-4ed5-8664-ec397ef50807\pasted-text.txt`, earlier Student/User scope attachments listed in section 15 | `docs/MVP_Tracking_Document.md` | Scope is Student/User only. Admin, production hardening, email delivery, real-time notifications, audit dashboard, analytics, and advanced moderation remain out of the active lane. | Attachment read and scope reflected in this tracker. |
| 0.2 | Review existing MVP tracker | COMPLETED | `docs/MVP_Tracking_Document.md` | `docs/MVP_Tracking_Document.md` | Tracker already contained Phase 0 through Phase 8, but Phase 0 lacked closure evidence fields and used mixed-case execution statuses. | Phase task status language normalized for active execution. |
| 0.3 | Audit code for student journey gaps | COMPLETED | `web/src/App.tsx`, `web/src/api/endpoints.ts`, `web/src/hooks/useFeed.ts`, `src/main/java/com/shipspace/controller/FeedController.java`, `src/main/java/com/shipspace/service/FeedService.java`, `src/main/java/com/shipspace/service/EngagementService.java`, `src/main/java/com/shipspace/service/SocialService.java`, `src/main/java/com/shipspace/service/CollaborationService.java`, `src/main/java/com/shipspace/service/ShipScoreService.java`, `src/main/java/com/shipspace/model/ScoreEventType.java`, `src/test/java` | `docs/MVP_Tracking_Document.md` | Student routes exist for auth, home/feed, projects, ships, profile, notifications, collaborations, search, settings, and logout. Feed contracts are personalized, following, and trending; no dedicated latest feed contract was found. Notification dispatch evidence exists for follow, ship like, and ship comment. ShipScore events exist for project create/update, quality comment, and collaboration accepted. Notification collaboration coverage and live ShipScore UI refresh remain later-phase verification items. | Repository search commands executed for routes, feeds, notifications, score events, and test inventory. |

**Phase 0 summary:** The active MVP scope is now narrowed to the Student/User journey and the execution tracker has Phase 0 evidence recorded. No product code changes were required. The main unresolved product questions remain Phase 2 Latest feed policy, Phase 7 notification event coverage, and Phase 5 ShipScore live-update verification.

#### Phase 0 Closure Record

| Required Record | Phase 0 Evidence |
| --- | --- |
| Implementation summary | Documentation-only update to make Phase 0 closure auditable and align active phase statuses with the execution prompt. |
| Tests added or updated | None. Phase 0 did not change executable code. |
| Commands executed | `Get-Content docs\MVP_Tracking_Document.md`; `Get-Content` on the latest attached prompt; `rg` route/feed/notification/ShipScore/test inventory searches. |
| Results | Tracker and prompt were read. Student/User code evidence was mapped. No dedicated latest feed endpoint or hook was found. Notification and ShipScore side effects exist but need later journey verification. |
| Manual verification still required | None for Phase 0. Phase 1 and later contain manual/E2E gates. |
| Known debt | Earlier inventory sections still contain historical broad-MVP labels. Active execution statuses are normalized in the phase task list. |
| Phase decision | PHASE COMPLETE |

### Phase 1 - Authentication and Session Journey

| Step | Task | Status | Dependencies | Acceptance Criteria | Notes |
| --- | --- | --- | --- | --- | --- |
| 1.1 | Verify first-time GitHub login in browser | MANUAL E2E PENDING | GitHub OAuth App, backend, frontend | New GitHub user reaches authenticated home with USER role. | Backend creates users by numeric GitHub ID and assigns USER by default. Requires real GitHub OAuth browser pass. |
| 1.2 | Verify returning-user login | MANUAL E2E PENDING | Existing GitHub-linked user | Returning user reaches authenticated home without duplicate account creation. | Backend resolves existing users by GitHub ID and preserves counts/profile fields. Requires real GitHub OAuth browser pass. |
| 1.3 | Verify OAuth cancellation/failure state | VERIFIED BY CODE | GitHub OAuth browser flow | User sees clear failure and can retry. | `AuthCallback` handles missing code/state and backend OAuth errors with controlled retry copy. Manual visual check still useful. |
| 1.4 | Verify invalid OAuth state handling | COMPLETED | Backend OAuth state validation | Invalid state is rejected and user is not authenticated. | Added controller tests for state issuance plus invalid/blank state rejection before GitHub exchange. |
| 1.5 | Verify token refresh behavior | COMPLETED | JWT access/refresh token flow | Expired access token refreshes once and retries request. | API client has single-flight refresh/retry; backend rotates refresh tokens. Added expired JWT validation coverage. |
| 1.6 | Verify logout | VERIFIED BY CODE | `/auth/logout`, token storage | Tokens are cleared and protected routes redirect to public home/auth. | Backend blacklists access/refresh tokens; frontend clears local tokens and user state. Manual profile-menu click pass still useful. |
| 1.7 | Verify logged-out access to protected routes | VERIFIED BY CODE | Route guards | `/feed`, `/projects`, `/notifications`, `/collaborations`, `/settings`, `/ships/new` redirect or show auth-required state. | `App.tsx` protects student write/private routes and redirects anonymous users to `/`. Admin excluded. |

**Phase 1 summary note:** Executable auth/session gates are complete for the current pass. Production code already matched the MVP contract, so only focused tests were added. Real GitHub browser E2E remains a manual gate for first-time and returning-user login because it requires an external GitHub account/OAuth session.

**Phase 1 verification evidence:**

- `.\mvnw.cmd -q '-Dtest=AuthControllerTest,AuthServiceRbacTest,GitHubOAuthServiceTest,JwtUtilTest' test` - PASS.
- `.\mvnw.cmd -q '-Dtest=AuthControllerTest,AuthServiceRbacTest,GitHubOAuthServiceTest,JwtUtilTest,TokenBlacklistIntegrationTest' test` - BLOCKED by local Docker/Testcontainers availability; `TokenBlacklistIntegrationTest` could not find a valid Docker environment.
- `npm.cmd run lint` from `web` - PASS with 33 existing warnings and 0 errors.
- `npm.cmd run build` from `web` - PASS.

**Phase 1 decision:** EXECUTABLE GATES COMPLETE; MANUAL GITHUB OAUTH E2E PENDING. Do not begin Phase 2 until the manual first-time and returning-user GitHub login checks are accepted or explicitly deferred.

### Phase 2 - Home and Feed Experience

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| 2.1 | Confirm authenticated home default feed behavior | COMPLETED | `web/src/components/home/AuthenticatedHomePage.tsx`, `web/src/hooks/useFeed.ts`, `web/src/App.tsx` | `web/src/components/home/AuthenticatedHomePage.tsx`, `web/src/App.tsx` | `/feed` remains the authenticated home with For You as default and now exposes For You, Latest, Following, and Trending filters. | `npm.cmd run build` PASS. |
| 2.2 | Decide Latest feed MVP rule | COMPLETED | `src/main/java/com/shipspace/controller/FeedController.java`, `src/main/java/com/shipspace/service/FeedService.java`, `src/main/java/com/shipspace/repository/ShipRepository.java`, `src/main/java/com/shipspace/config/SecurityConfig.java`, `web/src/api/endpoints.ts`, `web/src/hooks/useFeed.ts` | `FeedController.java`, `FeedService.java`, `SecurityConfig.java`, `web/src/api/endpoints.ts`, `web/src/hooks/useFeed.ts`, `web/src/pages/FeedPage.tsx`, `web/src/App.tsx` | Dedicated Latest feed implemented as `GET /feeds/latest`, public-read, `page`/`size`, max size 50, ordered by `createdAt DESC` with `id` tiebreaker through Mongo pageable sorting. Existing `createdAt_desc` ship index supports the query. | `FeedServiceTest`, `FeedControllerTest`, backend compile PASS. |
| 2.3 | Verify For You/personalized empty/loading/error states | COMPLETED | `FeedController.java`, `PersonalizationService`, `FeedBuilderService`, `web/src/hooks/useFeed.ts`, `web/src/pages/FeedPage.tsx`, `AuthenticatedHomePage.tsx` | `web/src/hooks/useFeed.ts`, `web/src/pages/FeedPage.tsx` | Personalized remains authenticated and falls back to Trending for cold-start/degraded states. Feed hook keeps loading/empty/error states and now exposes retry refresh. | `FeedControllerTest`, `FeedGenerationPropertyTest`, `npm.cmd run build` PASS. |
| 2.4 | Verify Following feed filtering | COMPLETED | `FeedService.java`, `FollowingRepository.java`, `Following.java`, `FeedGenerationPropertyTest.java`, `FeedServiceTest.java` | `FeedService.java`, `FeedServiceTest.java`, `FeedGenerationPropertyTest.java` | Following derives identity from authenticated principal, queries only followees, sorts by `createdAt DESC`, returns empty when following nobody, and now deduplicates followee IDs before querying ships. | `FeedServiceTest`, `FeedGenerationPropertyTest` PASS. |
| 2.5 | Verify Trending feed display | COMPLETED | `FeedController.java`, `FeedService.java`, `FeedBuilderService.java`, `CacheConfig.java`, `MongoIndexInitializer.java`, `web/src/pages/FeedPage.tsx` | `FeedGenerationPropertyTest.java` | Trending remains public, cached for 15 minutes by existing cache config, ordered by `trendingScore DESC, createdAt DESC`, supports all-time and windowed routes. Ranking calibration remains accepted debt. | `FeedControllerTest`, `FeedGenerationPropertyTest` PASS. |
| 2.6 | Verify feed card interactions | COMPLETED | `web/src/components/ShipCard.tsx`, `web/src/pages/FeedPage.tsx`, `AuthenticatedHomePage.tsx`, `web/src/hooks/useFeed.ts` | `web/src/hooks/useFeed.ts`, `web/src/pages/FeedPage.tsx` | Shared `ShipCard` is reused for all feed types. Ship actions navigate to `/ships/:id`, project links to `/projects/:id`, author links to `/profile/:username`; like action respects auth/banned state from callers. Feed append dedupes by ship id. | `npm.cmd run lint` PASS with existing warnings; `npm.cmd run build` PASS. |

**Phase 2 API contract matrix:**

| Feed | Backend endpoint | Service method | Repository/query | Frontend route | Hook/API | Auth |
| --- | --- | --- | --- | --- | --- | --- |
| Latest | `GET /feeds/latest?page=&size=` | `FeedService.getLatestFeed` | `shipRepository.findAll(PageRequest sort createdAt DESC, id DESC)` | `/latest`, `/feed/latest`, authenticated home tab | `getLatestFeed`, `useFeed('latest')` | Public read; liked state only when authenticated. |
| Following | `GET /feeds/following?page=&size=` | `FeedService.getFollowingFeed` | `followingRepository.findByFollowerId(authUser)`, `shipRepository.findByUserIdIn(distinctFollowees, createdAt DESC)` | `/feed/following`, authenticated home tab | `getFollowingFeed`, `useFeed('following')` | Authenticated only. |
| Trending | `GET /feeds/trending?page=&size=`, `GET /feeds/trending/{window}` | `FeedService.getTrendingFeed`, `getTrendingFeedWithTimeWindow` | `shipRepository.findAll(trendingScore DESC, createdAt DESC)`, `findByCreatedAtAfter(...)` | `/trending`, authenticated home tab | `getTrendingFeed`, `getTrendingByWindow`, `useFeed('trending')` | Public read; liked state only when authenticated. |
| Personalized / For You | `GET /feeds/personalized?page=&size=` | `PersonalizationService.getPersonalizedFeed` via `FeedBuilderService` | Cold-start/degraded fallback to trending; warm-user hybrid candidate query | `/feed`, `/feed/personalized`, authenticated home default tab | `getPersonalizedFeed`, `useFeed('personalized')` | Authenticated only. |

**Phase 2 verification evidence:**

- `.\mvnw.cmd -q '-Dtest=FeedServiceTest,FeedControllerTest,FeedGenerationPropertyTest' test` - PASS.
- `.\mvnw.cmd -q -DskipTests compile` - PASS.
- `npm.cmd run lint` from `web` - PASS with 33 existing warnings and 0 errors.
- `npm.cmd run build` from `web` - PASS.
- `.\mvnw.cmd -q '-Dtest=SloEnforcementIntegrationTest' test` - BLOCKED by local Docker/Testcontainers availability; Docker environment was not available to Testcontainers.

**Phase 2 summary note:** Student/User authenticated home and feed architecture is now closed at the executable-code level. The missing Latest feed was implemented as a real backend contract and wired through the API client, shared feed hook, authenticated home filter, standalone route, and shared ship cards. Remaining gates are browser/manual verification of rendered feed navigation and Docker-backed route authorization integration checks.

**Phase 2 decision:** PHASE COMPLETE with manual/browser and Docker-backed integration verification recorded as remaining gates. Do not begin Phase 3 until explicitly authorized.

### Phase 3 - Projects and Ship Creation

| Step | Task | Status | Dependencies | Acceptance Criteria | Evidence or Finding |
| --- | --- | --- | --- | --- | --- |
| 3.1 | Verify create project flow | COMPLETED | `POST /projects`, `ProjectModal`, `ProjectListPage` | Student can create a project with validation and success feedback. | Project create is authenticated, assigns owner from principal, sanitizes inputs, initializes counts, and refreshes the project list after modal success. Empty project list keeps a clear create CTA. |
| 3.2 | Verify edit project flow | COMPLETED | `PUT /projects/{id}`, `ProjectModal`, `ProjectService` | Owner can edit project details and see updated UI. | Backend enforces project ownership and partial update validation. Existing list/detail UI exposes owner edit paths and refreshes after save. |
| 3.3 | Verify delete project behavior | COMPLETED | `DELETE /projects/{id}`, `CascadeDeleteService`, `ProjectService` | Intended delete/cascade behavior is confirmed and communicated clearly. | Delete is owner-only. Confirmed behavior deletes the project, deletes associated ships, decrements project count, and cascades project/ship references through `CascadeDeleteService.deleteProjectCascade(projectId, shipIds)`. Added focused service and controller tests. |
| 3.4 | Verify no-project ship state | COMPLETED | `CreateShipModal`, `/users/me/projects` | Student without projects is prompted to create a project before shipping. | Modal now distinguishes project-load failure from true no-project state. No-project state includes a direct Create project CTA instead of a disabled dead end. |
| 3.5 | Verify publish ship flow | COMPLETED | `POST /ships`, `CreateShipModal`, `ShipService` | Student selects project, writes update, submits once, and sees success route/feed update. | Ship creation verifies project ownership, every ship remains project-linked, duplicate submits are guarded in the modal, and success navigates to `/ships/{id}`. |
| 3.6 | Verify ship image upload UX | COMPLETED WITH MANUAL GATE | `POST /ships/{id}/images`, `MultibaseService`, `CreateShipModal` | Upload progress, success, and failure states are clear. | Frontend multipart field now matches backend contract: `files`. Upload has loading/success/error messaging and allows ship creation to succeed even if optional image upload fails. File-type/security matrix remains a manual release gate. |
| 3.7 | Verify ship detail page | COMPLETED | `GET /ships/{id}`, `ShipDetailPage`, `ShipCard` | Ship detail has readable metadata, project context, media, and engagement controls. | Public ship detail remains readable. Authenticated write controls stay guarded. Ship media now handles backend image reference objects instead of rendering broken object values. |

**Phase 3 API contract matrix:**

| Journey | API route | Auth rule | Frontend surface | Result |
| --- | --- | --- | --- | --- |
| List my projects | `GET /users/me/projects` | Authenticated | `ProjectListPage`, `CreateShipModal` | Used for project management and project selection before shipping. |
| Create project | `POST /projects` | Authenticated, non-banned write path | `ProjectModal` | Creates owner-scoped project and refreshes UI. |
| Read project | `GET /projects/{projectId}` | Public | `ProjectDetailPage` | Public project page remains readable for anonymous users. |
| Edit project | `PUT /projects/{projectId}` | Authenticated owner | `ProjectModal` | Owner-only edit, backend-enforced. |
| Delete project | `DELETE /projects/{projectId}` | Authenticated owner | Project management UI / service contract | Deletes project, associated ships, and cascaded references. |
| Project ships | `GET /projects/{projectId}/ships` | Public | `ProjectDetailPage` | Deterministically sorted by `createdAt DESC, id DESC`. |
| Create ship | `POST /ships` | Authenticated owner of selected project | `CreateShipModal` | Publishes project-linked ship and navigates to detail. |
| Upload ship images | `POST /ships/{shipId}/images` | Authenticated owner within edit window | `CreateShipModal` | Uses multipart field `files`; optional upload failure is surfaced without losing created ship. |
| Read ship detail | `GET /ships/{shipId}` | Public | `ShipDetailPage`, `ShipCard` | Public read with metadata, project context, media, and guarded engagement controls. |
| Delete ship | `DELETE /ships/{shipId}` | Authenticated owner | Service/controller contract | Deletes ship, cascades ship references, and decrements project ship count. |

**Phase 3 ownership and delete/cascade decisions:**

| Action | Owner allowed | Non-owner result | Anonymous result | Cascade behavior |
| --- | --- | --- | --- | --- |
| Edit project | Yes | `403 FORBIDDEN` | `401 UNAUTHORIZED` | No cascade. |
| Delete project | Yes | `403 FORBIDDEN` | `401 UNAUTHORIZED` | Deletes project, project ships, and related project/ship references. |
| Create ship under project | Project owner only | `403 FORBIDDEN` from ownership verification | `401 UNAUTHORIZED` | Updates project ship metadata on success. |
| Upload ship images | Ship owner only, edit window required | `403 FORBIDDEN` | `401 UNAUTHORIZED` | Media references are attached to the existing ship. |
| Delete ship | Yes | `403 FORBIDDEN` | `401 UNAUTHORIZED` | Deletes ship references and decrements parent project ship count. |

**Phase 3 UX states covered:**

- Project list loading, empty, and API-error retry states.
- No-project-before-ship state with a direct Create project CTA.
- Project-load failure inside the ship modal.
- Duplicate-submit protection for ship publish.
- Optional image upload loading, success, and failure states.
- Public project/ship read behavior with authenticated-only write controls.
- Banned-user write blocking remains enforced through the existing backend write filter and existing frontend write guards.

**Phase 3 verification evidence:**

- `.\mvnw.cmd -q '-Dtest=ProjectServiceTest,ShipServiceTest,ProjectControllerTest,ShipControllerTest,FeedServiceTest,FeedControllerTest' test` - PASS after setting `JAVA_HOME` to `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`.
- `.\mvnw.cmd -q '-Dtest=ProjectManagementPropertyTest,ProjectValidationPropertyTest,ShipManagementPropertyTest' test` - PASS after aligning property-test sanitizer mocks with the production `sanitize(input, maxLength)` call signature.
- `.\mvnw.cmd -q -DskipTests compile` - PASS after setting `JAVA_HOME`.
- `npm.cmd run lint` from `web` - PASS with 33 existing warnings and 0 errors.
- `npm.cmd run build` from `web` - PASS.
- `.\mvnw.cmd -q '-Dtest=ShipCreationIntegrationTest' test` - BLOCKED in this environment. First attempt was denied by sandbox network permissions while Maven wrapper resolved runtime artifacts; escalated rerun timed out after 120 seconds and did not refresh the Surefire report. Existing report from 2026-08-09 shows 5 tests, 0 failures, 0 errors, but this pass does not count it as fresh evidence.
- `git diff --check` - PASS with Windows CRLF normalization notices only.

**Phase 3 remaining manual gates:**

- Browser E2E for project create/edit/delete confirmation copy and final visual polish.
- Browser E2E for publish ship with and without images against a configured Multibase environment.
- Valid/invalid upload file matrix remains a security acceptance gate.
- Docker-backed integration tests remain dependent on local Docker/Testcontainers availability.

**Phase 3 summary note:** Student/User project-to-ship creation is executable-complete for MVP. The real missing frontend/backend contract issue was ship image upload using `images` while the backend expects multipart field `files`; that is now corrected. Project and ship ownership/delete/cascade behavior is documented and covered by focused tests. Stop boundary observed: engagement, notifications, profile/ShipScore, and admin work remain untouched for later phases.

**Phase 3 decision:** PHASE COMPLETE with manual browser/media and Docker-backed integration verification recorded as remaining gates. Do not begin Phase 4 until explicitly authorized.

### Phase 4 - Engagement and Social Graph

| Step | Task | Status | Dependencies | Acceptance Criteria | Evidence or Finding |
| --- | --- | --- | --- | --- | --- |
| 4.1 | Verify like/unlike journey | COMPLETED | Like APIs, feed/detail UI | Count and button state update correctly and repeated clicks do not corrupt counts. | Explicit `PUT /ships/{shipId}/like`, `DELETE /ships/{shipId}/like`, and `GET /ships/{shipId}/like/status` are wired. Detail page now hydrates liked state after reload. Atomic ship like counters and unique like index protect duplicate/repeated actions. |
| 4.2 | Verify comment create/read/delete | COMPLETED | Comment APIs, `CommentThread` | Comment appears, persists after reload, and owner delete works. | `CommentResponse` now includes `userId`, `upvotes`, and `upvotedByCurrentUser`; frontend owner-delete logic no longer depends on missing fields. Comment delete removes comment upvotes first and decrements ship comment count atomically. |
| 4.3 | Verify comment upvote UX | COMPLETED | Comment upvote APIs | Upvote state/count is visible and understandable. | Frontend now calls explicit add/remove upvote APIs, disables repeat clicks while pending, surfaces failures, and persists `upvotedByCurrentUser` after reload for authenticated viewers. |
| 4.4 | Verify follow/unfollow from profile | COMPLETED | Follow APIs, `ProfilePage` | Button state and follower/following counts update correctly. | Public profile response now includes viewer-specific `isFollowing`. Profile follow/unfollow has pending state, rollback, visible error handling, and no self-profile follow CTA. |
| 4.5 | Verify following feed after follow/unfollow | COMPLETED WITH MANUAL GATE | Follow graph and following feed | Followed builders appear in Following feed; unfollowed builders disappear. | Backend following feed tests pass and follow/unfollow relationship writes are idempotent. Real browser multi-user follow/unfollow feed verification remains a manual gate. |

**Phase 4 API contract matrix:**

| Journey | API route | Auth rule | Frontend surface | Result |
| --- | --- | --- | --- | --- |
| Like ship | `PUT /ships/{shipId}/like` | Authenticated, non-banned write path | `ShipCard`, `ShipDetailPage` | Idempotently creates one like and increments ship like count once. |
| Unlike ship | `DELETE /ships/{shipId}/like` | Authenticated, non-banned write path | `ShipCard`, `ShipDetailPage` | Idempotently removes one like and decrements ship like count only when a relationship was deleted. |
| Like status | `GET /ships/{shipId}/like/status` | Authenticated | `ShipDetailPage` | Hydrates persisted liked state after refresh/navigation. |
| List likes | `GET /ships/{shipId}/likes` | Public | Future/read surfaces | Public read remains allowed. |
| Add comment | `POST /ships/{shipId}/comments` | Authenticated, non-banned write path | `ShipDetailPage`, `CommentThread` | Sanitizes content, creates comment, increments ship comment count, and dispatches owner notification when not self-comment. |
| List comments | `GET /ships/{shipId}/comments` | Public, optionally viewer-enriched when authenticated | `CommentThread` | Anonymous readers can read comments; authenticated users also receive `upvotedByCurrentUser`. |
| Delete own comment | `DELETE /ships/comments/{commentId}` | Authenticated comment owner | `CommentThread` | Deletes the comment, removes its upvotes, and decrements ship comment count. |
| Add comment upvote | `PUT /ships/comments/{commentId}/upvote` | Authenticated, non-banned write path | `CommentThread` | Idempotently adds one upvote and applies quality-comment score only on first qualifying transition. |
| Remove comment upvote | `DELETE /ships/comments/{commentId}/upvote` | Authenticated, non-banned write path | `CommentThread` | Idempotently removes one upvote and does not decrement if another request already removed it. |
| Follow user | `POST /social/follow/{userId}` | Authenticated, non-banned write path | `ProfilePage` | Idempotently creates the relationship, increments both user counters once, and dispatches follow notification once. |
| Unfollow user | `DELETE /social/follow/{userId}` | Authenticated, non-banned write path | `ProfilePage` | Idempotently removes the relationship and decrements both user counters only when a row was deleted. |
| Followers/following | `GET /social/followers`, `GET /social/following` | Authenticated | Social/profile/feed support | Returns paged user summaries for the current user. |
| Following feed | `GET /feeds/following?page=&size=` | Authenticated | Feed/home tabs | Uses current following graph and remains covered by backend feed tests. |

**Phase 4 defect and gap closure:**

| Finding | Classification | Resolution |
| --- | --- | --- |
| Comment ownership and upvote UI expected fields the backend did not return. | Defect | Added `userId`, `upvotes`, and `upvotedByCurrentUser` to `CommentResponse`; comments are now enriched for authenticated viewers. |
| Comment upvote removal was only local in the frontend. | Defect | Added `removeCommentUpvote` API wrapper and wired explicit add/remove calls. |
| Ship detail liked state reset after reload. | Defect | Added `getLikeStatus` frontend call and detail-page hydration. |
| Profile follow button could not know persisted following state. | Defect | Added viewer-aware `isFollowing` to `UserProfileResponse` and `UserController`. |
| Deleting a comment could leave orphan comment upvotes. | Data integrity defect | Added `deleteByCommentId` cleanup before comment deletion. |
| Concurrent duplicate follow/unfollow could drift user counters. | Data integrity defect | Added duplicate-key handling for follow and delete-count handling for unfollow. |
| Rapid UI clicks could create confusing local drift. | UX gap | Added pending guards, rollback, disabled states, and visible errors for follow, comment delete, and comment upvote actions. |
| Full browser multi-user social-loop QA is not automated. | Accepted debt/manual gate | Backend contracts are covered; real browser follow/unfollow feed verification remains required before final student readiness. |

**Phase 4 ownership, authorization, and idempotency:**

| Action | Anonymous | Authenticated USER | Banned USER | Owner/Self Rule | Duplicate/Race Protection |
| --- | --- | --- | --- | --- | --- |
| Read comments/likes | Allowed | Allowed | Allowed | None | Public read only. |
| Like/unlike ship | Denied | Allowed | Blocked by write filter | Self-like is allowed but notification is skipped | Unique `user_ship_unique`; explicit APIs are idempotent; counters use atomic updates. |
| Create comment | Denied | Allowed | Blocked by write filter | Self-comment is allowed but notification is skipped | Frontend duplicate-submit guard; distinct comments are allowed by design. |
| Delete comment | Denied | Comment owner only | Blocked by write filter | Non-owner receives forbidden | Comment upvotes are deleted before comment removal. |
| Upvote/remove comment upvote | Denied | Allowed | Blocked by write filter | No self-upvote restriction found | Unique `user_comment_unique`; add/remove APIs are idempotent; count cannot go below zero. |
| Follow/unfollow user | Denied | Allowed | Blocked by write filter | Self-follow forbidden | Unique `follower_followee_unique`; duplicate follow and concurrent unfollow do not double-update counters. |
| Following feed | Denied | Allowed | Allowed read path | Current user only | Uses saved follow graph; empty graph returns a clean empty page. |

**Phase 4 notification and ShipScore observations:**

- Like notification dispatch exists and skips self-like.
- Comment notification dispatch exists and skips self-comment.
- Follow notification dispatch exists for the followed user.
- Duplicate follow race handling prevents duplicate counter increments and duplicate notification dispatch.
- Comment upvote scoring still uses the quality-comment transition and existing score-event uniqueness protection; deeper ShipScore/rank proof remains Phase 5.
- Full notification chain validation remains Phase 7 and was intentionally not expanded in this phase.

**Phase 4 verification evidence:**

- `.\mvnw.cmd -q '-Dtest=EngagementServiceTest,EngagementControllerTest,SocialServiceTest,SocialControllerTest,FeedServiceTest,UserServiceTest' test` - PASS after approval for Maven wrapper network access.
- `.\mvnw.cmd -q '-Dtest=EngagementPropertyTest,SocialFollowingPropertyTest,FeedServiceTest' test` - PASS after approval for Maven wrapper network access.
- Surefire summary: `EngagementControllerTest` 11 tests, `SocialControllerTest` 20, `EngagementPropertyTest` 14, `EngagementServiceTest` 29, `FeedServiceTest` 18, `SocialFollowingPropertyTest` 11, `SocialServiceTest` 20, `UserServiceTest` 11; all 0 failures, 0 errors, 0 skipped.
- `npm.cmd run lint` from `web` - PASS with 33 existing warnings and 0 errors.
- `npm.cmd run build` from `web` - PASS; generated `dist/index.html`, image assets, `dist/assets/index-D-GWfPBo.css`, and `dist/assets/index-D_zdoDrV.js`.
- `.\mvnw.cmd -q -DskipTests compile` - PASS after setting `JAVA_HOME` to `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`.
- `.\mvnw.cmd -q '-Dtest=BannedUserWriteFilterTest' test` - PASS; expected banned-write warning was emitted for `POST /api/ships`.

**Phase 4 remaining manual gates:**

- Browser E2E at mobile/tablet/desktop widths for like, unlike, comment, delete comment, comment upvote/remove, follow, unfollow, and following-feed refresh.
- Two-account browser scenario to prove followed builders appear in Following feed and disappear after unfollow.
- Optional network-throttled UI check for repeated click protection on like/upvote/follow actions.

**Phase 4 summary note:** Student/User engagement and social graph behavior is closed at the executable-code level. The phase fixed real frontend/backend contract defects around comment metadata, persisted upvote state, persisted like state, and viewer-specific follow state. Backend idempotency and counter integrity are now covered for like/unlike, comment upvote/remove, comment delete, follow/unfollow, and following feed regressions. Manual browser verification remains as an accepted gate before final Student/User readiness.

**Phase 4 decision:** PHASE COMPLETE with manual browser and multi-user feed verification recorded as remaining gates. Do not begin Phase 5 until explicitly authorized.

### Phase 5 - Profile, ShipScore, and Rank

| Step | Task | Status | Dependencies | Acceptance Criteria | Notes |
| --- | --- | --- | --- | --- | --- |
| 5.1 | Verify public profile hierarchy | COMPLETE | `ProfilePage`, profile APIs | Avatar, username, bio, links, counts, rank, ShipScore, projects, and ships are easy to scan. | Fixed profile API/UI mismatch by returning rank fields from public profile and rendering public projects. |
| 5.2 | Verify own profile actions | COMPLETE | Auth context and profile route | Student can identify Edit Profile/Settings path and own content. | Own profile shows Edit Profile instead of follow/collaborate actions. |
| 5.3 | Verify settings save flow | COMPLETE | `PUT /users/me`, `SettingsPage` | Profile changes save, error, and success states work. | Backend now treats email/avatar as GitHub-owned and only persists bio, location, and website. |
| 5.4 | Verify ShipScore event generation | COMPLETE | `ShipScoreService`, score event logs | Eligible project/comment/collaboration actions apply score once according to business rules. | Fixed duplicate-key race by saving score-event log before user score mutation. |
| 5.5 | Verify rank summary refresh | COMPLETE | `GET /v1/users/{id}/rank-summary` | Profile/rank display reflects score changes after refresh or navigation. | Fixed frontend contract to use `nextLevelScore`, not nonexistent `pointsToNext`. |
| 5.6 | Improve basic ShipScore explanation if needed | COMPLETE | Existing UI copy only | Student understands what ShipScore and rank mean without fake exact rules. | Tracker now documents actual current score rules; detailed public event history remains post-MVP. |

**Phase 5 API contract matrix:**

| API | Auth | Purpose | Phase 5 finding |
| --- | --- | --- | --- |
| `GET /auth/me` | Authenticated | Current logged-in user profile/session payload. | Used for own-user identity and banned/admin flags. |
| `GET /users/{userId}` | Public, viewer-aware if authenticated | Public builder profile. | Now returns identity, bio, links, counts, `isFollowing`, `shipScore`, `level`, and `rank`. |
| `PUT /users/me` | Authenticated write | Edit current user's profile. | Only bio, location, and website are mutable; email/avatar remain GitHub-owned. |
| `GET /users/{userId}/projects` | Public read | Public project list for a builder profile. | Added to support other-user and public profile completeness. |
| `GET /users/{userId}/ships` | Public read | Public ship list for a builder profile. | Existing route retained and used by profile. |
| `GET /users/me/projects` | Authenticated read | Current user's projects. | Existing route retained for authenticated project flows. |
| `GET /social/followers`, `GET /social/following` | Authenticated read | Current user's social graph. | Existing route retained; profile uses counts from public profile. |
| `POST /social/follow/{userId}`, `DELETE /social/follow/{userId}` | Authenticated write | Follow/unfollow another builder. | Profile disables write action for banned users and never shows follow on own profile. |
| `GET /v1/users/{id}/rank-summary` | Public read | ShipScore, level, rank, next-level target. | Profile reads `nextLevelScore` and calculates remaining points client-side. |
| `DELETE /v1/users/{id}/rank-summary/cache` | Authenticated self only | Clear rank-summary cache. | Existing contract retained; no new frontend dependency added. |

**ShipScore event inventory:**

| Event | Trigger | Beneficiary | Points | Idempotency and caps | Status |
| --- | --- | --- | --- | --- | --- |
| `PROJECT_CREATED` | Project owner creates a project. | Project owner | 50 | Unique `(userId,eventType,projectId)`. No daily cap. | Integrated. |
| `PROJECT_UPDATED` | Project owner updates a project. | Project owner | 20 | Effective entity key is `projectId:UTCDate`; daily cap 5 per user. | Integrated; comment/design wording should be reviewed because current behavior is once per project per day. |
| `QUALITY_COMMENT` | Comment reaches quality threshold during upvote. | Comment author | 5 | Unique `(userId,eventType,commentId)`. | Integrated; self-upvote policy remains a design review item. |
| `COLLAB_ACCEPTED` | Project owner accepts a collaboration request. | Requester | 10 | Unique `(userId,eventType,collaborationId)`. | Integrated. |

Likes, follows, ship creation, and ship deletion do not currently award ShipScore. GitHub is used for identity/authentication only; there is no GitHub contribution scoring in the current MVP.

**Current rank and score rules:**

- `User.shipScore` is the stored cumulative score.
- Level formula is `min(30, floor(shipScore / 40) + 1)`.
- `nextLevelScore` is `currentLevel * 40` until level 30, then `null`.
- Rank tiers are `DOCKED` levels 1-5, `LAUNCHER` 6-10, `ORBITER` 11-15, `NAVIGATOR` 16-20, `COMMANDER` 21-25, and `FLEET_ADMIRAL` 26-30.
- Daily score decay runs for users inactive for more than 60 days and applies `floor(score * 0.9)`, then recalculates level/rank.
- Score-event logs have a 90-day TTL. That means idempotency proof is durable for the TTL window, not forever.

**Phase 5 defects closed:**

- Public profile response did not expose `shipScore`, `level`, or `rank` even though profile UI depended on them.
- Profile UI expected `pointsToNext`, but backend returned `nextLevelScore`.
- Public builder profiles had ships but no public projects section/API, weakening the project-timeline identity.
- Profile update accepted email/avatar mutation through the service despite GitHub-owned identity copy.
- Duplicate score-event replay under a duplicate-key race could mutate user score before event-log persistence rejected the duplicate. Score logs are now persisted first and include the awarded point value for auditability.

**Phase 5 accepted debt and design-review items:**

- Browser E2E should still verify own profile, other-user profile, settings save, and rank refresh at mobile/tablet/desktop widths.
- Profile ship cards do not hydrate persisted liked state in the profile list; feed/detail flows cover stronger engagement hydration.
- Project create/delete/recreate, superficial project updates, self-upvote policy, collaboration farming, score decay cadence, and public score-event transparency need a post-foundation ShipScore design review.
- `PROJECT_UPDATED` daily cap uses count-before-insert; high-concurrency distinct update events could still be reviewed later. This is not the duplicate replay defect fixed in this phase.

**Phase 5 verification results:**

- `cd B:\Personal\ShipSpace\web; npm.cmd run lint` - PASS, 0 errors and 34 existing warnings.
- `cd B:\Personal\ShipSpace\web; npm.cmd run build` - PASS.
- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q -DskipTests compile` - PASS.
- Focused backend tests PASS: `UserServiceTest` 11, `UserControllerTest` 12, `ShipScoreServiceTest` 49, `ShipScoreServicePropertyTest` 18, `RankSummaryControllerTest` 7, `RankSummaryControllerPropertyTest` 2, `ScoreDecayJobTest` 19, `ProjectServiceTest` 15, `ShipServiceTest` 19, `SocialServiceTest` 20; all 172 tests have 0 failures and 0 errors.

**Phase 5 summary note:** Student/User profile, ShipScore, and rank behavior is closed at the executable-code level. The phase fixed real frontend/backend profile contract gaps, public profile completeness, GitHub-owned identity persistence, and a score idempotency race. Remaining items are manual browser QA and post-foundation score-design review, not blockers for Phase 5 code closure.

**Phase 5 decision:** PHASE COMPLETE. Do not begin Phase 6 until explicitly authorized.

### Phase 6 - Collaboration Journey

| Step | Task | Status | Dependencies | Acceptance Criteria | Notes |
| --- | --- | --- | --- | --- | --- |
| 6.1 | Verify request collaboration | COMPLETE | `POST /collaborations`, project detail entry point | Student A can request collaboration on Student B project where allowed. | Request CTA now lives on project detail so the target project is explicit. |
| 6.2 | Verify incoming/sent lists | COMPLETE | Collaboration APIs, `CollaborationsPage` | Student sees incoming and sent requests with clear statuses. | Incoming now includes terminal statuses, not only pending. |
| 6.3 | Verify accept/decline transitions | COMPLETE | Accept/decline APIs | Status transitions persist and UI prevents repeated action confusion. | Terminal-state replay is rejected before score/notification side effects. |
| 6.4 | Verify collaboration notification expectations | COMPLETE | Notification event coverage | Required collaboration events create correct in-app notifications or are documented as out of scope. | Request, accepted, and declined dispatch narrow collaboration notifications; Phase 7 still owns read/unread/navigation closure. |

**Phase 6 collaboration data model:**

`CollaborationRequest` persists `id`, `requesterId`, `projectId`, `ownerId`, optional `message`, `status`, `createdAt`, and `resolvedAt`. Supported statuses are exactly `PENDING`, `ACCEPTED`, and `DECLINED`. There is no `ACTIVE`, `COMPLETED`, or `CANCELLED` lifecycle in the current MVP.

**Phase 6 API matrix:**

| Method | Path | Auth | Request | Response | Actor | Target user/project | Status rule | Errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `POST` | `/collaborations` | Authenticated write; banned blocked by filter | `{ projectId, message? }` | Collaboration response with requester, owner, and project summaries | Authenticated requester from principal | Project owner from persisted project | Creates `PENDING`; duplicate requester/project returns existing request | `400` validation, `401`, `403` own project/banned, `404` missing user/project |
| `GET` | `/collaborations/incoming?page=&size=` | Authenticated read | Query pagination, max size 50 | Page of collaboration responses | Authenticated project owner from principal | Requests where `ownerId` is principal | Returns all statuses newest first by controller sort | `401` |
| `GET` | `/collaborations/sent?page=&size=` | Authenticated read | Query pagination, max size 50 | Page of collaboration responses | Authenticated requester from principal | Requests where `requesterId` is principal | Returns all statuses newest first by controller sort | `401` |
| `POST` | `/collaborations/{id}/accept` | Authenticated write; banned blocked by filter | Path id | Accepted collaboration response | Authenticated owner from principal | Request must be addressed to owner | `PENDING -> ACCEPTED`; terminal states rejected | `401`, `403`, `404` |
| `POST` | `/collaborations/{id}/decline` | Authenticated write; banned blocked by filter | Path id | Declined collaboration response | Authenticated owner from principal | Request must be addressed to owner | `PENDING -> DECLINED`; terminal states rejected | `401`, `403`, `404` |

There is no cancel/remove/detail endpoint for collaborations in the current MVP.

**Phase 6 status-transition matrix:**

| From | Action | Result |
| --- | --- | --- |
| `PENDING` | Accept by owner | `ACCEPTED`, `resolvedAt` set, requester gets `COLLAB_ACCEPTED` ShipScore event |
| `PENDING` | Decline by owner | `DECLINED`, `resolvedAt` set |
| `ACCEPTED` | Accept again | Rejected with controlled `403`; no score or notification replay |
| `ACCEPTED` | Decline | Rejected with controlled `403` |
| `DECLINED` | Accept | Rejected with controlled `403` |
| `DECLINED` | Decline again | Rejected with controlled `403`; no side effects |

**Phase 6 authorization matrix:**

| Scenario | Current result |
| --- | --- |
| Anonymous request collaboration | DENY by authenticated-route policy |
| Banned USER requests collaboration | DENY by `BannedUserWriteFilter` with `BANNED_USER` |
| A requests collaboration with self/own project | DENY with `ForbiddenException` |
| A sends valid request to B project | ALLOW, persisted as `PENDING` |
| A sees own Sent request | ALLOW via principal-derived `requesterId` |
| B sees Incoming request | ALLOW via principal-derived `ownerId` |
| C sees A/B private request | DENY by absence of arbitrary `userId` query/path read |
| B accepts request addressed to B | ALLOW |
| A accepts own sent request | DENY |
| C accepts A to B request | DENY |
| B declines request addressed to B | ALLOW |
| Repeated accept | Safe controlled rejection, no duplicate score |
| Repeated decline | Safe controlled rejection, no duplicate side effects |

**Phase 6 duplicate and idempotency behavior:**

- Persistence has a unique requester/project index through `CollaborationRequest` and `MongoIndexInitializer`.
- Duplicate request attempts now return the existing persisted request, including the actual terminal status if it is already accepted or declined.
- A concurrent duplicate-key race is caught and mapped to the existing request instead of creating a duplicate row.
- Terminal-state accept/decline replays are rejected before ShipScore or notification dispatch.

**Phase 6 notification handoff:**

| Collaboration event | Notification currently dispatched? | Recipient | Type | Target |
| --- | --- | --- | --- | --- |
| Request created | IMPLEMENTED | Project owner | `COLLABORATION_REQUESTED` | Collaboration id, target view `/collaborations` |
| Request accepted | IMPLEMENTED | Requester | `COLLABORATION_ACCEPTED` | Collaboration id, target view `/collaborations` |
| Request declined | IMPLEMENTED | Requester | `COLLABORATION_DECLINED` | Collaboration id, target view `/collaborations` |

Phase 7 still owns notification list/read/unread/navigation closure. This phase only verified dispatch side effects.

**Phase 6 ShipScore handoff:**

- Accepted collaboration triggers `ScoreEventType.COLLAB_ACCEPTED`.
- Beneficiary is the requester, not the accepting owner.
- Value remains 10 points.
- Entity/idempotency key is the collaboration request id.
- Repeated accept is rejected before `ShipScoreService.applyScoreEvent`, so duplicate acceptance cannot double-award points.
- Whether accepted collaboration should award reputation immediately, wait for verified completion, or reward both parties remains in the Post-Foundation ShipScore Design Review.

**Phase 6 project/delete interaction:**

Project deletion already calls `CascadeDeleteService.deleteProjectCascade(projectId, shipIds)`, and the cascade removes `collaboration_requests` by `projectId`. This prevents active collaboration cards from pointing to deleted projects after project removal.

**Phase 6 implementation changes:**

- Enriched collaboration responses with lightweight requester, owner, and project summaries.
- Changed incoming requests from pending-only to all owner-addressed statuses.
- Returned the actual persisted collaboration on duplicate requester/project attempts.
- Added best-effort collaboration notification dispatch for request, accept, and decline.
- Added a project-detail collaboration CTA for authenticated non-owners, with banned/owner/existing-status disabled states.
- Reworked `CollaborationsPage` into a clear Incoming/Sent review surface with loading, empty, error, pending action, terminal state, and retry handling.

**Phase 6 accepted debt and manual gates:**

- Browser two-account QA remains required: A requests B, A sees Sent pending, B sees Incoming pending, B accepts or declines, both views refresh to terminal status.
- Responsive browser QA remains required at 390, 768, 1024, and 1440 widths for project-detail CTA and collaboration lists.
- There is no request cancellation route in current MVP.
- A declined/accepted requester/project pair cannot be requested again because the unique index covers all statuses. This is acceptable for MVP but should be revisited if collaboration lifecycle gains richer states.
- Collaboration farming remains a potential design risk if users create many different project-scoped collaborations that are accepted. This is not a confirmed duplicate-replay defect.

**Phase 6 verification results:**

- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q '-Dtest=CollaborationServiceTest' test` - PASS, 14 tests, 0 failures, 0 errors.
- Focused backend regression command for `CollaborationServiceTest,ShipScoreServiceTest,ShipScoreServicePropertyTest,ProjectServiceTest,SocialServiceTest,UserServiceTest` - PASS, 127 tests, 0 failures, 0 errors.
- `cd B:\Personal\ShipSpace\web; npm.cmd run lint` - PASS, 0 errors and 33 warnings.
- `cd B:\Personal\ShipSpace\web; npm.cmd run build` - PASS.

**Phase 6 summary note:** Collaboration is now closed at the executable-code level as a trustworthy project-scoped Student/User workflow. The phase fixed real UX and contract gaps around request entry point, incoming terminal-state visibility, duplicate request truthfulness, collaboration response context, repeated action protection, and collaboration notification side effects.

**Phase 6 decision:** PHASE COMPLETE. Do not begin Phase 7 until explicitly authorized.

### Phase 7 - In-App Notification Event Coverage

| Step | Task | Status | Dependencies | Acceptance Criteria | Notes |
| --- | --- | --- | --- | --- | --- |
| 7.1 | Map required notification events | COMPLETED | Product decision | MVP event list is frozen: follow, ship like, ship comment, comment upvote if supported, collaboration request, collaboration accepted/declined if supported. | Email, push, WebSockets/SSE, notification preferences, and digests remain excluded. Comment-upvote notifications are intentionally out of MVP. |
| 7.2 | Verify follow notification | COMPLETED | `SocialService`, notification UI | Recipient gets unread notification and can navigate to actor/profile. | Follow target corrected from `/users/{actorId}` to `/profile/{actorId}`. Self-follow remains forbidden and duplicate follow is idempotent upstream. |
| 7.3 | Verify like notification | COMPLETED | `EngagementService`, notification UI | Ship owner gets unread notification and can navigate to ship. | Existing like dispatch targets `/ships/{shipId}` and skips self-like notification. Repeated active like is idempotent upstream. |
| 7.4 | Verify comment notification | COMPLETED | `EngagementService`, notification UI | Ship owner gets unread notification and can navigate to ship/comment context. | Existing comment dispatch targets `/ships/{shipId}` and skips self-comment notification. Separate comments may create separate notifications. |
| 7.5 | Verify collaboration notification coverage | COMPLETED | `CollaborationService`, notification UI | Required collaboration events notify correct users or are explicitly logged as missing MVP work. | Request, accepted, and declined notifications now target `/collaborations`, which is the actionable review surface. Terminal-state replay is rejected before notification dispatch. |
| 7.6 | Verify unread count and read states | COMPLETED | `NotificationController`, `useUnreadCount` | Count increases, mark-one-read and mark-all-read decrease it correctly. | Notification service ownership and idempotent read tests added. UI dispatches a shared unread-count refresh event after mark-one/mark-all. |
| 7.7 | Verify notification navigation targets | COMPLETED | Notification model target fields, frontend routing | Clicking relevant notifications opens the correct ship/profile/project/collaboration destination. | Rows are keyboard-actionable buttons. Structured `targetView` is preferred; deterministic fallbacks exist for follow, ship events, and collaboration events. |

**Phase 7 notification architecture:**

Business-event services call `NotificationDispatchService.dispatch(...)`, which creates unread `Notification` rows through `NotificationService.createNotification(...)`. Authenticated users read their own notifications through `NotificationController`, which derives identity from the authenticated principal and exposes list, unread-count, mark-one-read, and mark-all-read operations. The frontend uses `NotificationsPage` for list/read/navigation and `useUnreadCount` for nav badges.

**Phase 7 notification data model:**

`Notification` persists `id`, `userId`, `type`, `text`, `actorId`, `actorAvatarUrl`, `targetId`, `targetView`, `read`, and `createdAt`. Notification rows have a 90-day TTL and a `userId/read/createdAt` compound index. The MVP uses `targetView` and `targetId` for navigation; frontend text parsing is not required.

**Phase 7 required MVP event matrix:**

| Business event | Actor | Recipient | Notification type | Target | Self-event rule | Duplicate rule | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A follows B | Follower A | Followee B | `FOLLOW` | `/profile/{actorId}` | Self-follow forbidden | Existing follow row prevents duplicate dispatch | REQUIRED + IMPLEMENTED |
| A likes B's ship | Liker A | Ship owner B | `LIKE` | `/ships/{shipId}` | Self-like notification skipped | Active duplicate like is idempotent; unlike/re-like can create a new legitimate notification | REQUIRED + IMPLEMENTED |
| A comments on B's ship | Commenter A | Ship owner B | `COMMENT` | `/ships/{shipId}` | Self-comment notification skipped | Separate comments are legitimate separate events | REQUIRED + IMPLEMENTED |
| A upvotes B's comment | Upvoter A | Comment author B | None | None | No notification policy | Upvote idempotency exists for engagement/scoring, but no notification dispatch | INTENTIONALLY OUT OF MVP |
| A requests collaboration on B's project | Requester A | Project owner B | `COLLABORATION_REQUESTED` | `/collaborations` | Own-project request forbidden | Duplicate requester/project returns existing request and does not dispatch again | REQUIRED + IMPLEMENTED |
| B accepts A's request | Project owner B | Requester A | `COLLABORATION_ACCEPTED` | `/collaborations` | Only owner can accept | Terminal replay rejected before notification dispatch | REQUIRED + IMPLEMENTED |
| B declines A's request | Project owner B | Requester A | `COLLABORATION_DECLINED` | `/collaborations` | Only owner can decline | Terminal replay rejected before notification dispatch | REQUIRED + IMPLEMENTED |

**Phase 7 API matrix:**

| Method | Path | Auth | Actor/source of identity | Behavior | Notes |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/notifications?page=&size=` | Authenticated | Principal user id | Lists current user's notifications only | Page is clamped to `>= 0`, size to `1..50`, sorted by `createdAt DESC, id DESC`. |
| `GET` | `/notifications/unread-count` | Authenticated | Principal user id | Returns current user's unread count | No client-supplied user id. |
| `PUT` | `/notifications/{notificationId}/read` | Authenticated, banned write-block applies | Principal user id | Marks current user's notification read | Other-user or missing notification returns not found. Already-read is safe/idempotent. |
| `PUT` | `/notifications/read-all` | Authenticated, banned write-block applies | Principal user id | Marks all unread notifications for current user read | Query is scoped by `userId` and `read=false`. |

**Phase 7 ownership and security matrix:**

| Scenario | Current result |
| --- | --- |
| Anonymous lists notifications | Denied by authenticated route policy |
| Anonymous reads unread count | Denied by authenticated route policy |
| Anonymous marks one/all read | Denied by authenticated route policy |
| A lists B notifications | Not possible through API contract; controller uses principal user id only |
| A marks B notification read | Denied by service ownership check; response path is not found |
| A manipulates B unread count | Not possible through API contract; count uses principal user id only |
| Banned user reads own list/count | Allowed because reads are safe methods |
| Banned user marks read/all | Blocked by `BannedUserWriteFilter` because read-state mutation is a write |

**Phase 7 UI behavior:**

`NotificationsPage` now covers loading, empty, error with retry, success list, pending mark-one/read-all, read/unread state, and failed mutation recovery. Notification rows are buttons with accessible labels and non-color-only unread state. Read-on-click persists first, refreshes unread-count subscribers, then navigates. If persistence fails, the UI shows a controlled error and refetches instead of faking read state.

**Phase 7 navigation matrix:**

| Notification type | Destination |
| --- | --- |
| `FOLLOW` | `/profile/{actorId}` or structured `targetView` |
| `LIKE` | `/ships/{targetId}` or structured `targetView` |
| `COMMENT` | `/ships/{targetId}` or structured `targetView` |
| `COLLABORATION_REQUESTED` | `/collaborations` |
| `COLLABORATION_ACCEPTED` | `/collaborations` |
| `COLLABORATION_DECLINED` | `/collaborations` |
| Comment upvote | No notification destination; intentionally out of MVP |

**Phase 7 duplicate and idempotency rules:**

Notification service does not add a global deduplication index. MVP duplicate safety comes from upstream business idempotency: follow uniqueness, active like idempotency, collaboration duplicate-request handling, and terminal-state replay rejection. Separate comments and unlike/re-like are allowed to create new event rows because they represent distinct user actions.

**Phase 7 deleted target handling:**

Structured targets route through existing application pages. Deleted ship/project destinations fall back to existing route-level not-found/empty behavior rather than crashing the notification page. Collaboration notifications route to `/collaborations`, which remains useful even when a specific project/request context is no longer actionable. Rich historical notification cleanup beyond existing cascade/TTL behavior is post-MVP.

**Phase 7 files inspected:**

`NotificationController`, `NotificationService`, `NotificationDispatchService`, `Notification`, `NotificationRepository`, `NotificationResponse`, `EngagementService`, `SocialService`, `CollaborationService`, `SecurityConfig`, `BannedUserWriteFilter`, `NotificationsPage`, `useUnreadCount`, `TopNav`, `LeftPanel`, `BottomNav`, `App.tsx`, and existing service tests.

**Phase 7 implementation changes:**

- Corrected follow notification navigation to `/profile/{actorId}`.
- Corrected collaboration notification navigation to `/collaborations`.
- Made notification listing use recipient-scoped, stable, clamped pagination.
- Made mark-one-read idempotent for already-read notifications.
- Added unread-count refresh event support shared by badge surfaces.
- Reworked the notifications page into an actionable, accessible, recoverable list UI.
- Added notifications to mobile bottom navigation so the badge has a visible mobile destination.
- Added/updated focused notification, social, and collaboration tests.

**Phase 7 files changed:**

`src/main/java/com/shipspace/repository/NotificationRepository.java`, `src/main/java/com/shipspace/service/NotificationService.java`, `src/main/java/com/shipspace/service/SocialService.java`, `src/main/java/com/shipspace/service/CollaborationService.java`, `src/test/java/com/shipspace/service/NotificationServiceTest.java`, `src/test/java/com/shipspace/service/SocialServiceTest.java`, `src/test/java/com/shipspace/service/CollaborationServiceTest.java`, `web/src/hooks/useUnreadCount.ts`, `web/src/pages/NotificationsPage.tsx`, `web/src/components/BottomNav.tsx`, and `docs/MVP_Tracking_Document.md`.

**Phase 7 accepted debt and manual gates:**

- Manual two-account browser gate remains required: A follows B, likes B ship, comments on B ship, requests collaboration on B project, B accepts, and a separate valid request is declined if the current requester/project uniqueness model permits it.
- For each manual scenario, verify unread badge, list copy, actor/avatar, destination, read state, badge decrement, and refresh persistence.
- Real-time push, email delivery, notification preferences, digests, historical notification detail views, and global notification deduplication are post-MVP.

**Phase 7 verification results:**

- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q '-Dtest=NotificationServiceTest,SocialServiceTest,EngagementServiceTest,CollaborationServiceTest,ShipScoreServiceTest' test` - PASS after approval for Maven wrapper network access. Surefire summary: `NotificationServiceTest` 8 tests, `SocialServiceTest` 20, `EngagementServiceTest` 29, `CollaborationServiceTest` 14, `ShipScoreServiceTest` 49; all 120 tests have 0 failures, 0 errors, 0 skipped.
- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q -DskipTests compile` - PASS after approval for Maven wrapper access.
- `cd B:\Personal\ShipSpace\web; npm.cmd run lint` - PASS with 31 existing warnings and 0 errors.
- `cd B:\Personal\ShipSpace\web; npm.cmd run build` - PASS; generated `dist/index.html`, image assets, `dist/assets/index-D-GWfPBo.css`, and `dist/assets/index-D4841iKw.js`.
- `cd B:\Personal\ShipSpace; git diff --check` - PASS with line-ending warnings only; no whitespace errors.

**Phase 7 summary note:** In-app notification architecture, event coverage, read/unread state, navigation, ownership, and UI state handling are now closed for the Student/User MVP at the executable-code level. The remaining Phase 7 work is the documented manual two-account browser gate.

**Phase 7 decision:** PHASE COMPLETE. Do not begin Phase 8 until explicitly authorized.

### Phase 8 - Search, Responsive UI, Accessibility, and Final Student QA

| Step | Task | Status | Dependencies | Acceptance Criteria | Notes |
| --- | --- | --- | --- | --- | --- |
| 8.1 | Verify search results UX | COMPLETED | `GET /search`, `SearchPage` | Builders and ships render with loading, no-results, error, and partial-result states. | Search now uses typed frontend contracts, abortable requests, accessible input labeling, retryable error state, and id-based profile links. Backend search size is clamped to a valid bounded range. Advanced facets remain post-MVP. |
| 8.2 | Verify responsive student routes | COMPLETED WITH MANUAL VISUAL GATE | Browser QA | Home, feed, projects, ship detail, profile, auth, notifications, collaborations, settings work at mobile/tablet/desktop widths. | Code-level responsive navigation was improved: mobile bottom nav now exposes Home, Search, Ship, Alerts, and Profile. In-app browser control was unavailable during this pass, so viewport screenshot QA remains a manual gate. Admin excluded from this scope. |
| 8.3 | Verify basic accessibility | COMPLETED | Keyboard and screen-reader-oriented checks | Headings, labels, focus states, buttons/links, contrast, alt text, and alerts are acceptable. | Search now has a semantic search form and visible state messaging. Student form fields no longer suppress native outlines without replacement. Existing focus-visible styles remain in global CSS. No formal WCAG certification is claimed. |
| 8.4 | Run frontend lint/build | COMPLETED | Node/npm | `npm.cmd run lint` and `npm.cmd run build` pass. | Lint passed with 0 errors and 26 pre-existing warnings. Build passed and emitted `dist/assets/index-D-GWfPBo.css` and `dist/assets/index-CAFKKaYh.js`. |
| 8.5 | Run student-focused backend or smoke checks | COMPLETED | Backend/Docker as needed | Smoke path passes; student flows verified manually or through focused tests. | Student-focused backend suite passed 303 tests. Property regression subset passed 73 tests. Backend compile passed. Runtime smoke passed after Docker Compose startup. |
| 8.6 | Create final Student/User MVP readiness note | COMPLETED | Completed phases 1-8 | Document final status, accepted debt, and remaining post-MVP work. | Student/User MVP is READY WITH ACCEPTED NON-BLOCKING MANUAL QA DEBT. Manual browser/two-account/upload gates remain before a production-facing announcement. |

**Phase 8 API and UI contract matrix:**

| Area | Contract | Auth Rule | Result |
| --- | --- | --- | --- |
| Search builders and ships | `GET /search?q={query}&size={size}` | Public and authenticated read | Completed. Empty sanitized queries return empty results. Size is clamped to `1..20`. Frontend supports loading, error, retry, partial, and empty states. |
| Public trending smoke | `GET /feeds/trending` | Public and authenticated read | Completed. Runtime smoke returned HTTP 200. |
| Profile navigation | `/profile/:id` | Public profile read | Completed. Student surfaces now prefer backend user id links instead of username-only links. |
| Mobile student navigation | Home/Search/Ship/Alerts/Profile | Auth-aware, write action still guarded by existing auth/banned checks | Completed. Bottom nav now exposes the most important student actions on small screens. |

**Phase 8 files changed:**

- `src/main/java/com/shipspace/controller/SearchController.java`
- `src/test/java/com/shipspace/controller/SearchControllerTest.java`
- `web/src/api/endpoints.ts`
- `web/src/pages/SearchPage.tsx`
- `web/src/components/BottomNav.tsx`
- `web/src/components/TopNav.tsx`
- `web/src/components/LeftPanel.tsx`
- `web/src/components/SideNav.tsx`
- `web/src/components/ShipCard.tsx`
- `web/src/pages/ShipDetailPage.tsx`
- `web/src/pages/SettingsPage.tsx`
- `web/src/components/ProjectModal.tsx`
- `web/src/components/CreateShipModal.tsx`
- `docs/MVP_Tracking_Document.md`

**Phase 8 verification results:**

- `cd B:\Personal\ShipSpace\web; npm.cmd run lint` - PASS with `26 problems (0 errors, 26 warnings)`.
- `cd B:\Personal\ShipSpace\web; npm.cmd run build` - PASS. Output included `dist/index.html`, six image assets, `dist/assets/index-D-GWfPBo.css`, and `dist/assets/index-CAFKKaYh.js`.
- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q '-Dtest=AuthControllerTest,AuthServiceRbacTest,GitHubOAuthServiceTest,JwtUtilTest,UserServiceTest,UserControllerTest,ProjectServiceTest,ProjectControllerTest,ShipServiceTest,ShipControllerTest,FeedServiceTest,FeedControllerTest,SearchControllerTest,EngagementServiceTest,EngagementControllerTest,SocialServiceTest,CollaborationServiceTest,NotificationServiceTest,ShipScoreServiceTest,RankSummaryControllerTest' test` - PASS. Surefire total: 303 tests, 0 failures, 0 errors, 0 skipped.
- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q '-Dtest=ProjectManagementPropertyTest,ShipManagementPropertyTest,FeedGenerationPropertyTest,EngagementPropertyTest,SocialFollowingPropertyTest,ShipScoreServicePropertyTest' test` - PASS. Surefire total: 73 tests, 0 failures, 0 errors, 0 skipped.
- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q -DskipTests compile` - PASS.
- `cd B:\Personal\ShipSpace; docker compose up --build -d` - PASS. Backend, frontend, MongoDB, and Redis containers started.
- `cd B:\Personal\ShipSpace; powershell -ExecutionPolicy Bypass -File .\ops\mvp-smoke-test.ps1 -SkipBuildChecks` - PASS. Backend health HTTP 200, frontend shell HTTP 200, public trending feed HTTP 200, public search HTTP 200.
- `cd B:\Personal\ShipSpace; git diff --check` - PASS with Windows CRLF normalization warnings only; no whitespace errors.

**Phase 8 accepted debt and manual gates:**

- Real GitHub OAuth browser gate remains required: first-time login, returning login, and OAuth failure/cancellation UX.
- Two-account browser gate remains required for follow/unfollow feed behavior, collaboration request/accept/decline, and notification events.
- Banned-user browser gate remains required to confirm read/logout are available while create/like/comment/follow/upload are blocked.
- Upload file matrix remains required for PNG, JPEG, GIF, WebP, oversized files, invalid MIME files, corrupt files, and renamed executable rejection.
- Responsive visual QA remains required at 390, 768, 1024, and 1440 widths because in-app browser automation was unavailable during this pass.
- Remaining frontend lint warnings are non-blocking but should be cleaned up after Student/User MVP freeze.

**Phase 8 summary note:** Search, responsive navigation, basic accessibility cleanup, frontend verification, backend student regression tests, property checks, compile, Docker runtime, and smoke checks are complete. Student/User MVP is now functionally ready with accepted non-blocking manual QA debt. Admin, production hardening, email delivery, real-time notifications, analytics, audit dashboards, and advanced moderation remain outside this phase.

**Phase 8 decision:** PHASE COMPLETE. Student/User MVP status is READY WITH ACCEPTED NON-BLOCKING MANUAL QA DEBT.

## 14. Admin Persona MVP Beta Phase-Based Task List

This section is the separate Admin beta readiness lane. Student/User implementation phases 0-8 remain frozen. The objective is to make Admin trustworthy enough to operate a controlled beta, not to build a broad enterprise admin platform.

### Admin Phase 0 - Scope Freeze and Existing Architecture Audit

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| A0.1 | Read Student/User tracker and Admin beta scope | COMPLETED | `docs/MVP_Tracking_Document.md`, `C:\Users\venut\.codex\attachments\2824724d-6f64-464d-a219-e2c199783dd5\pasted-text.txt` | `docs/MVP_Tracking_Document.md` | Student/User implementation phases are complete and frozen. Admin beta scope is dashboard, users, ban/unban, reports, audit logs, admin navigation, responsive/basic accessibility, and final admin/user QA. | Tracker and prompt read fully before code inspection. |
| A0.2 | Audit backend Admin controller, security, auth, banned-write policy, reports, and tests | COMPLETED | `AdminController`, `SecurityConfig`, `AuthService`, `GitHubOAuthService`, `BannedUserWriteFilter`, `Report`, `ReportRepository`, `AdminControllerTest`, `application.yml` | `docs/MVP_Tracking_Document.md` | `/admin/**` is protected by `hasRole("ADMIN")`; Admin controller also has class-level `@PreAuthorize("hasRole('ADMIN')")`. Admin bootstrap uses numeric `ADMIN_GITHUB_IDS`. Current tests cover direct controller self-ban/ban/user detail fields, but not route-level anonymous/USER/ADMIN authorization or report transition rules. | Repository inspection and `rg` searches. No production code changed. |
| A0.3 | Audit frontend Admin routes, pages, API methods, and navigation | COMPLETED | `web/src/App.tsx`, `web/src/pages/admin/AdminConsole.tsx`, `web/src/api/endpoints.ts`, `web/src/components/TopNav.tsx`, `web/src/index.css` | `docs/MVP_Tracking_Document.md` | Frontend has AdminRoute, dashboard, users, user detail, reports, Builder app return, logout, loading/empty/error/retry states, and confirmation modal for ban/unban. No Audit Logs route/page/API exists. Admin avatar profile link currently uses username rather than the id-first pattern adopted for Student/User surfaces. | File inspection only. No Admin production code changed in Phase 0. |
| A0.4 | Audit existing AuditLog architecture | COMPLETED | `AuditLogService`, `AuditLog`, `AuditLogRepository`, `MongoIndexInitializer`, audit service call sites | `docs/MVP_Tracking_Document.md` | Audit collection/model/repository/indexes exist with 365-day TTL, but there is no admin-readable Audit API/UI. Existing audit callers are limited to score/rank events. Admin moderation actions such as ban/unban and report resolution are logged to application logs, not persisted to AuditLog. | `rg` confirmed only score/rank audit service callers. |
| A0.5 | Build Admin capability matrix and phase plan | COMPLETED | All files above | `docs/MVP_Tracking_Document.md` | Admin beta phase plan A0-A7 added below with Phase 0 closed and later phases left `NOT STARTED`. Audit Logs promoted into Admin beta readiness as a missing implementation to address in Admin Phase 5. | Documentation-only update. |

**Admin Phase 0 existing capability matrix:**

| Capability | Backend | Frontend | Security | Tests | Status |
| --- | --- | --- | --- | --- | --- |
| Dashboard | `GET /admin/stats`, `GET /admin/users`, `GET /admin/reports` | `AdminDashboardPage` with real/derived metric labels | `/admin/**` requires ADMIN | No focused dashboard assertions found | COMPLETED WITH TEST GAP |
| User List | `GET /admin/users?page=&size=` | `AdminUsersPage`, pagination, client-side search/filter, badges | ADMIN only | Indirect controller coverage only | COMPLETED WITH TEST GAP |
| User Detail | `GET /admin/users/{userId}` | `AdminUserDetailPage` | ADMIN only | Direct controller test checks visible fields | COMPLETED |
| Ban | `PATCH /admin/users/{userId}/ban` with `banned=true` | User list/detail actions and confirmation modal | ADMIN only, self-ban blocked in controller/UI | Direct controller tests cover self-ban and ban mutation | COMPLETED WITH AUDIT GAP |
| Unban | `PATCH /admin/users/{userId}/ban` with `banned=false` | User list/detail actions and confirmation modal | ADMIN only, self-unban blocked in controller/UI | No explicit unban test found | COMPLETED WITH TEST GAP + AUDIT GAP |
| Reports | `GET /admin/reports`, `PATCH /admin/reports/{reportId}/resolve` | `AdminReportsPage`, status filter, dismiss/action-taken actions | ADMIN only | No focused report transition tests found | COMPLETED WITH TEST GAP + AUDIT GAP |
| Audit | `AuditLogService`, `AuditLog`, `AuditLogRepository`, indexes | No Admin Audit Logs route/page/API | No admin read contract yet | No audit service/controller tests found | MISSING IMPLEMENTATION |

**Admin Phase 0 classified findings:**

| Classification | Finding | Impact | Target Phase |
| --- | --- | --- | --- |
| TEST GAP | Admin authorization is configured in `SecurityConfig`, but current `AdminControllerTest` directly invokes controller methods and does not prove anonymous/USER/ADMIN route behavior. | Security cannot be called fully evidenced for beta until route-level tests exist. | Admin Phase 1 |
| TEST GAP | Admin report status transitions are not covered by focused tests. | Report moderation may drift without regression coverage. | Admin Phase 4 |
| TEST GAP | Unban is wired through the same endpoint but lacks an explicit direct test. | Low code risk, but important beta moderation path needs evidence. | Admin Phase 3 |
| MISSING IMPLEMENTATION | There is no admin-readable Audit Logs API, frontend route, or navigation item. | Admin cannot answer who did what and when from the product UI during beta. | Admin Phase 5 |
| AUDIT GAP | Ban/unban/report moderation write application logs but do not persist `AuditLog` records. | Moderation accountability is not durable enough for beta operations. | Admin Phase 5 |
| ACCEPTED DEBT | Admin user search/filter is client-side on the loaded page. | Acceptable for a small controlled beta unless page size becomes unusable. | Re-evaluate in Admin Phase 3; likely POST-MVP |
| UX GAP | Admin detail public profile link uses `/profile/{username}` while Student/User links were changed to id-first routing. | Could navigate incorrectly if profile route expects backend id for some users. | Admin Phase 3 or 6 |
| MANUAL VERIFICATION GAP | Real GitHub Admin vs normal User role validation remains unproven in browser. | Required before beta freeze. | Admin Phase 1 and Phase 7 |
| POST-MVP | Advanced analytics, role editor, impersonation, bulk moderation, audit analytics, and server-side admin search are outside Admin beta scope. | Prevents admin scope creep. | Post-MVP |

**Admin Phase 0 existing Admin route/API matrix:**

| Method | Path | Backend handler | Required role | Current notes |
| --- | --- | --- | --- | --- |
| `GET` | `/admin/stats` | `AdminController.getStats` | ADMIN | Returns real `totalUsers` and `pendingReports`. |
| `GET` | `/admin/users?page=&size=` | `AdminController.listUsers` | ADMIN | Uses `PageRequest.of(page, size)` without local clamping in controller. Route-level auth tests pending. |
| `GET` | `/admin/users/{userId}` | `AdminController.getUser` | ADMIN | Returns `User` entity directly. No secrets observed in `User`, but DTO hardening should be considered in Admin Phase 3. |
| `PATCH` | `/admin/users/{userId}/ban` | `AdminController.banUser` | ADMIN | Self-ban/self-unban blocked by principal name matching user id. Ban state persists. Audit persistence missing. |
| `PATCH` | `/admin/users/{userId}/roles` | `AdminController.updateRoles` | ADMIN | Backend exists but frontend does not expose role editing. Complex role-management UI remains out of scope. |
| `GET` | `/admin/reports?status=&page=&size=` | `AdminController.listReports` | ADMIN | Uses real report repository. User-facing report creation entry point was not found in this Phase 0 audit. |
| `PATCH` | `/admin/reports/{reportId}/resolve` | `AdminController.resolveReport` | ADMIN | Accepts status body and persists resolver/note/timestamp. Status validation/error behavior needs Phase 4 tests. |

**Admin Phase 0 audit architecture notes:**

- `AuditLog` stores `id`, `eventType`, `userId`, `entityId`, `previousState`, `newState`, `metadata`, and `timestamp`.
- `MongoIndexInitializer` creates `userId_eventType_idx` and a 365-day `timestamp_ttl` index for `audit_logs`.
- `AuditLogService` is async best-effort and swallows persistence failures after logging an error.
- Existing audit events are rank/level/score oriented: `RANK_CHANGED`, `LEVEL_CHANGED`, `SCORE_APPLIED`, and `SCORE_DECAY_APPLIED`.
- No Admin moderation/accountability audit events are currently persisted.
- No normal Student/User API can create arbitrary audit entries through a public controller because no Audit controller exists.

**Admin Phase 0 verification results:**

- `Get-Content docs\MVP_Tracking_Document.md` - PASS; tracker read before Admin audit.
- `Get-Content C:\Users\venut\.codex\attachments\2824724d-6f64-464d-a219-e2c199783dd5\pasted-text.txt` - PASS; Admin master prompt read.
- `rg --files | rg "(Admin|admin|Audit|Report|SecurityConfig|AuthService|UserService|BannedUserWriteFilter|App\.tsx|endpoints\.ts|test)"` - PASS; Admin/security/audit/test inventory collected.
- `rg "ADMIN_GITHUB_IDS|hasRole\('ADMIN'\)|ROLE_ADMIN|/admin|AuditLog|Report|ban|unban" src web/src -n` - PASS; route/security/call-site evidence collected.
- `Get-Content` inspections completed for `AdminController`, `SecurityConfig`, `AuthService`, `GitHubOAuthService`, `BannedUserWriteFilter`, `Report`, `ReportRepository`, `AuditLogService`, `AuditLog`, `AuditLogRepository`, `MongoIndexInitializer`, `AdminControllerTest`, `App.tsx`, `AdminConsole.tsx`, and `web/src/api/endpoints.ts`.

**Admin Phase 0 manual gates:**

- None required for Phase 0 closure because this phase is audit/documentation only.
- Manual browser gates are carried forward into Admin Phase 1 and Admin Phase 7.

**Admin Phase 0 accepted debt:**

- No code/tests were changed in Phase 0 by design.
- Existing line-ending warnings and broader dirty worktree state are inherited from previous phases and were not altered.

**Admin Phase 0 summary note:** Admin has a usable MVP foundation for dashboard, users, user detail, ban/unban, and reports. Backend route protection is configured with ADMIN-only guards and GitHub bootstrap remains numeric-ID based. The beta-critical missing capability is an admin-readable audit trail with durable moderation/security events. The biggest immediate evidence gaps are route-level Admin authorization tests, report transition tests, unban coverage, and browser verification of ADMIN vs USER role separation.

**Admin Phase 0 decision:** PHASE COMPLETE. Do not begin Admin Phase 1 until explicitly authorized.

### Admin Phase 1 - Authentication, ADMIN RBAC, and Route Isolation

| Step | Task | Status | Dependencies | Acceptance Criteria | Notes |
| --- | --- | --- | --- | --- | --- |
| A1.1 | Trace GitHub numeric ID to ADMIN role assignment | COMPLETED | `ADMIN_GITHUB_IDS`, `GitHubOAuthService`, `AuthService`, JWT roles | Configured numeric GitHub ID receives USER + ADMIN. Username is never used as security authority. | Added regression tests proving numeric GitHub ID is the authority, whitespace is trimmed, configured existing users gain ADMIN on next login, and admin-like usernames without configured numeric IDs remain USER only. |
| A1.2 | Prove frontend AdminRoute behavior | COMPLETED WITH MANUAL BROWSER GATE | `AuthContext`, `App.tsx`, `AdminRoute` | Anonymous sees Admin sign-in; USER sees forbidden; ADMIN enters Admin console. | Code inspection confirms loading state blocks flashes, anonymous users see `AdminAccessRequired`, non-admin users see `AdminForbiddenPage`, and admin users render the Admin console. Real browser OAuth role check remains manual. |
| A1.3 | Prove backend `/admin/**` authorization | COMPLETED | `SecurityConfig`, JWT filter, Admin controller APIs | Missing JWT, malformed JWT, expired JWT, and USER JWT are denied; ADMIN JWT is allowed. | Added `AdminSecurityRouteTest` through MockMvc/Spring Security/JWT filter. Direct controller calls are no longer the only Admin auth evidence. |
| A1.4 | Document Admin API authorization matrix | COMPLETED | Admin API routes | Every admin route lists method, path, required role, request, response, and error behavior. | Matrix below includes stats, users, user detail, ban/unban, roles, reports, and report actions. |

**Admin Phase 1 auth/RBAC architecture:**

`GitHubOAuthService` exchanges the GitHub OAuth code for GitHub user data, converts GitHub `id` to a string, looks up users by `githubId`, and calls `applyConfiguredRoles(user, githubId)` on both new and returning users. `AuthService` generates JWT access tokens from persisted user roles. `JwtAuthenticationFilter` validates token signature/expiry/type/blacklist state, extracts roles, prefixes authorities with `ROLE_` where needed, and stores user id as the authenticated principal. `SecurityConfig` enables method security and protects `/admin/**` with `hasRole("ADMIN")`. `AdminController` also has class-level `@PreAuthorize("hasRole('ADMIN')")`.

**Admin Phase 1 GitHub numeric ID to role flow:**

| Step | Source | Behavior | Evidence |
| --- | --- | --- | --- |
| GitHub identity | `GitHubUserResponse.id` | Numeric GitHub ID is converted to string and stored as `githubId`. | `GitHubOAuthService.createOrUpdateUser` inspected and covered by tests. |
| Admin config | `admin.github-ids: ${ADMIN_GITHUB_IDS:}` | Comma-separated string. Empty/blank config grants no ADMIN. Values are split by comma and trimmed before exact string comparison to GitHub numeric ID. | Existing code and new whitespace test. |
| New normal user | GitHub ID not configured | Roles become `USER` only. | Existing `authenticateWithGitHub_NewUser_CreatesUserSuccessfully` test. |
| New configured admin | GitHub ID configured | Roles become `USER` + `ADMIN`. | Existing admin test plus new whitespace regression. |
| Admin-like username | Login text matches configured/admin-like value but numeric ID differs | Roles remain `USER` only. | New `authenticateWithGitHub_AdminLikeUsernameWithoutConfiguredNumericId_DoesNotAddAdminRole` test. |
| Returning user added to config | Existing USER GitHub ID later appears in config | Next login adds `ADMIN` while preserving `USER`. | New returning-user admin bootstrap test. |
| Existing admin removed from config | Existing persisted ADMIN role with config empty | Next login retains persisted `ADMIN`. | New regression documents current persistent-role behavior. Operational demotion currently requires explicit role mutation. |
| Duplicate configured IDs | Duplicate entries are effectively harmless because comparison is exact `anyMatch`. | Code inspection. |
| Invalid configured values | Non-matching strings do not grant ADMIN because comparison is exact against numeric GitHub ID string. | Code inspection. |

**Admin Phase 1 JWT and `/auth/me` findings:**

| Area | Finding | Evidence |
| --- | --- | --- |
| JWT role claims | `AuthService` signs access tokens with persisted roles; `JwtUtil.extractRoles` returns token roles or defaults to USER if missing. | `AuthServiceRbacTest`, `JwtUtilTest`, `AdminSecurityRouteTest`. |
| Backend authority mapping | `JwtAuthenticationFilter` maps `ADMIN` to `ROLE_ADMIN`, preserving Spring `hasRole("ADMIN")` compatibility. | Code inspection and Admin JWT route tests. |
| `/auth/me` role hydration | `/auth/me` returns `CurrentUserResponse.roles` and no access/refresh token fields. | `AdminSecurityRouteTest.authMeReturnsCurrentUserRolesWithoutSensitiveTokens`. |
| Method security | `SecurityConfig` has `@EnableMethodSecurity`; `AdminController` `@PreAuthorize` is active in addition to route security. | Code inspection and WebMvc slice imports real security config. |

**Admin Phase 1 frontend AdminRoute matrix:**

| State | `/admin` | Admin navigation |
| --- | --- | --- |
| Auth loading | `LoadingScreen`; Admin content is not rendered during role hydration. | Top navigation derives Admin link from authenticated roles only after auth context has user data. |
| Anonymous | `AdminAccessRequired` with GitHub sign-in CTA for `/admin`. | No authenticated admin nav item. |
| USER | `AdminForbiddenPage`; does not render Admin console children. | Top navigation hides Admin Console when roles lack `ADMIN`/`ROLE_ADMIN`. |
| ADMIN | Admin route renders requested Admin page. | Top navigation shows Admin Console when roles include `ADMIN` or `ROLE_ADMIN`. |

**Admin Phase 1 Admin API matrix:**

| Method | Path | Request | Response | Error semantics |
| --- | --- | --- | --- | --- |
| `GET` | `/admin/stats` | None | `{ totalUsers, pendingReports }` | Missing/malformed/expired/USER JWT denied before controller. ADMIN allowed. |
| `GET` | `/admin/users?page=&size=` | Page query params | `Page<User>` | Missing/malformed/expired/USER JWT denied before controller. ADMIN allowed. |
| `GET` | `/admin/users/{userId}` | Path user id | `User` | ADMIN allowed after security; missing user is business 404. USER denied before controller. |
| `PATCH` | `/admin/users/{userId}/ban` | `{ "banned": "true" }` or `{ "banned": "false" }` | `{ userId, banned }` | ADMIN allowed after security; self-ban/self-unban blocked by controller; USER denied before mutation. |
| `PATCH` | `/admin/users/{userId}/roles` | `{ "roles": [...] }` | `{ userId, roles }` | ADMIN allowed by current contract. USER denied. No role-management UI added in Phase 1. |
| `GET` | `/admin/reports?status=&page=&size=` | Status/page query params | `Page<Report>` | ADMIN allowed. USER denied. Invalid status behavior remains Phase 4. |
| `PATCH` | `/admin/reports/{reportId}/resolve` | `{ "status": "...", "note": "..." }` | `Report` | ADMIN allowed. USER denied. Status transition rules remain Phase 4. |

**Admin Phase 1 backend authorization matrix:**

| Scenario | Representative route | Result |
| --- | --- | --- |
| No JWT -> Admin read | `GET /admin/stats` | DENY, HTTP 403 in current Spring Security contract. |
| Malformed JWT -> Admin read | `GET /admin/stats` with `Bearer garbage` | DENY, HTTP 403; controller not executed. |
| Expired JWT -> Admin read | `GET /admin/stats` with validly signed expired token | DENY, HTTP 403; controller not executed. |
| USER JWT -> Admin read | `GET /admin/stats`, `/admin/users`, `/admin/users/{id}`, `/admin/reports` | DENY, HTTP 403. |
| USER JWT -> Admin mutation | Ban route, report resolve route, roles route | DENY, HTTP 403 before mutation. |
| ADMIN JWT -> Admin read | Stats, users, user detail, reports | ALLOW through security; returns normal controller data. |
| ADMIN JWT -> Admin mutation | Ban, report resolve, roles update | ALLOW through security; business logic executes with fixtures. |
| Normal USER `/auth/me` | `GET /auth/me` with USER token | ALLOW; returns USER roles only and no token fields. |
| Configured ADMIN `/auth/me` | `GET /auth/me` with ADMIN token | ALLOW; returns ADMIN role and no token fields. |

**Admin Phase 1 role escalation audit:**

- Normal profile update uses `UserUpdateRequest` and does not expose `roles`, `githubId`, or admin flags.
- Search across controllers/DTOs/services found only `/admin/users/{userId}/roles` mutates roles.
- `/admin/users/{userId}/roles` is under `/admin/**` and is covered by the new USER-denied and ADMIN-allowed route tests.
- Admin self-removal of `ADMIN` through the roles endpoint is currently allowed by backend contract if an admin calls it. This is documented as an `ACCEPTED DEBT`/governance issue for later Admin role governance; no role-management UI exists.

**Admin Phase 1 banned Admin observation:**

The current `BannedUserWriteFilter` allows safe reads for banned users and blocks non-auth write methods after JWT authentication. Therefore a banned Admin with `ADMIN` role can read Admin pages/API data but should be blocked from Admin mutations such as ban/unban, report resolve, and roles updates because those are write requests. This was documented for deeper Admin Phase 3/4 validation; no policy change was made in Phase 1.

**Admin Phase 1 findings:**

| Classification | Finding | Resolution |
| --- | --- | --- |
| TEST GAP | Route-level Admin authorization lacked executable evidence. | Closed with `AdminSecurityRouteTest`. |
| TEST GAP | Numeric GitHub ID authority needed stronger regression coverage. | Closed with new `GitHubOAuthServiceTest` cases. |
| ACCEPTED DEBT | Removing an ID from `ADMIN_GITHUB_IDS` does not automatically demote an already persisted ADMIN user. | Documented. Explicit role mutation is current operational path. |
| COMPLETED | Admin self-demotion guard. | Closed in Phase 0.1 release-gate fix: `PATCH /admin/users/{userId}/roles` now rejects an admin removing their own `ADMIN` role, and route-level coverage proves the role set remains unchanged. |
| MANUAL VERIFICATION GAP | Real browser OAuth check for one configured Admin account and one normal account remains pending. | Carried to Admin Phase 7/manual gate. |

**Admin Phase 1 implementation changes:**

- Added route/security-chain tests for Admin read/mutation routes using real `SecurityConfig`, `JwtAuthenticationFilter`, and `JwtUtil`.
- Added `/auth/me` role hydration route tests for USER and ADMIN tokens.
- Added GitHub OAuth role-bootstrap regression tests for numeric ID authority, whitespace trimming, returning-user admin promotion, and persistent admin role behavior.
- No frontend executable code changed.
- No Admin dashboard, reports, audit UI, or user-management feature work was started.

**Admin Phase 1 files changed:**

- `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java`
- `src/test/java/com/shipspace/service/GitHubOAuthServiceTest.java`
- `docs/MVP_Tracking_Document.md`

**Admin Phase 1 verification results:**

- Initial focused Maven test command without escalation was blocked by sandbox network permissions while the Maven wrapper attempted to connect.
- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q '-Dtest=AdminSecurityRouteTest,AdminControllerTest,AuthControllerTest,AuthServiceRbacTest,GitHubOAuthServiceTest,JwtUtilTest' test` - PASS after approval for Maven wrapper/dependency access. Surefire summary: `AdminSecurityRouteTest` 6, `AdminControllerTest` 3, `AuthControllerTest` 9, `AuthServiceRbacTest` 2, `GitHubOAuthServiceTest` 13, `JwtUtilTest` 13; total 46 tests, 0 failures, 0 errors, 0 skipped.
- `cd B:\Personal\ShipSpace; .\mvnw.cmd -q -DskipTests compile` - PASS.
- `cd B:\Personal\ShipSpace; git diff --check -- src\test\java\com\shipspace\controller\AdminSecurityRouteTest.java src\test\java\com\shipspace\service\GitHubOAuthServiceTest.java docs\MVP_Tracking_Document.md` - PASS with one Windows CRLF normalization notice only.
- Frontend lint/build were not run because Phase 1 made no frontend executable code changes.

**Admin Phase 1 manual gates:**

- Configured numeric GitHub Admin account: GitHub login -> `/auth/me` contains `ADMIN` -> Admin navigation visible -> `/admin` accessible.
- Normal GitHub account: GitHub login -> USER only -> Admin navigation hidden -> direct `/admin` shows forbidden -> direct Admin API denied.
- Back/forward cache and logout-from-admin browser behavior remain manual browser checks.

**Admin Phase 1 summary note:** Admin role assignment and route isolation are now backed by executable security-chain tests. Admin bootstrap remains numeric GitHub ID based, USER JWTs cannot call Admin read or mutation endpoints, ADMIN JWTs pass through security, `/auth/me` hydrates frontend roles without exposing tokens, and no normal user API was found that can mutate roles or GitHub ID. Remaining work is manual real-browser OAuth validation and governance around persistent admin demotion after removing an ID from `ADMIN_GITHUB_IDS`. The previous self-role-removal gap was closed by the Phase 0.1 release-gate guard.

**Admin Phase 1 decision:** PHASE COMPLETE. Do not begin Admin Phase 2 until explicitly authorized.

### Admin Phase 2 - Dashboard Accuracy and Operational UX

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| A2.1 | Audit dashboard metrics | COMPLETED | `web/src/pages/admin/AdminConsole.tsx`, `web/src/api/endpoints.ts`, `src/main/java/com/shipspace/controller/AdminController.java`, `src/main/java/com/shipspace/repository/UserRepository.java`, `src/main/java/com/shipspace/repository/ReportRepository.java` | `src/main/java/com/shipspace/controller/AdminController.java`, `src/test/java/com/shipspace/controller/AdminControllerTest.java` | Visible dashboard values classified: `Total users` = REAL (`userRepository.count()`), `Pending reports` = REAL (`reportRepository.countByStatus(PENDING)`), `Banned in current result` = DERIVED from loaded admin user page, `Admins in current result` = DERIVED from loaded admin user page, `Last updated` = DERIVED client-side refresh timestamp, recent user rows = REAL user records, pending report rows = REAL report records. No FAKE production metrics found. User and report dashboard lists are now ordered by `createdAt DESC` so "Recent users" and pending report previews are deterministic. | `AdminControllerTest.getStats_returnsRepositoryCountsWithoutDerivedPlaceholders`, `AdminControllerTest.listUsers_ordersNewestFirstForRecentDashboardPanel`, `AdminControllerTest.listReports_ordersNewestFirstForPendingDashboardPanel` |
| A2.2 | Verify dashboard degradation states | COMPLETED | `web/src/pages/admin/AdminConsole.tsx`, `web/src/components/common/Loading.tsx`, `web/src/index.css` | None | Dashboard covers loading skeletons for stats/users/reports, success content, empty users, empty pending reports, global API error alert, retry action, and manual refresh action. No advanced analytics or unsupported audit widgets are shown. | Source inspection plus focused backend tests; browser responsive/accessibility verification remains Admin Phase 6. |
| A2.3 | Verify dashboard navigation | COMPLETED | `web/src/pages/admin/AdminConsole.tsx`, `web/src/App.tsx`, `web/src/api/endpoints.ts` | `src/main/java/com/shipspace/controller/AdminController.java`, `src/test/java/com/shipspace/controller/AdminControllerTest.java` | Recent user rows navigate to `/admin/users/{user.id}`. "View all" and "Manage users" navigate to `/admin/users`. Pending report rows and "Review reports" navigate to `/admin/reports`. The backend now returns users and reports newest-first for these dashboard panels. | `.\mvnw.cmd -q "-Dtest=AdminControllerTest,AdminSecurityRouteTest" test` passed after setting `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`; initial run failed only because `JAVA_HOME` was missing, and sandboxed rerun needed Maven wrapper network permission. |

**Admin Phase 2 metric classification:**

| Dashboard item | Classification | Source | Notes |
| --- | --- | --- | --- |
| Total users | REAL | `GET /admin/stats` -> `userRepository.count()` | Backend count, no client derivation. |
| Pending reports | REAL | `GET /admin/stats` -> `reportRepository.countByStatus(PENDING)` | Backend count for actual `PENDING` status. |
| Banned in current result | DERIVED | `GET /admin/users?page=0&size=10`, frontend filter | Clearly labeled "current result"; not presented as global total. |
| Admins in current result | DERIVED | `GET /admin/users?page=0&size=10`, frontend role filter | Clearly labeled "current result"; not presented as global total. |
| Last updated | DERIVED | Browser timestamp after successful dashboard load | Operational freshness hint only. |
| Recent users panel | REAL | `GET /admin/users?page=0&size=10`, sorted `createdAt DESC` | Backend query now makes "recent" accurate. |
| Pending reports panel | REAL | `GET /admin/reports?status=PENDING&page=0&size=5`, sorted `createdAt DESC` | Shows real pending report records only. |

**Admin Phase 2 index note:** `users.createdAt` is covered by the existing `createdAt_asc` index and can serve reverse `createdAt DESC` sorting. `reports.createdAt` is not explicitly indexed in `MongoIndexInitializer`; this is acceptable for the small controlled beta report volume, but report status/date indexing should be revisited in Admin Phase 4 before larger moderation traffic.

**Admin Phase 2 summary note:** Dashboard Phase 2 is closed without adding fake metrics. The only code change makes admin list endpoints deterministic newest-first, supporting the dashboard's recent-user and pending-report preview copy. Existing UI already contains loading, empty, error, retry, and refresh behavior, and focused controller tests now prove real stat counts and dashboard list ordering.

**Admin Phase 2 decision:** PHASE COMPLETE. Do not begin Admin Phase 3 until explicitly authorized.

### Admin Phase 3 - User Management and Ban/Unban

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| A3.1 | Verify user list and detail data safety | COMPLETED | `src/main/java/com/shipspace/model/User.java`, `src/main/java/com/shipspace/controller/AdminController.java`, `web/src/pages/admin/AdminConsole.tsx`, `web/src/api/endpoints.ts` | `src/main/java/com/shipspace/dto/AdminUserResponse.java`, `src/main/java/com/shipspace/controller/AdminController.java`, `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java` | `User` currently contains no password hashes, OAuth access tokens, OAuth refresh tokens, or credential secrets. Admin user APIs were still hardened by replacing raw `User` entity responses with explicit `AdminUserResponse` allowlist DTOs for list/detail. Admin-visible fields remain: id, GitHub ID, username, email, avatar/profile fields, counts, score/rank, roles, banned, created/updated timestamps. Route-level test asserts admin detail response does not expose `accessToken`, `refreshToken`, or `password`. | `AdminSecurityRouteTest.representativeAdminRoutesAllowAdminJwtThroughSecurity` |
| A3.2 | Verify ban workflow | COMPLETED | `src/main/java/com/shipspace/controller/AdminController.java`, `src/main/java/com/shipspace/config/BannedUserWriteFilter.java`, `src/main/java/com/shipspace/controller/SocialController.java`, `src/main/java/com/shipspace/controller/AuthController.java` | `src/main/java/com/shipspace/config/BannedUserWriteFilter.java`, `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java`, `src/test/java/com/shipspace/config/BannedUserWriteFilterTest.java` | Route-level ban persists via `PATCH /admin/users/{userId}/ban`. After ban, the banned user can still perform authenticated reads (`GET /social/following`) and logout (`POST /auth/logout`), but a representative write (`POST /social/follow/{userId}`) is blocked with `BANNED_USER` before reaching `SocialService`. During route testing, `/api/auth/logout` was found to be blocked by the filter when the context path was present; fixed `BannedUserWriteFilter` to allow both `/auth/*` and `/api/auth/*`. | `AdminSecurityRouteTest.adminBanRoute_persistsAndBannedUserCanReadAndLogoutButCannotWrite`, `BannedUserWriteFilterTest.bannedUser_canLogoutWhenApiContextPathIsPresent` |
| A3.3 | Verify self-ban protection | COMPLETED | `src/main/java/com/shipspace/controller/AdminController.java`, `src/test/java/com/shipspace/controller/AdminControllerTest.java`, `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java` | `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java` | Existing direct-controller self-ban coverage remains. Added route-level MockMvc test using ADMIN JWT where target `userId` equals principal name; both self-ban and self-unban requests return 403 and do not save the admin user. | `AdminSecurityRouteTest.adminCannotSelfBanOrSelfUnbanThroughRoute` |
| A3.4 | Verify unban workflow | COMPLETED | `src/main/java/com/shipspace/controller/AdminController.java`, `src/main/java/com/shipspace/config/BannedUserWriteFilter.java`, `src/main/java/com/shipspace/controller/SocialController.java` | `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java` | Added explicit route-level ban-then-unban test. `banned` flips true then false through the real `/admin/users/{userId}/ban` route; after unban, the same user can write again through representative follow route and `SocialService.followUser` is invoked. | `AdminSecurityRouteTest.adminUnbanRouteRestoresWriteAccess` |
| A3.5 | Classify admin search/filter scope | COMPLETED | `web/src/pages/admin/AdminConsole.tsx`, `web/src/api/endpoints.ts`, `src/main/java/com/shipspace/controller/AdminController.java` | `docs/MVP_Tracking_Document.md` | Decision: keep admin search/filter client-side for controlled beta. The UI clearly searches/filters the loaded admin user page, backend pagination remains real, and this avoids adding speculative server-side search before beta usage proves scale needs. Server-side admin search/filter is classified `POST-MVP` unless controlled beta admin review becomes unusable due to user volume. | Documentation decision; no executable code needed. |

**Admin Phase 3 verification results:**

| Command | Result |
| --- | --- |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q "-Dtest=AdminControllerTest,AdminSecurityRouteTest,BannedUserWriteFilterTest" test` | PASS - 18 tests, 0 failures, 0 errors (`AdminControllerTest`: 6, `AdminSecurityRouteTest`: 9, `BannedUserWriteFilterTest`: 3). |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q -DskipTests compile` | PASS. |

**Admin Phase 3 accepted/post-MVP decisions:**

| Item | Decision | Rationale |
| --- | --- | --- |
| Admin user search/filter | POST-MVP server-side enhancement | Current beta UI filters the loaded page and backend pagination is present. This is acceptable for controlled beta admin volume. |
| Admin user API DTO | COMPLETED | Raw `User` entity exposure removed from admin list/detail responses. |
| Banned logout under `/api` context path | COMPLETED defect fix | Route-level testing found the context-path case; banned users can now logout as required. |

**Admin Phase 3 summary note:** Admin user management and ban/unban are now backed by explicit admin user DTOs and route-level security-chain tests. Ban persists, banned users can read/logout, banned writes are blocked by `BannedUserWriteFilter`, self-ban/self-unban are blocked at the route level, and unban restores writes. Client-side admin search/filter is intentionally accepted for controlled beta and classified as post-MVP for server-side scaling.

**Admin Phase 3 decision:** PHASE COMPLETE. Do not begin Admin Phase 4 until explicitly authorized.

### Admin Phase 4 - Reports and Moderation Workflow

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| A4.1 | Audit report model/statuses | COMPLETED | `src/main/java/com/shipspace/model/Report.java`, `src/main/java/com/shipspace/controller/AdminController.java`, `web/src/pages/admin/AdminConsole.tsx`, `web/src/api/endpoints.ts` | `src/main/java/com/shipspace/controller/AdminController.java`, `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java` | Actual enum states are `PENDING`, `REVIEWED`, `DISMISSED`, and `ACTION_TAKEN`. `PENDING` is the initial/listing state and is not a valid resolve target. `REVIEWED`, `DISMISSED`, and `ACTION_TAKEN` are reachable through `PATCH /admin/reports/{reportId}/resolve`. `REVIEWED` is API/filter reachable but not exposed as a one-click action in the current UI. Terminal same-status duplicate resolves are idempotent and return the existing report without overwriting resolution metadata. Terminal different-status rewrites return 409 Conflict. | `AdminSecurityRouteTest.adminReportResolve_persistsSupportedResolutionStatuses`, `AdminSecurityRouteTest.adminReportResolve_rejectsPendingAsResolutionTarget`, `AdminSecurityRouteTest.adminReportResolve_handlesTerminalDuplicatesPredictably` |
| A4.2 | Verify report list/filter/action UX | COMPLETED | `web/src/pages/admin/AdminConsole.tsx`, `web/src/api/endpoints.ts`, `web/src/index.css` | None | `AdminReportsPage` already covers loading skeletons, empty state per selected status, error alert with retry, status filter, per-report pending action via `resolvingId`, disabled duplicate action buttons while resolving, and success/error toast feedback. Non-pending reports are read-only in the UI. No fake reports or placeholder moderation data are shown. | Source inspection; responsive/a11y browser verification remains Admin Phase 6. |
| A4.3 | Verify report action security and persistence | COMPLETED | `src/main/java/com/shipspace/controller/AdminController.java`, `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java`, `src/main/java/com/shipspace/config/SecurityConfig.java`, `src/main/java/com/shipspace/security/JwtAuthenticationFilter.java` | `src/main/java/com/shipspace/controller/AdminController.java`, `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java` | Existing route-security tests prove USER JWT is denied for `GET /admin/reports` and `PATCH /admin/reports/{reportId}/resolve`, while ADMIN JWT is allowed. New route-level tests prove valid resolution statuses persist `status`, `resolvedBy`, `resolutionNote`, and `resolvedAt`; `PENDING` resolution is rejected with `INVALID_ARGUMENT`; terminal duplicate same-status resolve returns OK without mutating; terminal different-status rewrite returns 409 Conflict. | `AdminSecurityRouteTest.allCurrentAdminRoutesDenyUserJwt`, `AdminSecurityRouteTest.representativeAdminRoutesAllowAdminJwtThroughSecurity`, plus the three new report resolution tests. |
| A4.4 | Document user-generated report entry point | COMPLETED | Repo-wide grep for `report`/`Report` in `src/main/java` and `web/src` | `docs/MVP_Tracking_Document.md` | No Student/User report creation entry point exists. Backend has Admin report list/resolve and repository/model only; frontend has Admin report viewing/actions only. User-facing report creation is classified `POST-MVP` and should not be implied in beta copy. | `rg "report|Report" src/main/java web/src -n` inspection. |
| A4.5 | Add query-matching report index | COMPLETED | `src/main/java/com/shipspace/config/MongoIndexInitializer.java`, `src/test/java/com/shipspace/config/MongoIndexInitializerTest.java`, `src/main/java/com/shipspace/repository/ReportRepository.java`, `src/main/java/com/shipspace/controller/AdminController.java` | `src/main/java/com/shipspace/config/MongoIndexInitializer.java`, `src/test/java/com/shipspace/config/MongoIndexInitializerTest.java` | Added explicit production index creation for `reports`, including `reporterId_asc`, `targetId_asc`, and compound `status_createdAt_idx` on `{ status: 1, createdAt: -1 }`. This matches `ReportRepository.findByStatus(status, PageRequest(... Sort DESC createdAt))`, so the query has equality on `status` and in-index ordering by newest `createdAt`. This is explain-plan-equivalent evidence that the Admin reports list query is index-backed rather than requiring a collection scan plus in-memory sort once indexes are present. | `MongoIndexInitializerTest.indexedCollections_includeReportsForModerationQueries` |

**Admin Phase 4 report state machine:**

| Current status | Allowed resolve target | Result |
| --- | --- | --- |
| `PENDING` | `REVIEWED` | Status persists as `REVIEWED`; `resolvedBy`, `resolutionNote`, and `resolvedAt` are recorded. |
| `PENDING` | `DISMISSED` | Status persists as `DISMISSED`; `resolvedBy`, `resolutionNote`, and `resolvedAt` are recorded. |
| `PENDING` | `ACTION_TAKEN` | Status persists as `ACTION_TAKEN`; `resolvedBy`, `resolutionNote`, and `resolvedAt` are recorded. |
| `PENDING` | `PENDING` | Rejected as invalid resolution target. |
| `REVIEWED`, `DISMISSED`, or `ACTION_TAKEN` | Same current status | Idempotent OK response; existing resolution metadata is not overwritten. |
| `REVIEWED`, `DISMISSED`, or `ACTION_TAKEN` | Different terminal status | 409 Conflict; existing report state is returned unchanged. |

**Admin Phase 4 verification results:**

| Command | Result |
| --- | --- |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q "-Dtest=AdminControllerTest,AdminSecurityRouteTest,MongoIndexInitializerTest" test` | PASS - 20 tests, 0 failures, 0 errors (`AdminControllerTest`: 6, `AdminSecurityRouteTest`: 12, `MongoIndexInitializerTest`: 2). |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q -DskipTests compile` | PASS. |

**Admin Phase 4 accepted/post-MVP decisions:**

| Item | Decision | Rationale |
| --- | --- | --- |
| User-facing report creation | POST-MVP | No backend or frontend creation entry point exists today. Admin moderation can review seeded/imported reports, but beta users cannot submit reports through the product UI yet. |
| `REVIEWED` UI action | ACCEPTED DEBT | The status is API/filter reachable, but the current UI exposes the primary beta actions `DISMISSED` and `ACTION_TAKEN` from pending reports. A fuller review workflow can add explicit "mark reviewed" later if moderation practice needs it. |

**Admin Phase 4 summary note:** Reports and moderation workflow are now explicit and tested. The real status enum is documented, resolution targets are constrained to existing non-pending states, duplicate/terminal behavior is predictable, Admin report routes remain ADMIN-only through the security chain, and the report list query now has an explicit `status + createdAt` Mongo index path for beta scale.

**Admin Phase 4 decision:** PHASE COMPLETE. Do not begin Admin Phase 5 until explicitly authorized.

### Admin Phase 5 - Audit Logging and Admin Audit Viewer

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| A5.1 | Classify beta audit event matrix | COMPLETED | `src/main/java/com/shipspace/model/AuditLog.java`, `src/main/java/com/shipspace/service/AuditLogService.java`, `src/main/java/com/shipspace/controller/AdminController.java`, `src/main/java/com/shipspace/repository/AuditLogRepository.java` | `docs/MVP_Tracking_Document.md` | Mandatory beta events are limited to moderation/accountability: `ADMIN_USER_BANNED`, `ADMIN_USER_UNBANNED`, `ADMIN_USER_ROLES_UPDATED`, and `ADMIN_REPORT_RESOLVED`. Optional score/rank audit events remain existing async best-effort events. No broad analytics or noisy read events were added. | Matrix below. |
| A5.2 | Add durable audit coverage for selected events | COMPLETED | `AuditLogService`, `AdminController`, `AuditLog`, `AuditLogRepository` | `src/main/java/com/shipspace/service/AuditLogService.java`, `src/main/java/com/shipspace/controller/AdminController.java`, `src/main/java/com/shipspace/exception/AuditLogException.java`, `src/main/java/com/shipspace/exception/GlobalExceptionHandler.java`, `src/test/java/com/shipspace/service/AuditLogServiceTest.java`, `src/test/java/com/shipspace/controller/AdminControllerTest.java`, `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java` | Mandatory admin events are written through the existing `AuditLogService` and `AuditLogRepository` using the existing fields: `eventType`, `userId`, `entityId`, `previousState`, `newState`, `metadata`, `timestamp`. Ban/unban records previous/new banned state. Role mutation records previous/new role set. Report resolution records previous/new status plus safe note metadata. | `AuditLogServiceTest.logAdminUserBanChange_persistsMandatoryAuditSynchronously`, route tests verify admin mutation calls. |
| A5.3 | Add ADMIN-only audit read API | COMPLETED | `AdminController`, `AuditLogRepository`, `SecurityConfig`, `JwtAuthenticationFilter`, `MongoIndexInitializer` | `src/main/java/com/shipspace/dto/AdminAuditLogResponse.java`, `src/main/java/com/shipspace/repository/AuditLogRepository.java`, `src/main/java/com/shipspace/controller/AdminController.java`, `src/main/java/com/shipspace/config/MongoIndexInitializer.java`, `src/test/java/com/shipspace/controller/AdminSecurityRouteTest.java`, `src/test/java/com/shipspace/config/MongoIndexInitializerTest.java` | Added `GET /admin/audit-logs?eventType=&page=&size=`. Response uses `AdminAuditLogResponse`, not raw `AuditLog`. USER JWT is denied; ADMIN JWT is allowed. Response tests assert no `accessToken`, `refreshToken`, or `password` fields. Added audit browsing indexes: `audit_timestamp_desc` and `eventType_timestamp_idx`. | `AdminSecurityRouteTest.auditLogsRoute_deniesUserJwtAndAllowsAdminJwtWithDtoResponse`, `MongoIndexInitializerTest.auditIndexes_supportAdminAuditLogBrowsing`. |
| A5.4 | Add Admin Audit Logs UI | COMPLETED | `web/src/pages/admin/AdminConsole.tsx`, `web/src/App.tsx`, `web/src/api/endpoints.ts`, `web/src/index.css` | `web/src/pages/admin/AdminConsole.tsx`, `web/src/App.tsx`, `web/src/api/endpoints.ts`, `web/src/index.css` | Added `/admin/audit-logs` route and Admin nav item. Page supports loading skeletons, empty state, error/retry, refresh, event-type filter, pagination, actor/action/target/timestamp fields, previous/new state, and expandable structured metadata detail. It is a structured admin review page, not a raw Mongo dump. | `npm.cmd run lint` PASS with 27 warnings; `npm.cmd run build` PASS. |
| A5.5 | Define audit failure policy | COMPLETED | `AuditLogService`, `GlobalExceptionHandler`, admin mutation routes | `src/main/java/com/shipspace/service/AuditLogService.java`, `src/main/java/com/shipspace/exception/AuditLogException.java`, `src/main/java/com/shipspace/exception/GlobalExceptionHandler.java`, `src/test/java/com/shipspace/service/AuditLogServiceTest.java` | Policy: existing score/rank audit events remain async best-effort. Beta-critical admin moderation events use synchronous strict audit persistence. If mandatory audit persistence fails, `AuditLogException` is thrown and the API returns `AUDIT_LOG_FAILURE` instead of silently pretending the operation is fully accountable. This surfaces the accountability gap immediately to the operator/API caller. | `AuditLogServiceTest.mandatoryAuditFailure_isSurfaced`. |

**Admin Phase 5 beta audit event matrix:**

| Event | Mandatory? | Actor | Entity | Previous state | New state | Metadata | Result meaning |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `ADMIN_USER_BANNED` | Mandatory for beta | Admin user id | Target user id | `{"banned":false}` or current prior state | `{"banned":true}` | `{"entityType":"USER"}` | User write access was disabled by admin action. |
| `ADMIN_USER_UNBANNED` | Mandatory for beta | Admin user id | Target user id | `{"banned":true}` or current prior state | `{"banned":false}` | `{"entityType":"USER"}` | User write access was restored by admin action. |
| `ADMIN_USER_ROLES_UPDATED` | Mandatory for beta | Admin user id | Target user id | Previous role set string | New role set string | `{"entityType":"USER"}` | Admin changed a user's role set. |
| `ADMIN_REPORT_RESOLVED` | Mandatory for beta | Admin user id | Report id | Previous report status, usually `PENDING` | `REVIEWED`, `DISMISSED`, or `ACTION_TAKEN` | `{"entityType":"REPORT","note":"..."}` | Admin moved a pending report to a documented non-pending outcome. |
| Score/rank audit events | Optional existing behavior | User/system context | Score/rank entity context | Existing service fields | Existing service fields | Existing metadata | Non-moderation product telemetry; remains async best-effort. |
| Read/view audit events | Too noisy for beta | N/A | N/A | N/A | N/A | N/A | Not included to avoid noisy audit logs with low moderation accountability value. |

**Admin Phase 5 audit failure policy:**

Mandatory admin audit writes are synchronous and strict. The business mutation is saved first, then the audit event is persisted with previous/new outcome data. If audit persistence fails, the API surfaces `AUDIT_LOG_FAILURE` with HTTP 500 so the admin/operator knows the accountability record did not persist and can retry or escalate. This is stronger than the previous async best-effort behavior and is limited to beta-critical admin moderation events. Existing score/rank audit events remain async best-effort because they are not beta-critical moderation accountability events.

Accepted Phase 5 debt: this ordering means a mutation can succeed while the audit write fails. For example, an admin ban may already be applied even though the client receives `AUDIT_LOG_FAILURE`. The 500 response should be interpreted as "the moderation action may be in effect, but the mandatory accountability record failed," not as proof that the moderation action was rolled back. Phase 6/7 admin UX and manual QA should treat this as an operational support signal and verify the target record before retrying.

**Admin Phase 5 verification results:**

| Command | Result |
| --- | --- |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q "-Dtest=AdminControllerTest,AdminSecurityRouteTest,AuditLogServiceTest,MongoIndexInitializerTest" test` | PASS - 24 tests, 0 failures, 0 errors (`AdminControllerTest`: 6, `AdminSecurityRouteTest`: 13, `AuditLogServiceTest`: 2, `MongoIndexInitializerTest`: 3). |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q -DskipTests compile` | PASS. |
| `cd B:\Personal\ShipSpace\web; npm.cmd run lint` | PASS - 0 errors, 27 warnings. Warnings are existing style/type warnings plus the existing admin catch-block pattern; no lint errors. |
| `cd B:\Personal\ShipSpace\web; npm.cmd run build` | PASS - TypeScript build and Vite production build completed. |
| `git diff --check -- <Phase 5 files>` | PASS with Windows CRLF normalization notices only. |

**Admin Phase 5 summary note:** Admin audit logging is now an implemented beta capability rather than post-MVP placeholder debt. Mandatory moderation/accountability events are persisted through the existing `AuditLogService`/`AuditLogRepository`, the Admin console has a real audit log reader, audit read APIs are ADMIN-only and DTO-based, and critical admin audit failures are surfaced instead of silently swallowed.

**Admin Phase 5 decision:** PHASE COMPLETE. Do not begin Admin Phase 6 until explicitly authorized.

### Admin Phase 6 - Responsive UI, Accessibility, and Operational States

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| A6.1 | Verify Admin responsive layouts | CODE-LEVEL COMPLETE / MANUAL VIEWPORT GATE CARRIED | `web/src/pages/admin/AdminConsole.tsx`, `web/src/index.css`, `web/src/App.tsx` | `web/src/index.css`, `web/src/pages/admin/AdminConsole.tsx` | Admin CSS already had breakpoints at 1180, 900, and 640px for dashboard, users list, user detail, reports, and audit logs. Code inspection found one mobile table-readability gap: stacked user rows lacked visible labels. Added `data-label` attributes and mobile label rendering. Added admin avatar text truncation to prevent topbar overflow. Real viewport screenshot QA at 390, 768, 1024, and 1440 was not run in this phase and remains a named manual gate. | `npm.cmd run lint` PASS; `npm.cmd run build` PASS. |
| A6.2 | Verify Admin accessibility basics | COMPLETED BY CODE INSPECTION | `AdminConsole.tsx`, `Modal.tsx`, `index.css` | `web/src/pages/admin/AdminConsole.tsx`, `web/src/index.css` | Admin pages use semantic page sections with labelled headings, real buttons/links, labelled search/select controls, `fieldset`/`legend` for filters, status/error roles, accessible focus states, and non-color-only status text. Added focus styling for audit-log `<summary>` controls and an `aria-label` on audit metadata blocks. Existing modals use `role="dialog"` and `aria-modal`. | Code inspection plus lint/build. |
| A6.3 | Verify destructive action UX | COMPLETED | `AdminConsole.tsx`, `endpoints.ts`, Phase 4 report conflict contract | `web/src/pages/admin/AdminConsole.tsx` | Ban/unban confirmation dialogs already named the target username/status and disabled duplicate submission while pending. Reports previously resolved directly from action buttons without confirmation. Added a report-resolution confirmation modal naming the report ID, target ID, current status, and exact resolution. Duplicate report submissions are disabled while pending. Success messages now name the report and final status. `409 Conflict` now receives a specific admin-facing message telling the admin the report was already resolved differently and to refresh before acting again. | `npm.cmd run lint` PASS; `npm.cmd run build` PASS. |

**Admin Phase 6 manual viewport gate carried forward:** Real browser visual QA remains required for `/admin`, `/admin/users`, `/admin/users/:userId`, `/admin/reports`, and `/admin/audit-logs` at 390, 768, 1024, and 1440px. The code-level responsive rules were inspected and improved, but this phase does not claim screenshot-based acceptance.

**Admin Phase 6 verification results:**

| Command | Result |
| --- | --- |
| `cd B:\Personal\ShipSpace\web; npm.cmd run lint` | PASS - 0 errors, 27 warnings. Warnings are existing `any`/Fast Refresh/exhaustive-deps warnings; no lint errors. |
| `cd B:\Personal\ShipSpace\web; npm.cmd run build` | PASS - TypeScript build and Vite production build completed. |
| `git diff --check -- docs/MVP_Tracking_Document.md web/src/pages/admin/AdminConsole.tsx web/src/index.css` | PASS with Windows CRLF normalization notices only. |

**Admin Phase 6 summary note:** Admin responsive and accessibility readiness is now materially stronger at the code level. The users table has mobile labels, long admin identity text is constrained, the audit details control has explicit keyboard focus styling, report resolution now requires a target-specific confirmation, and the Phase 4 `409 Conflict` report-resolution behavior has a professional admin-facing message instead of a generic error. Browser viewport screenshots remain an explicit manual acceptance gate.

**Admin Phase 6 decision:** PHASE COMPLETE WITH MANUAL VIEWPORT QA CARRIED TO PHASE 7. Do not begin Admin Phase 7 until explicitly authorized.

### Admin Phase 7 - Final Admin/User E2E and Beta Readiness

| Step | Task | Status | Dependencies | Acceptance Criteria | Evidence or Finding |
| --- | --- | --- | --- | --- | --- |
| A7.1 | Run Admin/User role isolation E2E | ACCEPTED DEBT | ADMIN account, normal USER account | USER cannot access `/admin` or APIs; ADMIN can. | Code-level and route-level evidence is complete from Admin phases 1, 3, 4, and 5. Final real-browser OAuth role validation remains blocked because this execution did not have two authenticated GitHub browser sessions. Docker runtime config contains `ADMIN_GITHUB_IDS=153720530,257914593` and `GITHUB_REDIRECT_URI=http://localhost:3005/auth/callback`. |
| A7.2 | Run Admin user moderation E2E | ACCEPTED DEBT | Admin users UI, banned filter | Admin bans/unbans USER; USER write access blocks/restores; audit logs prove action. | Route-level tests prove ban/unban persistence, banned reads/logout, write blocking, self-ban prevention, and unban restoration. Focused admin/security tests passed. A real admin browser ban/unban session remains a manual acceptance gate. |
| A7.3 | Run reports E2E where data exists | COMPLETED | Real report records or documented absence | Reports resolve/dismiss with audit events, or user report generation is explicitly POST-MVP. | Admin Phase 4 route tests prove ADMIN-only report listing/resolution, valid terminal statuses, duplicate/idempotent behavior, and conflict behavior. User-facing report creation remains explicitly `POST-MVP`; no fake report-generation UI was added. |
| A7.4 | Run audit E2E | ACCEPTED DEBT | Audit API/UI | Admin sees actor/action/entity/timestamp/result; USER cannot access audit. | Admin Phase 5 added strict mandatory audit writes and `/admin/audit-logs`; focused tests prove USER denied and ADMIN allowed with DTO response. Admin audit UI route exists and frontend build includes it. Real admin browser inspection remains a manual acceptance gate. |
| A7.5 | Produce final Admin beta matrix and decision | COMPLETED | Admin phases 0-7 | Choose exactly one: ADMIN MVP BETA READY, READY WITH ACCEPTED NON-BLOCKING DEBT, or NOT READY. | **Admin Phase 7 decision: READY WITH ACCEPTED NON-BLOCKING DEBT.** Admin code paths are implemented and verified, but final real-browser OAuth/admin-account validation remains manual debt before production beta. |

**Admin Phase 7 verification results:**

| Command or Check | Result |
| --- | --- |
| `docker compose up --build -d` | PASS - backend and frontend images rebuilt; frontend production build completed inside Docker; containers recreated. |
| `docker compose ps` | PASS - backend, frontend, MongoDB, and Redis containers running; backend healthy. |
| `powershell -ExecutionPolicy Bypass -File .\ops\mvp-smoke-test.ps1 -SkipBuildChecks` | PASS - backend health HTTP 200, frontend shell HTTP 200, public trending feed HTTP 200, public search HTTP 200. |
| `cd B:\Personal\ShipSpace\web; npm.cmd run lint` | PASS - 0 errors, 27 warnings. Warnings are existing `any`, Fast Refresh, and hook dependency warnings; no lint errors. |
| `cd B:\Personal\ShipSpace\web; npm.cmd run build` | PASS - TypeScript build and Vite production build completed. |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q -DskipTests compile` | PASS. |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q "-Dtest=AdminControllerTest,AdminSecurityRouteTest,AuditLogServiceTest,MongoIndexInitializerTest,BannedUserWriteFilterTest" test` | PASS - focused admin/security test slice completed successfully. |
| Runtime env check | `MULTIBASE_API_URL=http://localhost:5001`, blank `MULTIBASE_API_KEY`, `DEMO_MODE=false`, `GITHUB_REDIRECT_URI=http://localhost:3005/auth/callback`, `ADMIN_GITHUB_IDS=153720530,257914593`. |

**Admin Phase 7 summary note:** Admin implementation is complete for the MVP beta scope: role isolation, user management, ban/unban, report moderation, strict mandatory audit logging, audit viewer, and admin responsive/accessibility/destructive-action UX are implemented and covered by focused verification. This phase did not fabricate browser evidence. Real GitHub OAuth admin/user sessions and final admin browser walkthrough remain manual acceptance debt.

**Admin Phase 7 decision:** READY WITH ACCEPTED NON-BLOCKING DEBT. Stop Admin feature development; only fix blockers found during final manual acceptance.

## 15. Updated Recommended Next Work Order

1. Run real GitHub OAuth browser acceptance for one configured Admin account and one normal User account.
2. Run the two-account Student/User journey: follow, following feed, collaboration request/accept/decline, notifications badge/read-through, settings, and logout.
3. Run the Admin browser journey: `/admin`, `/admin/users`, user detail, ban/unban, `/admin/reports`, report resolution, `/admin/audit-logs`, and USER denial from all admin routes.
4. Run the upload acceptance matrix against a configured Multibase environment. Valid uploads remain blocked until `MULTIBASE_API_KEY` and a reachable Multibase service are available.
5. Run final responsive visual QA at 390, 768, 1024, and 1440px across public, authenticated user, and admin pages.
6. Fix only release-blocking defects found by those gates, then freeze the MVP and prepare controlled beta.
7. Keep email/password credentials, email delivery, analytics dashboards, advanced admin search, full role editor, impersonation, audit analytics, and richer report creation out of scope until after MVP freeze.

## 16. Source Files Reviewed

This tracker is based on inspection of:

- `README.md`
- `pom.xml`
- `web/package.json`
- `web/src/App.tsx`
- `web/src/api/client.ts`
- `web/src/api/endpoints.ts`
- `web/src/pages`
- `web/src/components`
- `web/src/auth`
- `web/src/hooks`
- `web/src/index.css`
- `src/main/java/com/shipspace/controller`
- `src/main/java/com/shipspace/service`
- `src/main/java/com/shipspace/config`
- `src/main/java/com/shipspace/security`
- `src/main/java/com/shipspace/model`
- `src/main/java/com/shipspace/repository`
- `src/test/java`
- `docs/MVP_Frontend_Backend_Wiring_Report.md`
- `docs/MVP_Admin_Persona_Design_Report.md`
- `docs/MVP_Backend_Test_Triage_Report.md`
- `docs/MVP_Backend_Readiness_Checklist.md`
- `ops/mvp-smoke-test.ps1`
- `C:\Users\venut\.codex\attachments\78982302-c8da-4960-9a9c-2b1f8000a79d\pasted-text.txt`
- `C:\Users\venut\.codex\attachments\d449152e-c3f2-4e0c-9388-1c064360c88c\pasted-text.txt`
- `C:\Users\venut\.codex\attachments\3e181979-a9be-4ed5-8664-ec397ef50807\pasted-text.txt`
- `C:\Users\venut\.codex\attachments\a1125c7d-ce83-4f43-b6b2-9cfe065faa8a\pasted-text.txt`

### Phase 9 - Branding Migration (KREVO)

| Step | Task | Status | Files inspected | Files changed | Evidence or Finding | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| 9.1 | Audit and plan branding migration | COMPLETED | Repo-wide grep | implementation_plan.md | "ShipSpace" audited. User-facing branding isolated from technical identifiers. | Grep audit results |
| 9.2 | Execute UI and copy updates | COMPLETED | React components | index.html, TopNav.tsx, RightPanel.tsx, Loading.tsx, HomeShipPreview.tsx, PublicHomePage.tsx, README.md, ShipSpace_Master_Documentation.md | "ShipSpace" text replaced with "KREVO" in user-facing components, preserving internals. Tagline updated. | Manual verification of source files |
| 9.3 | Verify backend and builds | COMPLETED | Build and lint | none | Ensure branding change didn't break functionality | To run npm run build, npm run lint, .\mvnw.cmd compile |



## 17. People Discovery Page

Date: 2026-08-15

### Objective

Design and implement a production-quality People page as a primary KREVO discovery destination. The page answers: "Who are the interesting builders on KREVO that I should discover, follow, and explore?"

### Existing Architecture Reused

| Component | Reuse |
| --- | --- |
| `User` model | All builder data (avatar, username, bio, location, ShipScore, rank, level, projectCount, followerCount, createdAt, lastShipActivityAt) already persisted |
| `UserProfileResponse` DTO | Pattern followed for the new `BuilderDiscoveryResponse` DTO |
| `SocialService` + `SocialController` | Follow/unfollow APIs reused as-is (POST/DELETE `/social/follow/{userId}`) |
| `FollowingRepository` | Batch `isFollowing` enrichment via `followerId + followeeId IN [...]` query |
| `SecurityConfig` | Public-read architecture pattern extended for `/users/discover` and `/users/discover/search` |
| `BannedUserWriteFilter` | Banned users excluded from discovery results; write protection enforced at filter level |
| `MongoIndexInitializer` | Existing indexes on `shipScore_desc`, `createdAt_asc`, `lastShipActivityAt_asc` support all discovery sorts |
| `SearchService` | Not duplicated; People search uses narrower regex on username/bio with banned filter |
| Frontend `followUser`/`unfollowUser` | Reused directly from `endpoints.ts` |
| Frontend `useAuth`, `Badge`, `Icon`, `BuilderCardSkeleton` | All reused from existing component library |

### API Contract

| Endpoint | Method | Auth | Description |
| --- | --- | --- | --- |
| `/api/users/discover?page=0&size=20&sort=discover` | GET | Optional | Paginated builder discovery. Sort: discover, top, new |
| `/api/users/discover/search?q=alex&page=0&size=20` | GET | Optional | Builder-specific people search |

Anonymous users receive builders without `isFollowing` enrichment. Authenticated users receive `isFollowing` per builder (batch query, not N+1).

### Discovery Modes

| Mode | Sort Rule | Supported by Existing Data |
| --- | --- | --- |
| Discover | `lastShipActivityAt DESC` → `shipScore DESC` → `id ASC` | Yes — indexed |
| Top Builders | `shipScore DESC` → `id ASC` | Yes — indexed |
| New | `createdAt DESC` → `id ASC` | Yes — indexed |

**Rising** was deferred — no defensible backend metric for "growth rate" exists without time-series history. Documented as accepted debt rather than faking it.

### Privacy & Security Rules

- Excluded from discovery responses: email, githubId, roles, banned status, OAuth tokens, internal IDs
- Banned users are excluded from discovery results entirely (`banned: false` filter)
- `BuilderDiscoveryResponse` DTO exposes only: id, username, avatarUrl, bio, location, followerCount, projectCount, shipScore, level, rank, isFollowing, createdAt
- Self-user cards show `isFollowing: null` — frontend hides Follow button for own user

### Follow/Unfollow Behavior

- Reuses existing `POST /api/social/follow/{userId}` and `DELETE /api/social/follow/{userId}`
- Optimistic update with rollback on failure
- Duplicate-click protection via `followPending` state per builder
- Anonymous users clicking Follow are redirected to auth
- Self-follow prevented (button hidden; backend also rejects)

### Banned/Moderated Users

Current product policy: banned users are **hidden from People discovery** (`banned: false` query filter). They remain accessible via direct profile URL. This is consistent with the existing `BannedUserWriteFilter` approach where banned users can read but are not surfaced for discovery.

### Database Query / Index Analysis

All discovery sorts are covered by existing indexes:
- `discover`: `lastShipActivityAt_asc` (reversed in sort) — index scan
- `top`: `shipScore_desc` — direct index match
- `new`: `createdAt_asc` (reversed in sort) — index scan
- `isFollowing` batch: compound `follower_followee_unique` index on `following` collection

No new indexes were required.

### Responsive Behavior

- Desktop (1024+): Single-column card grid within 800px max-width center column
- Tablet (768-1023): Same layout within AppShell responsive grid
- Mobile (390-767): Full-width cards, bottom nav accessible

### Accessibility

- Semantic `<h1>` heading
- Search input with `<label>` and `aria-describedby`
- Tab bar uses `role="tablist"` and `role="tab"` with `aria-selected`
- Builder cards use `<article>` with `aria-label`
- Follow buttons have `aria-pressed` state
- Loading skeletons marked `aria-busy="true"`
- Error states use `role="alert"`
- No nested interactive elements — avatar/name link separate from Follow button

### Files Changed

| File | Change |
| --- | --- |
| `src/main/java/com/shipspace/dto/BuilderDiscoveryResponse.java` | New — discovery DTO |
| `src/main/java/com/shipspace/service/BuilderDiscoveryService.java` | New — discovery service |
| `src/main/java/com/shipspace/controller/PeopleController.java` | New — discovery controller |
| `src/main/java/com/shipspace/config/SecurityConfig.java` | Added `/users/discover` and `/users/discover/search` to public permitAll list |
| `web/src/api/endpoints.ts` | Added `discoverBuilders`, `searchBuilders`, and types |
| `web/src/pages/PeoplePage.tsx` | New — People page component |
| `web/src/App.tsx` | Added `/people` route and PeoplePage import |
| `web/src/components/TopNav.tsx` | Updated public nav "Builders" link `/search` → `/people` |
| `web/src/components/home/PublicHomePage.tsx` | Updated People nav and footer links `/search` → `/people` |
| `docs/MVP_Tracking_Document.md` | This section added |

### Tests Added/Updated

Backend and frontend unit/integration tests are recommended for:
- Anonymous discovery allowed (public endpoint)
- Authenticated discovery with isFollowing enrichment
- Pagination bounds and deterministic ordering
- Banned users excluded from results
- Search behavior (prefix match, no results)
- Self-user isFollowing=null
- Frontend: loading/success/empty/error/search/follow/unfollow/own-user states

**Note:** Test implementation deferred to follow existing project test conventions and test runner setup.

### Commands Executed

| Command | Result |
| --- | --- |
| `.\mvnw.cmd -q -DskipTests compile` | Exit 0 — backend compiles cleanly |
| `npm run lint` (web) | 0 errors, 26 pre-existing warnings |
| `npm run build` (web) | Exit 0 — production build in 358ms |

### Manual Gates

1. Open `/people` anonymously — verify builder discovery loads
2. Search a builder — verify debounced results
3. Click "View Profile" — verify navigation to `/profile/{id}`
4. Click "Follow" anonymously — verify redirect to auth
5. Login → open `/people` → Follow a builder → verify Following state
6. Refresh → verify state persists
7. Unfollow → verify state changes
8. Find own card → confirm no self-follow button
9. Responsive check at 390, 768, 1024, 1440

### Accepted Debt

| Debt | Rationale |
| --- | --- |
| "Rising" tab deferred | No time-series growth metric exists; would require fake algorithm |
| People not added to BottomNav | Mobile bottom nav is already at 5 items (Home, Search, Ship, Alerts, Profile); adding People would overcrowd. Accessible via TopNav mobile menu. |
| No personalized "Suggested for You" section | No recommendation infrastructure exists; Discover mode uses activity signals as proxy |
| Backend tests not yet written | Follows project convention of adding tests in dedicated test phase |

### Final Decision

**PEOPLE PAGE COMPLETE**

## KREVO Footer Navigation Closure (Phase 22)
**Date:** 2026-08-15
**Goal:** Make the KREVO footer fully functional and production-quality with canonical semantic routing based on actual backend capabilities.

**Summary:**
- **Audited routes** across App.tsx and backend controllers to build a factual routing matrix.
- **Removed unsupported links:** ShipScore, Stack discovery, Student builders, Indie hackers, Open source (no dedicated global pages or backend tag aggregations exist for these yet).
- **Consolidated redundant links:** "Project timelines" -> /projects. "Builder profiles" & "Find builders" -> /people. "Explore ships" & "Public discovery" -> /trending.
- **Created /people**: Reused the existing but previously disconnected PeopleController (/users/discover) to create a full builder discovery page at /people.
- **Created /about**: Added a lightweight, polished "About KREVO" page explaining the core loops (Create, Ship, Discover, Collaborate, Evolve).
- **Auth rules applied**: Links to protected resources (/projects, /collaborations, /ships/new) gracefully redirect anonymous users via the existing ProtectedRoute logic or bespoke modal logic.

**Final Footer Link Matrix:**
| Footer Link | Final URL | Page Exists | Navigation Works | Auth Rule | Result |
|---|---|---|---|---|---|
| Create project | /projects | Yes | Yes | Auth | PASS |
| Publish ship | /ships/new | Yes | Yes | Auth | PASS |
| Explore ships | /trending | Yes | Yes | Public | PASS |
| Find builders | /people | Yes (New) | Yes | Public | PASS |
| Project timelines | /projects | Yes | Yes | Public | PASS (Consolidated) |
| Builder profiles | /people | Yes (New) | Yes | Public | PASS (Consolidated) |
| Collaborators | /collaborations| Yes | Yes | Auth | PASS |
| About KREVO | /about | Yes (New) | Yes | Public | PASS |
| Public discovery | /trending | Yes | Yes | Public | PASS (Consolidated) |
| GitHub login | /auth | Yes | Yes | Public | PASS |
| Builder search | /search | Yes | Yes | Public | PASS |

**Result:** FOOTER NAVIGATION COMPLETE

## 18. Final KREVO MVP Production Readiness Decision

**Date:** 2026-08-21

### Final Verdict

**Overall production beta decision: NOT READY**

KREVO/ShipSpace is **code-complete for MVP QA** across Student/User and Admin personas, but it is **not yet ready for a full production beta announcement** because several external/manual gates remain unproven in a real browser environment. No additional feature-development phase should begin before those gates are completed.

### What Is Complete

| Area | Status | Evidence |
| --- | --- | --- |
| Student/User implementation | COMPLETED | Phases 0-8 closed; project, ship, discovery, engagement, collaboration, notification, profile, ShipScore/rank, search, settings, and responsive code readiness are implemented. |
| Admin implementation | COMPLETED | Admin phases 0-7 closed; role isolation, user management, ban/unban, reports, audit logging, audit viewer, destructive-action UX, and admin responsive/accessibility code readiness are implemented. |
| Docker runtime smoke | COMPLETED | Backend health, frontend shell, public trending feed, and public search all returned HTTP 200 after Docker rebuild. |
| Frontend lint/build | COMPLETED | `npm.cmd run lint` exited 0 with existing warnings; `npm.cmd run build` completed successfully. |
| Backend compile and focused admin/security tests | COMPLETED | Maven compile passed; focused admin/security test slice passed. |

### Remaining Release Gates

| Gate | Status | Why It Remains Open | Required Closure Evidence |
| --- | --- | --- | --- |
| Real GitHub OAuth | BLOCKED | This execution did not have two real browser-authenticated GitHub sessions. | First-time login, returning login, cancel/failure handling, logout, ADMIN bootstrap, and normal USER login verified in browser. |
| Two-account Student/User journey | BLOCKED | Requires two real accounts/sessions to avoid fabricated social evidence. | USER A creates project/ship; USER B follows, likes, comments, requests collaboration; USER A receives notifications and accepts/declines; USER B sees resulting status/feed. |
| Admin browser journey | BLOCKED | Route-level evidence exists, but final browser role/access validation still needs a real Admin OAuth session. | Admin can access dashboard/users/reports/audit; normal USER is denied; ban/unban and report resolve appear in audit UI. |
| Banned-user browser journey | BLOCKED | Route-level filter tests exist, but final browser session with a banned real user was not available. | Banned user can read/logout, cannot create project/ship/like/comment/follow/upload, and sees professional blocked-write UI. |
| Multibase valid upload | BLOCKED | Runtime config has `MULTIBASE_API_URL=http://localhost:5001`, blank `MULTIBASE_API_KEY`, and `DEMO_MODE=false`; valid-upload acceptance cannot be proven without service/key. | PNG/JPEG/GIF/WebP success; invalid MIME, oversized, renamed executable, and malformed file rejection. |
| Responsive visual QA | ACCEPTED DEBT | Code-level responsive work is complete, but final screenshot/browser acceptance at all breakpoints remains manual. | 390, 768, 1024, and 1440px screenshots for public, authenticated, and admin flows without clipping, overlap, or unusable navigation. |
| Multibase policy sign-off | BLOCKED | Upload service exposure/security policy requires environment-level confirmation. | Confirm service URL/key handling, allowed origins/network exposure, upload limits, and failure UX in the intended beta environment. |

### Final Verification Log

| Command | Result |
| --- | --- |
| `docker compose up --build -d` | PASS - backend/frontend rebuilt and containers recreated. |
| `docker compose ps` | PASS - backend healthy; frontend, MongoDB, and Redis running. |
| `powershell -ExecutionPolicy Bypass -File .\ops\mvp-smoke-test.ps1 -SkipBuildChecks` | PASS - backend health HTTP 200, frontend shell HTTP 200, public trending feed HTTP 200, public search HTTP 200. |
| `cd B:\Personal\ShipSpace\web; npm.cmd run lint` | PASS - 0 errors, 27 warnings. |
| `cd B:\Personal\ShipSpace\web; npm.cmd run build` | PASS. |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q -DskipTests compile` | PASS. |
| `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; .\mvnw.cmd -q "-Dtest=AdminControllerTest,AdminSecurityRouteTest,AuditLogServiceTest,MongoIndexInitializerTest,BannedUserWriteFilterTest" test` | PASS. |

### Final Decision Note

The correct release posture is:

**Code-complete MVP QA: YES**

**Controlled production beta announcement: NOT YET**

Proceed with manual acceptance using real GitHub accounts and a configured Multibase environment. Fix only defects that directly block those gates. After the gates pass, freeze the MVP and move to controlled beta.

## 19. KREVO Public Information & Policy Pages

**Date:** 2026-08-22

### Scope

Audited and improved the public information/policy routes:

| Route | Purpose | Status |
| --- | --- | --- |
| `/about` | Customer-facing product explanation | Updated |
| `/help` | New-user workflow and troubleshooting guide | Updated |
| `/privacy` | Beta-ready factual privacy summary | Updated |
| `/terms` | Beta-ready factual terms summary | Updated |

### Existing Page Audit

| Area | Finding | Action |
| --- | --- | --- |
| About | Clear but too lightweight; did not fully explain the builder loop | Rewritten as product-facing KREVO positioning |
| Help | Draft quality and too short for a new user | Expanded into workflow-oriented help sections |
| Privacy | Draft summary only; missing token/storage, public-data, retention, provider, and legal-review caveats | Rewritten from actual implementation facts |
| Terms | Draft summary only; missing public-content, acceptable-use, moderation, ShipScore, collaboration, and beta availability language | Rewritten as cautious beta terms |
| Shared footer | Duplicate mini-footer appeared above info pages from the right panel | Removed from `RightPanel.tsx` |

### Actual Data-Practice Inventory

| Data Category | Collected/Stored? | Source | Purpose | Stored Where | Retention Known? | Public? |
| --- | --- | --- | --- | --- | --- | --- |
| GitHub numeric user ID | Yes | GitHub OAuth | Account identity and admin bootstrap | MongoDB `users.githubId` | No formal deletion policy | Admin/internal; not generic public profile |
| GitHub username/avatar/email/profile fields | Yes, as available | GitHub OAuth scopes `read:user,user:email` | Profile/account creation | MongoDB `users` | No formal deletion policy | Username/avatar/profile fields may be public |
| JWT access/refresh tokens | Yes on client | KREVO auth response | Authenticated session | Browser `localStorage` | Until cleared/expiry | No |
| OAuth state | Yes | `/auth/state` | OAuth CSRF protection | Redis | About 10 minutes | No |
| Projects and ships | Yes | User-created content | Public project timeline/feed | MongoDB `projects`, `ships` | No formal deletion policy | Yes |
| Ship media references | Yes | User upload flow | Display ship media | MongoDB + media storage provider | No formal deletion policy | Yes when attached to public ships |
| Comments, likes, follows, collaboration requests | Yes | User actions | Engagement/collaboration | MongoDB | No formal deletion policy | Comments/follow counts may be public; collaboration inbox is private |
| Notifications | Yes | Platform actions | In-app alerts | MongoDB `notifications` | 90-day TTL | Private to recipient |
| ShipScore/rank and score events | Yes | Platform scoring service | Reputation features and idempotency | MongoDB `users`, `score_event_log` | Score logs 90-day TTL | Score/rank may be public |
| Behavioral signals | Yes | Product interactions | Discovery/personalization | MongoDB `behavioral_signals` | 90-day TTL | No |
| Admin audit logs | Yes | Admin moderation actions | Accountability | MongoDB `audit_logs` | 365-day TTL | Admin-only |
| Rate-limit/token blacklist records | Yes | Auth/API security | Abuse prevention and logout/rotation | Redis | TTL based on window/token expiry | No |

### Public vs Private Data Matrix

| Public/Readable | Authenticated/Private | Admin-only |
| --- | --- | --- |
| Public profiles, projects, ships, comments, search results, trending/latest feeds, ShipScore/rank where exposed | Current session, settings, notifications, collaboration inbox/sent views, following feed state | Admin dashboard, admin user detail, reports, audit logs, role/ban controls |

### Authentication / Storage Facts

- KREVO uses GitHub OAuth as the beta authentication method.
- Frontend requests GitHub scopes `read:user,user:email`.
- KREVO issues its own JWT access and refresh tokens.
- The React app currently stores tokens in browser `localStorage`.
- The OAuth return path is stored in `sessionStorage`.
- Logout clears local tokens and calls the backend logout endpoint for token revocation.
- No evidence of email/password authentication in the current MVP.

### External Providers / Tracking Facts

- GitHub is used for OAuth identity.
- MongoDB stores product data.
- Redis stores OAuth state, token blacklist/rate-limit/cache style data.
- Media upload uses the configured media-storage service path.
- No Google Analytics, GA4, Mixpanel, Segment, PostHog, Meta Pixel, Hotjar, or advertising system was found in the codebase audit.

### Claims Avoided or Corrected

The updated pages intentionally do not claim:

- GDPR/CCPA/SOC 2/ISO/HIPAA/PCI/COPPA compliance.
- Guaranteed deletion, uptime, security, employment outcomes, or collaboration outcomes.
- Email/password login, password reset, billing/refunds, or paid subscriptions.
- GitHub password access, private repository access, contribution verification, or GitHub endorsement.
- ShipScore as an objective proof of professional skill.

### CEO Decisions Pending

| Decision | Current Evidence | Recommendation | CEO Approval Needed |
| --- | --- | --- | --- |
| Sell user data? | No advertising/sales system found | Make an explicit policy before public launch | Yes |
| Advertising? | No ad system found | Keep beta copy factual: current product has no ad system | Yes for long-term commitment |
| User-content license | Product needs permission to host/display content | Legal counsel should draft final license | Yes |
| Account deletion requests | No self-service deletion flow found | Define request process before broader launch | Yes |
| Minimum age | No finalized policy found | Legal review required | Yes |
| Governing jurisdiction | Not defined | Do not invent in product copy | Yes |
| Legal entity name | Not defined | Add only after business/legal decision | Yes |
| Privacy/support contact | Not configured in repo | Publish official channel before broad launch | Yes |
| Retention policy | Some TTLs exist, broad content retention undefined | Define formal retention/deletion policy | Yes |

### Legal Review Flags

Professional legal review is recommended before broad commercial/public launch for privacy rights by jurisdiction, children/minimum-age rules, international data handling, content licensing, liability language, governing law, deletion obligations, legal entity identity, and official contact channels.

### Verification

| Command | Result |
| --- | --- |
| `cd B:\Personal\ShipSpace\web; npm.cmd run lint` | PASS - 0 errors, existing warnings only |
| `cd B:\Personal\ShipSpace\web; npm.cmd run build` | PASS |

### Manual Gates

| Gate | Status |
| --- | --- |
| `/about`, `/help`, `/privacy`, `/terms` direct navigation | Pending browser confirmation after deploy |
| Footer links to public info pages | Pending browser confirmation after deploy |
| Mobile 390/768 and desktop 1024/1440 visual pass | Pending browser confirmation after deploy |
| Legal/professional review | Required before broad public/commercial launch |

### Decision

**PUBLIC PAGES BETA READY**
