# ShipSpace

Social network for technical builders — share project updates ("Ships"), follow other builders, earn rank XP.

## Architecture

- **Frontend**: React 19 + Vite 8, TypeScript, runs on port 5000
- **Backend**: Spring Boot 3.2 (Java 17), runs on port 8080
- **Database**: MongoDB 7 (local, data in /tmp/mongodb-data)
- **Cache**: Redis 7 (local, port 6379)
- **Auth**: GitHub OAuth 2.0 + JWT

## Workflows

- **Start application** — runs `bash start-frontend.sh` (Vite dev server, port 5000)
- **Backend** — runs `bash start-backend.sh` (starts MongoDB + Redis + Spring Boot, port 8080)

## Key Files

- `web/` — React frontend source
- `web/src/api/client.ts` — Axios client (proxies `/api` through Vite to port 8080)
- `web/src/api/endpoints.ts` — All API endpoint functions
- `web/src/auth/AuthContext.tsx` — GitHub OAuth + JWT auth context
- `web/vite.config.ts` — Vite config (host 0.0.0.0, port 5000, proxy /api→8080)
- `src/main/java/com/shipspace/` — Spring Boot source
- `src/main/resources/application.yml` — Spring Boot config

## Environment Variables

- `JWT_SECRET` — Required, set in Replit secrets (min 32 chars)
- `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` — GitHub OAuth app (optional for non-auth features)
- `MONGODB_URI` — defaults to `mongodb://localhost:27017/shipspace`
- `REDIS_HOST` / `REDIS_PORT` — defaults to localhost:6379

## GitHub OAuth Setup

1. Create a GitHub OAuth app at https://github.com/settings/developers
2. Set callback URL to `https://<your-replit-domain>/auth/callback`
3. Add `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` to Replit secrets
4. Set `GITHUB_REDIRECT_URI` env var to the same callback URL

## API Endpoints (all prefixed /api)

Auth, Users, Social, Feed, Ships, Projects, Notifications, Collaborations, Search — full list in README.md
