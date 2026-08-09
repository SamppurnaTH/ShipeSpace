# ShipSpace MVP Backend Readiness Checklist

Use this checklist before starting UI enhancement work.

## Automated Gates

- `npm.cmd run lint` passes in `web`.
- `npm.cmd run build` passes in `web`.
- `.\mvnw.cmd -q -DskipTests compile` passes.
- Focused backend gate passes:

```powershell
.\mvnw.cmd -q '-Dtest=AuthServiceRbacTest,BannedUserWriteFilterTest,MongoIndexInitializerTest,MultibaseServiceTest,GitHubOAuthServiceTest,JwtUtilTest,UserInterestProfileRepositoryTest' test
```

- Full backend suite either passes or has an accepted defect list.
- `docker compose config` validates.
- `docker compose up --build` starts MongoDB, Redis, backend, and frontend cleanly.

## Route Contract

| Route group | Anonymous | USER | ADMIN |
| --- | --- | --- | --- |
| Health | allowed | allowed | allowed |
| Auth bootstrap | allowed | allowed | allowed |
| Trending feed/search | allowed | allowed | allowed |
| Public profile/read pages | allowed | allowed | allowed |
| Create project | denied | allowed | allowed |
| Create ship | denied | allowed | allowed |
| Like/comment/follow | denied | allowed | allowed |
| Upload ship image | denied | allowed owner | allowed owner |
| Admin users | denied | denied | allowed |
| Ban/unban user | denied | denied | allowed |

## Manual OAuth Gates

- Anonymous user can load the app.
- GitHub login redirects to GitHub and returns to the configured callback.
- Callback exchanges code and creates/updates user.
- Access token includes persisted roles.
- Normal user receives `USER`.
- GitHub numeric ID listed in `ADMIN_GITHUB_IDS` receives `ADMIN`.
- Refresh token preserves roles in new access token.
- Logout succeeds.

## Banned User Gates

- Banned user can read public pages.
- Banned user can log out.
- Banned user cannot create project.
- Banned user cannot create ship.
- Banned user cannot like, comment, or follow.
- Banned user cannot upload.
- Blocked writes return a clear 403 response with `BANNED_USER`.

## Upload Gates

- Valid JPEG upload works.
- Valid PNG upload works.
- Valid GIF upload works.
- Valid WebP upload works.
- Empty file is rejected.
- Oversized file is rejected.
- Unsupported MIME type is rejected.
- Renamed executable with image extension/MIME is rejected by magic-byte validation.
- Multibase outage returns a clean 502-style application error.
- Multibase public/signed delivery policy is reviewed before production.

## Production Config Gates

- `MONGODB_URI` points to a backed-up production cluster.
- Redis host, port, and password are configured.
- `JWT_SECRET` is strong and not reused across environments.
- GitHub OAuth client ID, secret, and callback URL match production.
- `ADMIN_GITHUB_IDS` contains only intended initial admins.
- `MULTIBASE_API_URL` and `MULTIBASE_API_KEY` are set.
- `CORS_ALLOWED_ORIGINS` contains only production frontend origins.
- Swagger is disabled unless intentionally enabled for staging.
- Actuator exposure is limited to health, info, and required metrics.
- Backup/restore procedure is tested.
