# ShipSpace

**The Social Network for Builders. Proof of Work > Engagement.**

---

## What is ShipSpace?

ShipSpace is a platform for technical builders — indie hackers, side-project developers, and founders — who want to share their journey through "Ships" (project updates). Every Ship is pinned to a persistent Project, creating a transparent, historical log of building progress.

---

## Actual Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend API | Spring Boot 3.2 (Java 17) |
| Primary Database | MongoDB (standalone or replica set) |
| Cache | Redis (Lettuce client, connection pooling) |
| Auth | GitHub OAuth 2.0 + JWT (JJWT 0.12.3) |
| Image Storage | Multibase (content-addressed) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI, dev only) |
| Async | Spring `@Async` with bounded thread pool |
| Scheduled Jobs | Spring `@Scheduled` |

---

## Architecture Overview

```
Client
  │
  ▼
Nginx (reverse proxy)
  │
  ▼
Spring Boot API  ──── MongoDB (users, ships, projects, notifications, ...)
  │                ── Redis  (trending feed cache, rate limiting, token blacklist)
  │
  ├── Auth:         GitHub OAuth → JWT access + refresh tokens
  ├── Feed:         Following (chronological), Trending (score-based), Personalized (hybrid)
  ├── Engagement:   Like/unlike, comments, comment upvotes (atomic $inc counters)
  ├── Social:       Follow/unfollow (atomic counters)
  ├── Scoring:      ShipScore → Level (1-30) → Rank tier (DOCKED → FLEET_ADMIRAL)
  ├── Personalization: BehavioralSignal → UserInterestProfile → hybrid feed
  └── Jobs:         ScoreDecayJob (2 AM), TrendingRecalculation (15 min),
                    TimeDecayJob (midnight), CounterReconciliation (3 AM)
```

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /api/auth/github | No | GitHub OAuth login |
| POST | /api/auth/refresh | No | Refresh JWT |
| GET | /api/auth/me | Yes | Current user |
| POST | /api/auth/logout | Yes | Revoke tokens |
| GET | /api/users/{id} | No | Public profile |
| PUT | /api/users/me | Yes | Update profile |
| GET | /api/users/me/projects | Yes | My projects |
| GET | /api/users/me/activity | Yes | My activity |
| POST | /api/projects | Yes | Create project |
| GET | /api/projects/{id} | No | Get project |
| PUT | /api/projects/{id} | Yes | Update project |
| DELETE | /api/projects/{id} | Yes | Delete project + ships |
| GET | /api/projects/{id}/ships | No | Project ships |
| POST | /api/ships | Yes | Create ship |
| GET | /api/ships/{id} | No | Get ship |
| PUT | /api/ships/{id} | Yes | Update ship (10-min window) |
| DELETE | /api/ships/{id} | Yes | Delete ship |
| POST | /api/ships/{id}/images | Yes | Upload images |
| POST | /api/ships/{id}/like | Yes | Toggle like |
| POST | /api/ships/{id}/comments | Yes | Add comment |
| GET | /api/ships/{id}/comments | No | Get comments |
| DELETE | /api/ships/comments/{id} | Yes | Delete comment |
| GET | /api/feeds/following | Yes | Following feed |
| GET | /api/feeds/trending | No | Trending feed |
| GET | /api/feeds/trending/{window} | No | Trending (24h/7d/30d) |
| GET | /api/feeds/personalized | Yes | Personalized hybrid feed |
| GET | /api/notifications | Yes | Notifications |
| GET | /api/notifications/unread-count | Yes | Unread count |
| PUT | /api/notifications/{id}/read | Yes | Mark read |
| PUT | /api/notifications/read-all | Yes | Mark all read |
| GET | /api/health | No | Health check |

---

## Local Setup

### Prerequisites
- Java 17+
- Maven 3.6+
- MongoDB 6+ (standalone or replica set)
- Redis 7+

### Environment Variables

Copy `.env.example` to `.env` and fill in:

```
MONGODB_URI=mongodb://localhost:27017/shipspace
JWT_SECRET=<min 256-bit secret>
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
GITHUB_CLIENT_ID=<your GitHub OAuth app client ID>
GITHUB_CLIENT_SECRET=<your GitHub OAuth app client secret>
GITHUB_REDIRECT_URI=http://localhost:3000/auth/callback
CORS_ALLOWED_ORIGINS=http://localhost:3000
REDIS_HOST=localhost
REDIS_PORT=6379
```

### Run

```bash
mvn spring-boot:run
```

Health check: `curl http://localhost:8080/api/health`

### Docker

```bash
docker-compose up
```

---

## Production Notes

- **MongoDB transactions**: `@Transactional` requires a replica set. On standalone, annotations are silently ignored. Provision a replica set before enabling transactional guarantees.
- **Auto-index creation**: Disabled. Indexes are created on startup via `MongoConfig.initIndexes()` — idempotent and safe.
- **Rate limiting**: Auth endpoints are rate-limited per IP (10 req/min). Requires Redis.
- **Swagger UI**: Disabled by default. Enable with `SWAGGER_ENABLED=true` (dev/staging only).
- **Score decay**: Runs daily at 2 AM. Requires `@EnableScheduling` (already configured).
- **Trending recalculation**: Runs every 15 minutes. Configurable via `feed.trending.recalculation-interval-ms`.

---

## Contributors

- Venu Thota ([@SamppurnaTH](https://github.com/SamppurnaTH))
- Kumar Mannepalli ([@KumarMannepalli](https://github.com/KumarMannepalli))
