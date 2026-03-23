# ShipSpace Full-Stack Integration Summary

## Backend API Contract

All endpoints use `/api` prefix (via `server.servlet.context-path`). Controllers map under this:

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST   | /api/auth/github | GitHub OAuth login |
| POST   | /api/auth/refresh | Refresh JWT |
| GET    | /api/auth/me | Current user |
| GET    | /api/users/{userId} | User profile |
| PUT    | /api/users/me | Update profile |
| GET    | /api/users/me/projects | My projects |
| GET    | /api/users/me/activity | My activity |
| POST   | /api/projects | Create project |
| GET    | /api/projects/{id} | Get project |
| PUT    | /api/projects/{id} | Update project |
| GET    | /api/projects/{id}/ships | Project ships |
| POST   | /api/ships | Create ship |
| GET    | /api/ships/{id} | Get ship |
| PUT    | /api/ships/{id} | Update ship |
| POST   | /api/ships/{id}/images | Upload images |
| POST   | /api/ships/{id}/like | Toggle like |
| POST   | /api/ships/{id}/comments | Add comment |
| GET    | /api/ships/{id}/comments | Get comments |
| GET    | /api/feeds/following | Following feed |
| GET    | /api/feeds/trending | Trending feed |
| GET    | /api/notifications | Notifications |
| GET    | /api/notifications/unread-count | Unread count |
| PUT    | /api/notifications/{id}/read | Mark read |
| PUT    | /api/notifications/read-all | Mark all read |
| GET    | /api/health | Health check |

## DTOs & Entity Mapping

- **CurrentUserResponse** – Auth /me and PUT users/me (no raw `githubId`)
- **UserProfileResponse** – Public profiles
- **ProjectResponse** / **ShipResponse** – No entities exposed
- **UserActivityResponse.recentShips** – `List<ShipResponse>` (not `Ship`)
- **DtoMapper** – Central mapping for Ship → ShipResponse, Project → ProjectResponse

## Error Response Format

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Invalid input",
  "timestamp": "...",
  "path": "...",
  "details": { "field": "error" }
}
```

## Environment Variables

### Backend (.env or application.yml)
- `MONGODB_URI`
- `JWT_SECRET`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI`
- `CORS_ALLOWED_ORIGINS` (e.g. http://localhost:3000,http://localhost:5173)
- `PORT`, `REDIS_*`, `RATE_LIMIT_*` (optional)

### Frontend (.env)
- `VITE_API_BASE_URL` = http://localhost:8080/api
- `VITE_GITHUB_CLIENT_ID` = your GitHub OAuth app client ID

## Frontend API Service

- **api/client.ts** – Axios instance, JWT interceptor, 401 → logout, auth storage
- **api/endpoints.ts** – All API calls (auth, users, projects, ships, feeds, notifications)

## Replaced Mock Data

| View | Before | After |
|------|--------|-------|
| App | MOCK_PROJECTS, CURRENT_USER | AuthContext + API |
| LoginView | onClick → onLogin() | GitHub OAuth redirect + callback |
| FeedView | mockUpdates array | getFollowingFeed + getTrendingFeed |
| NotificationsView | hardcoded notifications | getNotifications API |

## Integration Verification Steps

1. **Backend**
   - `mvn clean verify` – tests pass
   - Start with MongoDB + Redis: `java -jar target/shipspace-mvp-*.jar`
   - Health: `curl http://localhost:8080/api/health`

2. **Frontend**
   - `npm install` (includes axios)
   - Create `.env` from `.env.example`
   - `npm run dev` – runs on port 3000

3. **E2E**
   - Configure GitHub OAuth app with callback `http://localhost:3000/auth/callback`
   - Login with GitHub
   - Feed loads from API (trending or following)
   - Profile, notifications use real data
   - Logout clears session

4. **CORS**
   - Frontend at localhost:3000 can call backend at localhost:8080
   - `withCredentials: true` for cookies if used

5. **Docker**
   - `docker-compose up` (if present)
   - Backend health responds
   - Frontend build: `npm run build`
