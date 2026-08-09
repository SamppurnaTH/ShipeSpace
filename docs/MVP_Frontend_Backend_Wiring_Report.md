# ShipSpace MVP Frontend-Backend Wiring Report

Date: 2026-08-09

## Decision

**USER PERSONA MVP UI READY**

The backend MVP remains frozen as previously certified. This pass completes the normal USER persona frontend surface without reopening backend stabilization work.

The active user-facing frontend no longer exposes unsupported credential login, debug routes, stale landing fragments, emoji-based UI, or "coming soon" destinations for core MVP navigation. User flows now either use a real backend-supported action, route to an existing MVP page, or show a blocked-write state when the backend contract requires it.

Admin persona design is intentionally not included in this pass.

## Frontend Route Inventory

| Route | Screen | Access | Backend dependency | MVP status |
| --- | --- | --- | --- | --- |
| `/` | `LandingPage` / `PublicHomePage` | Public or authenticated | `GET /feeds/trending`, GitHub OAuth start, authenticated home data | Ready |
| `/auth` | `AuthPage` | Public | GitHub OAuth start | Ready, GitHub-only |
| `/signup` | `AuthPage` | Public | GitHub OAuth start | Ready, GitHub-only |
| `/auth/callback` | `AuthCallback` | Public callback | `POST /auth/github` | Ready |
| `/feed` | `AuthenticatedHomePage` | Authenticated | `GET /users/me/projects`, feed APIs, ship delete | Ready |
| `/feed/personalized` | `FeedPage` | Authenticated | `GET /feeds/personalized` with fallback behavior | Ready |
| `/trending` | `FeedPage` | Public | `GET /feeds/trending`, `GET /feeds/trending/{window}` | Ready |
| `/search` | `SearchPage` | Public read, auth write likes | `GET /search`, engagement APIs | Ready |
| `/ships/new` | `CreateShipModal` route | Authenticated non-banned | `GET /users/me/projects`, `POST /ships`, `POST /ships/{id}/images` | Ready |
| `/ships/:id` | `ShipDetailPage` | Public read, auth write | `GET /ships/{id}`, comments, likes, delete | Ready |
| `/content/:id` | Redirect | Public | canonical `/ships/:id` | Ready |
| `/profile/:id` | `ProfilePage` | Public read, auth write follow | `GET /users/{id}`, rank summary, user ships, follow APIs | Ready |
| `/projects` | `ProjectListPage` | Authenticated non-banned writes | `GET /users/me/projects`, project create/update | Ready |
| `/projects/:id` | `ProjectDetailPage` | Public read, auth write like | `GET /projects/{id}`, `GET /projects/{id}/ships` | Ready |
| `/notifications` | `NotificationsPage` | Authenticated | notification APIs | Ready |
| `/collaborations` | `CollaborationsPage` | Authenticated non-banned writes | collaboration APIs | Ready |
| `/settings` | `SettingsPage` | Authenticated | `GET /auth/me`, `PUT /users/me` | Ready |

## Backend API Capability Inventory

| Capability | Backend route(s) | Frontend usage | Notes |
| --- | --- | --- | --- |
| GitHub OAuth state/login | `GET /auth/state`, `POST /auth/github` | Landing, top nav, auth page, callback | Supported MVP auth path |
| Token refresh/logout/current user | `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me` | API client, auth context, settings | Supported |
| Public feed | `GET /feeds/trending`, `GET /feeds/trending/{window}` | landing, trending, right rail | Supported public read |
| Personalized/following feed | `GET /feeds/personalized`, `GET /feeds/following` | feed/home | Supported authenticated read with fallback |
| Search users/ships | `GET /search?q=&size=` | search, nav links | Supported |
| User profile/read | `GET /users/{userId}`, `GET /users/{userId}/ships` | profile pages | Supported public read |
| Profile update | `PUT /users/me` | settings | Supported auth write |
| Projects | `POST /projects`, `GET /projects/{id}`, `PUT /projects/{id}`, `DELETE /projects/{id}`, `GET /users/me/projects` | projects and modals | Supported |
| Ships | `POST /ships`, `GET /ships/{id}`, `PUT /ships/{id}`, `DELETE /ships/{id}`, image upload | create/detail/feed/cards | Supported |
| Engagement | like/unlike/comment/upvote/delete comment | cards/detail/comment UI | Supported |
| Social follow | follow/unfollow/followers/following | profile | Supported |
| Notifications | notifications/read/unread count | notifications/top nav | Supported |
| Collaborations | request/incoming/sent/accept/decline | collaborations page | Supported |
| Rank summary | `GET /v1/users/{id}/rank-summary` | profile | Supported |
| Admin | `/admin/*` | No normal USER UI | Correctly excluded from this pass |

## User Persona Coverage

| Flow | Status |
| --- | --- |
| Public landing | Ready with builder-focused copy and real GitHub CTA |
| Sign in / sign up | Ready, GitHub-only to match frozen backend |
| Public trending ships | Ready |
| Public search/discovery | Ready |
| Authenticated home/feed | Ready |
| Create project | Ready for non-banned users |
| Publish ship | Ready for non-banned users |
| Like/comment/follow | Ready for authenticated non-banned users |
| Profile and ShipScore | Ready |
| Project list/detail | Ready |
| Notifications | Ready |
| Collaborations | Ready |
| Settings | Ready |
| Banned-user write blocking | Ready on visible USER write surfaces |
| Emoji-free UI | Ready in active `web/src` source |
| Stale debug/coming-soon pages | Removed from active routes |

## UX State Coverage

| State | Coverage |
| --- | --- |
| Loading | Feed, profile, project, ship detail, create flows, landing preview |
| Empty | Feed, projects, profile ships, project ships, collaboration inbox/sent, landing preview |
| Error | Feed, landing preview, auth callback, ship/project/profile/search paths |
| Unauthenticated | Public read routes remain available; write routes redirect or prompt auth |
| Authenticated | Home/feed/projects/settings/notifications/collaborations wired |
| Banned user | Write CTAs hidden, blocked, or guarded while public reads/logout remain available |
| API unavailable | Runtime smoke confirms frontend shell and public API reads; visible fallbacks exist in core surfaces |

## Verification

Latest verification after the USER persona completion pass:

- `npm.cmd run lint`: passed, 0 errors, 26 warnings.
- `npm.cmd run build`: passed.
- `docker compose up -d --build frontend`: completed successfully.
- `powershell -ExecutionPolicy Bypass -File .\ops\mvp-smoke-test.ps1 -SkipBuildChecks`: passed.

Smoke result:

- Backend health: HTTP 200.
- Frontend shell: HTTP 200.
- Public trending feed: HTTP 200.
- Public search: HTTP 200.

## External Acceptance Gates

These are not UI implementation debt, but they still require real configured accounts and files:

1. Complete real GitHub OAuth login in browser.
2. Confirm normal GitHub user receives USER only.
3. Confirm `ADMIN_GITHUB_IDS` user receives ADMIN and can access `/admin/**`.
4. Ban a user and verify reads/logout work while create/like/comment/follow/upload are blocked.
5. Upload valid PNG, JPEG, GIF, and WebP files.
6. Verify oversized, invalid MIME, and renamed executable uploads are rejected cleanly.
7. Confirm Multibase delivery policy does not expose private data unintentionally.

## Next Persona

The next planned design and implementation track is the ADMIN persona: admin navigation, user moderation, ban/unban workflows, admin-only route protection, audit-friendly empty/error states, and production-grade admin UX.
