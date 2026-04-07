# ShipSpace Frontend UI Specification

> Version 1.0 — Aligned with backend API v1  
> Audience: Frontend engineering team  
> Base API URL: `/api` (all endpoints prefixed)

---

## 1. Design System Foundation

### 1.1 Token Vocabulary

```
Colors
  --color-bg-base        #0D0F14   (dark canvas)
  --color-bg-surface     #161A22   (cards, panels)
  --color-bg-elevated    #1E2330   (modals, dropdowns)
  --color-border         #2A2F3D
  --color-text-primary   #E8EAF0
  --color-text-secondary #8B91A8
  --color-text-muted     #555C72
  --color-accent         #5B8DEF   (primary CTA, links)
  --color-accent-hover   #7AA3F5
  --color-success        #3ECF8E
  --color-warning        #F5A623
  --color-danger         #E05252
  --color-rank-docked    #8B91A8
  --color-rank-launcher  #3ECF8E
  --color-rank-orbiter   #5B8DEF
  --color-rank-navigator #A78BFA
  --color-rank-commander #F5A623
  --color-rank-admiral   #E05252

Typography
  --font-sans   "Inter", system-ui, sans-serif
  --font-mono   "JetBrains Mono", monospace
  --text-xs     11px / 1.4
  --text-sm     13px / 1.5
  --text-base   15px / 1.6
  --text-lg     18px / 1.5
  --text-xl     22px / 1.4
  --text-2xl    28px / 1.3

Spacing (4px base grid)
  --space-1  4px   --space-2  8px   --space-3  12px
  --space-4  16px  --space-5  20px  --space-6  24px
  --space-8  32px  --space-10 40px  --space-12 48px

Radius
  --radius-sm  4px   --radius-md  8px
  --radius-lg  12px  --radius-xl  16px  --radius-full 9999px

Shadows
  --shadow-card   0 1px 3px rgba(0,0,0,0.4), 0 1px 2px rgba(0,0,0,0.3)
  --shadow-modal  0 20px 60px rgba(0,0,0,0.6)
```

### 1.2 Component Primitives

| Component | Variants | Notes |
|-----------|----------|-------|
| Button | primary, secondary, ghost, danger | 36px / 32px / 28px heights |
| Input | default, error, disabled | Always show character count when max exists |
| Badge | rank tier, status, count | Rank badges use tier color tokens |
| Avatar | 24px, 32px, 40px, 56px, 80px | Fallback: initials on --color-bg-elevated |
| Card | default, interactive, selected | interactive adds hover border |
| Skeleton | text, avatar, card | Used during all loading states |
| Toast | success, error, warning, info | Bottom-right, 4s auto-dismiss |
| Modal | sm (400px), md (560px), lg (720px) | Trap focus, ESC to close |
| Spinner | sm (16px), md (24px), lg (40px) | Used inline and full-screen |
| EmptyState | icon + heading + subtext + optional CTA | Centered in container |
| ErrorBanner | inline, full-page | Maps to HTTP error codes |

### 1.3 Rank Badge Component

Displays rank tier + level inline. Used throughout the app.

```
RankBadge({ rank, level })
  → <span class="rank-badge rank-badge--{rank.toLowerCase()}">
      <RankIcon rank={rank} size={14} />
      {rank} · Lv.{level}
    </span>
```

Rank → color mapping uses `--color-rank-*` tokens.  
Max level (30) shows a subtle shimmer animation.

### 1.4 System Status Banner

A persistent top-of-page banner that appears when the backend signals degraded state.  
Triggered by response headers or 503 responses.

```
Conditions → Banner text
  X-Feed-Mode: trending-fallback  → "Personalized feed is temporarily unavailable. Showing trending content."
  HTTP 503 + Retry-After          → "Server is under high load. Some features may be slower than usual."
  HTTP 429 + Retry-After: N       → "You're doing that too fast. Try again in {N}s."
```

Banner: full-width, --color-warning background, dismissible, non-blocking.

---

## 2. Application Shell & Navigation

### 2.1 Layout Structure

```
┌─────────────────────────────────────────────────────┐
│  TopNav (64px, sticky)                              │
├──────────┬──────────────────────────────────────────┤
│ SideNav  │  Main Content Area                       │
│ (240px,  │  (flex-1, max-w-3xl centered on feed)   │
│  sticky) │                                          │
│          │                                          │
└──────────┴──────────────────────────────────────────┘
```

Mobile (<768px): SideNav collapses to bottom tab bar (5 tabs).

### 2.2 TopNav

Elements (left → right):
- Logo / wordmark (links to `/feed`)
- Search input (placeholder: "Search ships, projects, builders...")
- Notification bell icon with unread badge (calls `GET /notifications/unread-count` on mount, polls every 60s)
- Avatar dropdown (links to profile, settings, logout)

Unread badge: red dot with count, hidden when count = 0, shows "99+" when > 99.

### 2.3 SideNav

```
Navigation items:
  🚀  Feed          /feed
  🔥  Trending      /trending
  📦  Projects      /projects
  👥  Following     /social/following
  🔔  Notifications /notifications
  ─────────────────
  👤  My Profile    /profile/{username}
  ⚙️  Settings      /settings
```

Active item: accent left border + accent text color.  
Collapsed state (mobile): icon only, tooltip on hover.

---

## 3. Authentication Screens

### 3.1 Landing / Login Screen

Route: `/login` (public, redirect to `/feed` if already authenticated)

Layout: centered card (480px wide) on full-screen dark background with subtle grid pattern.

```
┌─────────────────────────────────┐
│         [ShipSpace Logo]        │
│                                 │
│   Build in public. Ship often.  │
│                                 │
│  ┌───────────────────────────┐  │
│  │  Continue with GitHub  ↗  │  │  ← primary button, GitHub icon
│  └───────────────────────────┘  │
│                                 │
│  By signing in you agree to     │
│  our Terms of Service.          │
└─────────────────────────────────┘
```

States:
- Default: button enabled
- Loading: button disabled, spinner inside, text "Connecting to GitHub..."
- Error: ErrorBanner below button with message from API

OAuth Flow (frontend steps):
1. User clicks button → call `GET /auth/state` → store `state` in sessionStorage
2. Redirect to `https://github.com/login/oauth/authorize?client_id={CLIENT_ID}&state={state}&scope=read:user,user:email`
3. GitHub redirects to `/auth/callback?code={code}&state={state}`
4. Validate `state` matches sessionStorage value (CSRF check)
5. Call `POST /auth/github` with `{ code, state }`
6. On success: store tokens (see §3.3), redirect to `/feed`
7. On failure: redirect to `/login?error=auth_failed`

Error states on `/login`:
- `?error=auth_failed` → "Authentication failed. Please try again."
- `?error=rate_limited` → "Too many attempts. Please wait 60 seconds."
- `?error=state_mismatch` → "Security check failed. Please try again."

### 3.2 OAuth Callback Screen

Route: `/auth/callback`

Full-screen loading state while processing:
```
[Spinner]
Signing you in...
```

No user interaction. Handles the code exchange automatically.  
On any error, redirects to `/login?error={type}`.

### 3.3 Token Storage Strategy

```
accessToken   → memory only (module-level variable, NOT localStorage)
refreshToken  → localStorage key "ss_rt" (acceptable tradeoff for UX)
userId        → localStorage key "ss_uid"
username      → localStorage key "ss_username"
```

On app boot:
1. Check if `accessToken` in memory → if yes, proceed
2. Else check `ss_rt` in localStorage → if yes, call `POST /auth/refresh`
3. On refresh success: store new tokens, proceed
4. On refresh failure (401/403): clear localStorage, redirect to `/login`

Axios interceptor pattern:
- Attach `Authorization: Bearer {accessToken}` to every request
- On 401 response: attempt one silent refresh, retry original request
- On second 401: logout and redirect to `/login`

### 3.4 Session Expiry Handling

When a 401 is received mid-session:
- Show a non-blocking toast: "Your session expired. Signing you back in..."
- Attempt silent token refresh in background
- If refresh succeeds: retry the failed request transparently
- If refresh fails: redirect to `/login` with current path as `?redirect=/current/path`

After login, redirect back to the saved path.

### 3.5 Rate Limit Handling (Auth)

On `POST /auth/github` or `POST /auth/refresh` returning 429:
- Read `Retry-After` header (seconds)
- Show countdown: "Too many attempts. Try again in {N}s"
- Disable the button for the duration of the countdown
- Re-enable automatically when countdown reaches 0

---

## 4. Feed Screens

### 4.1 Feed Layout (Shared)

All feed screens share this layout:

```
┌─────────────────────────────────────────────────────┐
│  Feed Tab Bar                                       │
│  [Following]  [Trending ▾]  [Personalized]         │
├─────────────────────────────────────────────────────┤
│  [System Status Banner — conditional]               │
├─────────────────────────────────────────────────────┤
│  FeedItem                                           │
│  FeedItem                                           │
│  FeedItem                                           │
│  [Load More button / infinite scroll trigger]       │
└─────────────────────────────────────────────────────┘
```

Trending tab has a dropdown: All Time / 24h / 7d / 30d  
→ maps to `GET /feeds/trending`, `/feeds/trending/24h`, etc.

### 4.2 FeedItem Card

```
┌──────────────────────────────────────────────────────┐
│  [Avatar 40px]  @username  [RankBadge]  · 2h ago    │
│                 Project: {project.name}  →           │
├──────────────────────────────────────────────────────┤
│  {ship.content}                                      │
│  (truncated at 300 chars with "Show more" toggle)    │
│                                                      │
│  [Image grid — max 4, 2x2 layout]                   │
│                                                      │
│  [ExternalLink chip] [ExternalLink chip]             │
├──────────────────────────────────────────────────────┤
│  ♥ {likeCount}   💬 {commentCount}   [Edit] [···]   │
└──────────────────────────────────────────────────────┘
```

Data source: `FeedItemResponse { ship, project, user }`

Interactions:
- Avatar / username → `/profile/{username}`
- Project name → `/projects/{project.id}`
- ♥ button → `PUT /ships/{shipId}/like` (toggle), optimistic update
- 💬 button → expand inline comment thread
- Edit button → visible only if `ship.editWindowOpen === true` AND current user is owner
- ··· menu → Delete (owner only), Report (others)

Like button states:
- Default: outline heart, muted color
- Liked: filled heart, --color-danger
- Loading: spinner replaces icon, button disabled
- Optimistic: update count immediately, revert on error

Edit window indicator:
- When `editWindowOpen === true`: show countdown chip "Edit closes in {MM:SS}"
- Countdown derived from `ship.editWindowExpires` (ISO timestamp)
- When window closes: chip disappears, Edit button hides

### 4.3 Following Feed

Route: `/feed`  
Endpoint: `GET /feeds/following?page={n}&size=20`  
Auth: required

Empty state (user follows nobody):
```
[Icon: satellite dish]
Your feed is empty
Follow some builders to see their ships here.
[Browse Trending →]
```

### 4.4 Trending Feed

Route: `/trending`  
Endpoint: `GET /feeds/trending` (or with time window)  
Auth: not required (public)

Time window selector renders as pill tabs below the main tab bar.  
Default: All Time.

### 4.5 Personalized Feed

Route: `/feed/personalized`  
Endpoint: `GET /feeds/personalized`  
Auth: required

Check response header `X-Feed-Mode`:
- `personalized` → no banner
- `trending-fallback` → show System Status Banner: "Personalized feed is temporarily unavailable. Showing trending content."

Cold-start state (new user, <10 behavioral signals):
- Same as trending-fallback banner but with text: "We're learning your preferences. Showing trending content for now."

### 4.6 Feed Loading States

Initial load:
- Show 5 FeedItem skeleton cards (avatar circle + 3 text lines + action bar)

Pagination (load more):
- Infinite scroll: trigger `page + 1` fetch when last item is 80% visible
- Show 3 skeleton cards at bottom while loading
- "You've reached the end" message when `page.last === true`

Error state:
- Show ErrorBanner: "Couldn't load feed. [Retry]"
- Retry button re-fetches current page

---

## 5. Ship Screens

### 5.1 Ship Detail Screen

Route: `/ships/{shipId}`

```
┌──────────────────────────────────────────────────────┐
│  ← Back                                              │
├──────────────────────────────────────────────────────┤
│  [Avatar 56px]  @username  [RankBadge]               │
│  Project: {project.name}  →  {createdAt relative}   │
├──────────────────────────────────────────────────────┤
│  {ship.content — full, no truncation}                │
│                                                      │
│  [Image gallery — lightbox on click]                 │
│                                                      │
│  [ExternalLink chips]                                │
├──────────────────────────────────────────────────────┤
│  ♥ {likeCount}  💬 {commentCount}                   │
│  [Edit Ship]  (visible if owner + editWindowOpen)    │
│  [Edit closes in MM:SS]  (countdown chip)            │
├──────────────────────────────────────────────────────┤
│  COMMENTS ({commentCount})                           │
│  ┌────────────────────────────────────────────────┐  │
│  │ [Avatar 32px]  @commenter  · 1h ago            │  │
│  │ {comment.content}                              │  │
│  │ ▲ {upvotes}  [Delete — owner only]             │  │
│  └────────────────────────────────────────────────┘  │
│  [Load more comments]                                │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │ [Avatar 32px]  Write a comment...              │  │
│  │                              {len}/1000  [Post]│  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

Comment upvote:
- `PUT /ships/comments/{commentId}/upvote` (toggle)
- Response `qualifiedForScoring: true` → show brief toast: "+1 ShipScore for quality comment"
- Optimistic update on upvote count

### 5.2 Create Ship Screen

Route: `/ships/new` (modal or dedicated page — recommend modal)  
Auth: required

```
┌──────────────────────────────────────────────────────┐
│  New Ship                                    [✕]     │
├──────────────────────────────────────────────────────┤
│  Project *                                           │
│  [Dropdown — user's projects]                        │
│                                                      │
│  What did you ship?  *                               │
│  ┌────────────────────────────────────────────────┐  │
│  │                                                │  │
│  │                                                │  │
│  │                                    0 / 5000   │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  External Links  (optional)                          │
│  [+ Add link]  → url input + type select             │
│                                                      │
│  Images  (optional, upload after creation)           │
│  [📎 Attach images]                                  │
│                                                      │
│  [Cancel]                    [Ship It →]             │
└──────────────────────────────────────────────────────┘
```

Flow:
1. `POST /ships` with `{ projectId, content, externalLinks }`
2. On success: if images selected, immediately call `POST /ships/{id}/images` (multipart)
3. Show edit window countdown from `editWindowExpires`
4. Redirect to ship detail or close modal and refresh feed

Validation (client-side before submit):
- Project: required
- Content: 1–5000 chars, show live counter
- External links: valid URL format

### 5.3 Edit Ship Screen

Route: `/ships/{shipId}/edit` (modal)  
Auth: required, owner only, `editWindowOpen` must be true

Same layout as Create Ship but pre-populated.  
Prominent countdown banner at top: "You have MM:SS to edit this ship."  
When countdown hits 0: disable all inputs, show "Edit window closed. This ship is now permanent."

Image upload section shows existing images with delete option.  
New images can be added while window is open.

`PUT /ships/{shipId}` on save.  
`POST /ships/{shipId}/images` for new images.

### 5.4 Delete Ship Confirmation

Triggered from ··· menu on FeedItem or Ship Detail.

Modal (sm):
```
Delete this ship?
This action cannot be undone. The ship and all its engagement data will be permanently removed.
[Cancel]  [Delete]
```

`DELETE /ships/{shipId}` on confirm.  
On success: remove from feed (optimistic), show success toast.

---

## 6. Project Screens

### 6.1 Project List Screen

Route: `/projects` (current user's projects)  
Endpoint: `GET /users/me/projects`  
Auth: required

```
┌──────────────────────────────────────────────────────┐
│  My Projects                    [+ New Project]      │
├──────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────┐  │
│  │  {project.name}                                │  │
│  │  {project.description — truncated 120 chars}   │  │
│  │  [React] [Node.js] [MongoDB]  ← tech tag chips │  │
│  │  {shipCount} ships  ·  Last ship {relative}    │  │
│  └────────────────────────────────────────────────┘  │
│  (repeat for each project)                           │
└──────────────────────────────────────────────────────┘
```

Empty state:
```
[Icon: rocket]
No projects yet
Start by creating your first project.
[Create Project →]
```

### 6.2 Project Detail Screen

Route: `/projects/{projectId}`  
Endpoints: `GET /projects/{projectId}`, `GET /projects/{projectId}/ships`

```
┌──────────────────────────────────────────────────────┐
│  ← Back                                              │
├──────────────────────────────────────────────────────┤
│  {project.name}                                      │
│  by @{owner.username}  [RankBadge]                   │
│                                                      │
│  {project.description}                               │
│                                                      │
│  [React] [Node.js] [MongoDB]  ← tech tag chips       │
│                                                      │
│  [🔗 Website] [🔗 GitHub]  ← external link chips    │
│                                                      │
│  {shipCount} ships  ·  Created {date}                │
│                                                      │
│  [Edit Project]  (owner only)                        │
│  [Request to Collaborate]  (non-owner, auth required)│
├──────────────────────────────────────────────────────┤
│  SHIP HISTORY  (reverse chronological)               │
│  ┌────────────────────────────────────────────────┐  │
│  │  [ShipItem — compact version of FeedItem]      │  │
│  └────────────────────────────────────────────────┘  │
│  [Load more]                                         │
└──────────────────────────────────────────────────────┘
```

"Request to Collaborate" button:
- Disabled if user already sent a pending request (check via `GET /collaborations/sent`)
- On click: opens Collaboration Request Modal (§9.2)

### 6.3 Create / Edit Project Modal

Route: modal overlay (no dedicated route)

```
┌──────────────────────────────────────────────────────┐
│  New Project                                 [✕]     │
├──────────────────────────────────────────────────────┤
│  Project Name *                                      │
│  [________________________]  0/100                   │
│                                                      │
│  Description *                                       │
│  [________________________]                          │
│  [________________________]  0/2000                  │
│                                                      │
│  Tech Tags  (comma-separated or tag input)           │
│  [React ✕] [Node.js ✕] [+ Add tag]                  │
│                                                      │
│  External Links  (optional)                          │
│  [+ Add link]                                        │
│                                                      │
│  [Cancel]                    [Create Project]        │
└──────────────────────────────────────────────────────┘
```

Edit mode: pre-populated, button text "Save Changes".  
`POST /projects` on create, `PUT /projects/{id}` on edit.

### 6.4 Delete Project Confirmation

Modal (sm):
```
Delete "{project.name}"?
This will permanently delete the project and all {shipCount} ships. This cannot be undone.
[Cancel]  [Delete Project]
```

`DELETE /projects/{projectId}` on confirm.

---

## 7. User Profile Screens

### 7.1 Public Profile Screen

Route: `/profile/{username}`  
Endpoints: `GET /users/{userId}`, `GET /v1/users/{userId}/rank-summary`

```
┌──────────────────────────────────────────────────────┐
│  [Avatar 80px]                                       │
│  @{username}                                         │
│  [RankBadge — large variant]                         │
│                                                      │
│  {bio}                                               │
│  📍 {location}   🔗 {websiteUrl}                    │
│                                                      │
│  ┌──────────┬──────────┬──────────┐                  │
│  │ Followers│ Following│ Projects │                  │
│  │   {n}    │   {n}    │   {n}    │                  │
│  └──────────┴──────────┴──────────┘                  │
│                                                      │
│  [Follow]  /  [Following ✓]  (auth required)         │
│  [Edit Profile]  (own profile only)                  │
├──────────────────────────────────────────────────────┤
│  RANK PROGRESSION                                    │
│  Level {level} · {rank}                              │
│  ████████████░░░░  {shipScore} / {nextLevelScore} XP │
│  {pointsToNext} points to Level {level+1}            │
├──────────────────────────────────────────────────────┤
│  Tab: [Ships]  [Projects]                            │
│  ─────────────────────────────────────────────────   │
│  (ship list or project list based on active tab)     │
└──────────────────────────────────────────────────────┘
```

Rank progression bar:
- Width: `(shipScore % 40) / 40 * 100%` (progress within current level)
- Color: `--color-rank-{tier}`
- At max level (30): full bar, "Max Level" label, shimmer animation

Follow button:
- Default: "Follow" (primary button)
- Following: "Following ✓" (secondary button, hover shows "Unfollow")
- Loading: spinner, disabled
- Own profile: hidden, replaced by "Edit Profile"

### 7.2 Edit Profile Screen

Route: `/settings/profile`  
Auth: required

```
┌──────────────────────────────────────────────────────┐
│  Edit Profile                                        │
├──────────────────────────────────────────────────────┤
│  Avatar                                              │
│  [Avatar 80px]  (read-only, synced from GitHub)      │
│                                                      │
│  Username  (read-only, set from GitHub)              │
│  @{username}                                         │
│                                                      │
│  Bio                                                 │
│  [________________________]  0/500                   │
│                                                      │
│  Location                                            │
│  [________________________]  0/100                   │
│                                                      │
│  Website                                             │
│  [https://...]                                       │
│                                                      │
│  [Save Changes]                                      │
└──────────────────────────────────────────────────────┘
```

`PUT /users/me` on save.  
Show success toast on save. Show inline field errors on validation failure.

### 7.3 Followers / Following Lists

Route: `/profile/{username}/followers`, `/profile/{username}/following`  
Endpoints: `GET /social/followers`, `GET /social/following`

```
┌──────────────────────────────────────────────────────┐
│  ← @{username}                                       │
│  [Followers]  [Following]  ← tab switcher            │
├──────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────┐  │
│  │ [Avatar 40px]  @username  [RankBadge]          │  │
│  │ {bio — truncated 80 chars}                     │  │
│  │                              [Follow / Unfollow]│  │
│  └────────────────────────────────────────────────┘  │
│  (paginated list)                                    │
└──────────────────────────────────────────────────────┘
```

---

## 8. Notification Screen

Route: `/notifications`  
Endpoints: `GET /notifications`, `GET /notifications/unread-count`, `PUT /notifications/{id}/read`, `PUT /notifications/read-all`  
Auth: required

```
┌──────────────────────────────────────────────────────┐
│  Notifications                  [Mark all read]      │
├──────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────┐  │
│  │ [Avatar 40px]  {notification.text}             │  │  ← unread: left accent border
│  │                                    {relative}  │  │
│  └────────────────────────────────────────────────┘  │
│  (paginated, reverse chronological)                  │
└──────────────────────────────────────────────────────┘
```

Notification types → icons:
- `SHIP_LIKE` → ♥ icon
- `SHIP_COMMENT` → 💬 icon
- `NEW_FOLLOWER` → 👤 icon
- `MENTION` → @ icon

Unread notifications: slightly brighter background + left accent border.  
Clicking a notification:
1. Call `PUT /notifications/{id}/read`
2. Navigate to `notification.targetView` (e.g., `/ships/{shipId}`)

"Mark all read" button:
- Calls `PUT /notifications/read-all`
- Optimistically clears all unread indicators
- Updates TopNav bell badge to 0

Empty state:
```
[Icon: bell]
No notifications yet
When someone likes or comments on your ships, you'll see it here.
```

Polling strategy:
- `GET /notifications/unread-count` every 60 seconds (TopNav badge)
- Full notification list only fetched when user opens `/notifications`
- No WebSocket required (polling is sufficient given async dispatch)

---

## 9. Collaboration Screens

### 9.1 Collaboration Dashboard

Route: `/collaborations`  
Auth: required

```
┌──────────────────────────────────────────────────────┐
│  Collaborations                                      │
│  [Incoming ({count})]  [Sent]  ← tab switcher        │
├──────────────────────────────────────────────────────┤
│  INCOMING TAB                                        │
│  ┌────────────────────────────────────────────────┐  │
│  │ [Avatar 40px]  @{requester.username}           │  │
│  │ wants to collaborate on {project.name}         │  │
│  │ "{message}"                                    │  │
│  │ {relative time}                                │  │
│  │ [Accept]  [Decline]                            │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  SENT TAB                                            │
│  ┌────────────────────────────────────────────────┐  │
│  │ [Avatar 40px]  {project.name}                  │  │
│  │ by @{owner.username}                           │  │
│  │ Status: [PENDING / ACCEPTED / DECLINED]        │  │
│  │ {relative time}                                │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

Status badge colors:
- PENDING → --color-warning
- ACCEPTED → --color-success
- DECLINED → --color-text-muted

Accept/Decline actions:
- `POST /collaborations/{id}/accept` or `/decline`
- Optimistic: move item to resolved state immediately
- On accept: show toast "Collaboration accepted. {username} has been notified."
- On decline: show toast "Request declined."

### 9.2 Collaboration Request Modal

Triggered from Project Detail screen.

```
┌──────────────────────────────────────────────────────┐
│  Request to Collaborate                      [✕]     │
├──────────────────────────────────────────────────────┤
│  Project: {project.name}                             │
│  Owner: @{owner.username}                            │
│                                                      │
│  Message  (optional, max 500 chars)                  │
│  ┌────────────────────────────────────────────────┐  │
│  │ Tell them why you'd like to collaborate...     │  │
│  │                                      0/500    │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  [Cancel]              [Send Request]                │
└──────────────────────────────────────────────────────┘
```

`POST /collaborations` with `{ projectId, message }`.  
On success: close modal, show toast "Request sent to @{owner.username}."  
If duplicate (idempotent 200): show toast "You already have a pending request for this project."

---

## 10. Rank & Progression Screen

Route: `/profile/{username}/rank` (or inline on profile)  
Endpoint: `GET /v1/users/{userId}/rank-summary`

```
┌──────────────────────────────────────────────────────┐
│  Rank Progression                                    │
├──────────────────────────────────────────────────────┤
│  [Large rank icon]                                   │
│  {rank}  ·  Level {level}                            │
│                                                      │
│  ████████████████░░░░░░░░  {shipScore} XP            │
│  {pointsToNext} XP to Level {level+1}                │
│  (or "Maximum Level Reached" if level = 30)          │
├──────────────────────────────────────────────────────┤
│  RANK TIERS                                          │
│  ● DOCKED        Lv. 1–5    (current if applicable)  │
│  ● LAUNCHER      Lv. 6–10                            │
│  ● ORBITER       Lv. 11–15                           │
│  ● NAVIGATOR     Lv. 16–20                           │
│  ● COMMANDER     Lv. 21–25                           │
│  ● FLEET ADMIRAL Lv. 26–30                           │
├──────────────────────────────────────────────────────┤
│  HOW TO EARN SHIPSCORE                               │
│  Ship a project update    → points                   │
│  Receive a like           → points                   │
│  Quality comment (3+ ↑)   → points                   │
│  Collaboration accepted   → points                   │
└──────────────────────────────────────────────────────┘
```

Progress bar calculation:
- `progress = (shipScore % 40) / 40`
- At level 30: `progress = 1.0`, full bar, shimmer animation

Rank tier list: current tier highlighted with accent border + rank color.  
Locked tiers: muted color.

---

## 11. Error & System State Screens

### 11.1 HTTP Error Pages

| Code | Screen | Message |
|------|--------|---------|
| 401 | Redirect to `/login` | — |
| 403 | Inline ErrorBanner | "You don't have permission to do that." |
| 404 | Full-page | "This page doesn't exist." + [Go Home] |
| 429 | Inline ErrorBanner | "Slow down. Try again in {Retry-After}s." |
| 503 | Inline ErrorBanner | "Server is under high load. Retrying in {Retry-After}s..." |
| 5xx | Inline ErrorBanner | "Something went wrong on our end. [Retry]" |

### 11.2 503 Auto-Retry Behavior

When a non-critical request (feed, profile) returns 503:
1. Read `Retry-After` header (default: 5s if absent)
2. Show System Status Banner with countdown
3. Auto-retry after countdown
4. Max 3 retries, then show manual [Retry] button
5. Critical requests (auth, logout): never auto-retry, show error immediately

### 11.3 Rate Limit (429) Handling

For any 429 response:
- Read `X-RateLimit-Reset` header (Unix timestamp)
- Calculate seconds remaining: `reset - Date.now()/1000`
- Show inline countdown on the triggering UI element (button, input)
- Disable the element for the duration
- Re-enable automatically

### 11.4 Offline / Network Error

When `fetch` throws a network error (no response):
- Show persistent banner: "You appear to be offline. Changes will not be saved."
- Disable all write actions (create, edit, like, comment)
- Read-only actions (view feed, profile) show cached data if available
- Banner auto-dismisses when connectivity is restored

### 11.5 Warm Start / Cold Start State

On app boot, if `GET /auth/me` returns slowly (>2s):
- Show full-screen loading state: [Logo] + [Spinner]
- Do not flash login screen before auth check completes

---

## 12. Search Screen

Route: `/search?q={query}`

```
┌──────────────────────────────────────────────────────┐
│  [Search input — full width, auto-focused]           │
├──────────────────────────────────────────────────────┤
│  [Ships]  [Projects]  [Builders]  ← result tabs      │
├──────────────────────────────────────────────────────┤
│  (results list — same card components as feed)       │
└──────────────────────────────────────────────────────┘
```

Note: The backend does not expose a dedicated search endpoint in the current API surface. The frontend team should confirm with the backend team whether full-text search is planned. In the interim, the search UI can be scoped to client-side filtering of loaded feed/project data, or this screen can be deferred.

---

## 13. Settings Screen

Route: `/settings`  
Auth: required

Sections:
- Profile (links to `/settings/profile` — §7.2)
- Account: shows GitHub username, email (read-only, from OAuth)
- Danger Zone: "Delete Account" (out of scope for v1, placeholder only)

---

## 14. User Flows Summary

### 14.1 New User Onboarding

```
/login → GitHub OAuth → /auth/callback → /feed (empty)
  → System suggests: "Create your first project" banner
  → /projects/new modal → create project
  → /ships/new modal → create first ship
  → Feed populates
```

### 14.2 Returning User Session

```
App boot → check localStorage ss_rt
  → POST /auth/refresh (silent)
  → success: load /feed
  → failure: redirect /login
```

### 14.3 Ship Lifecycle

```
/ships/new → POST /ships → ship created
  → edit window open (10 min countdown)
  → optional: POST /ships/{id}/images
  → countdown expires → ship locked
  → ship appears in followers' feeds
```

### 14.4 Collaboration Flow

```
/projects/{id} → "Request to Collaborate"
  → POST /collaborations
  → owner receives notification
  → owner: /collaborations → Accept
  → POST /collaborations/{id}/accept
  → requester receives notification + ShipScore
```

### 14.5 Feed Degradation Flow

```
Backend under load:
  GET /feeds/personalized → X-Feed-Mode: trending-fallback
  → System Status Banner shown
  → Feed still renders (trending data)
  → Banner auto-dismisses when header returns "personalized"
```

---

## 15. API Integration Reference

### 15.1 HTTP Client Configuration

```typescript
// Base config
const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

// Request interceptor: attach token
api.interceptors.request.use(config => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

// Response interceptor: handle 401 with silent refresh
api.interceptors.response.use(
  res => res,
  async err => {
    if (err.response?.status === 401 && !err.config._retry) {
      err.config._retry = true
      await refreshTokens()  // updates accessToken in memory
      return api(err.config)
    }
    return Promise.reject(err)
  }
)
```

### 15.2 Rate Limit Header Utilities

```typescript
function getRateLimitInfo(response: AxiosResponse) {
  return {
    limit: parseInt(response.headers['x-ratelimit-limit'] ?? '0'),
    remaining: parseInt(response.headers['x-ratelimit-remaining'] ?? '0'),
    resetAt: parseInt(response.headers['x-ratelimit-reset'] ?? '0'),
    retryAfter: parseInt(response.headers['retry-after'] ?? '0')
  }
}
```

### 15.3 Pagination Utility

All paginated endpoints return Spring `Page<T>`:
```typescript
interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number       // current page (0-indexed)
  size: number
  last: boolean
  first: boolean
}
```

Infinite scroll: fetch `page + 1` when `!page.last`.

### 15.4 Feed Mode Header

```typescript
function getFeedMode(response: AxiosResponse): 'personalized' | 'trending-fallback' {
  return response.headers['x-feed-mode'] ?? 'personalized'
}
```

### 15.5 Edit Window Countdown

```typescript
function useEditWindowCountdown(editWindowExpires: string | null) {
  // Returns { open: boolean, secondsRemaining: number }
  // Polls every second, sets open=false when secondsRemaining <= 0
  const expires = editWindowExpires ? new Date(editWindowExpires).getTime() : 0
  const remaining = Math.max(0, Math.floor((expires - Date.now()) / 1000))
  return { open: remaining > 0, secondsRemaining: remaining }
}
```

---

## 16. Recommended Tech Stack

| Concern | Recommendation | Rationale |
|---------|---------------|-----------|
| Framework | React 18 + TypeScript | Ecosystem, type safety |
| Routing | React Router v6 | File-based or config routing |
| State | Zustand (global) + React Query (server) | React Query handles caching, pagination, refetch |
| HTTP | Axios | Interceptor support for token refresh |
| Styling | Tailwind CSS + CSS variables | Design token integration |
| Forms | React Hook Form + Zod | Validation aligned with backend constraints |
| Dates | date-fns | Lightweight, tree-shakeable |
| Images | Native `<img>` with lazy loading | Multibase URLs are standard HTTPS |
| Build | Vite | Fast HMR, ESM-native |

React Query is particularly well-suited here: it handles the feed pagination, background refetch, stale-while-revalidate for trending data, and automatic retry with exponential backoff for 503 responses.
