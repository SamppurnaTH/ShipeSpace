# KREVO Render Deployment Runbook

## Decision

Deploy KREVO to Render for the first controlled beta. Move to AWS later after the MVP gates pass and operational needs are clearer.

## Target Architecture

| Component | Render setup | Notes |
| --- | --- | --- |
| Frontend | Static site: `krevo-web` | Builds `web/` and serves `web/dist`. |
| Backend | Docker web service: `krevo-api` | Builds root `Dockerfile`; listens on `PORT=8080`. |
| Redis | Render Key Value: `krevo-redis` | Wired to backend through `REDIS_URL`. |
| MongoDB | MongoDB Atlas external cluster | Render does not provide MongoDB; set `MONGODB_URI` manually. |
| Media uploads | Current Multibase env placeholders | R2/S3 swap remains Phase 0.2 storage work. |

## Why The Current GitHub CI/CD Takes Too Long

The GitHub workflow runs a full chain on `main`: Maven verify, integration tests, frontend lint/build, CodeQL, OWASP, Docker build, Trivy, Docker push, and placeholder deploy jobs. Older runs were cancelled after roughly six hours, so this is an existing workflow-design issue.

The production deploy job in `.github/workflows/ci-cd.yml` is not a real deploy yet. It contains commented Kubernetes commands and an `echo "Deployment complete"` line. Render should therefore be treated as the real MVP deployment path, and GitHub Actions should be simplified later into fast CI plus optional nightly/security jobs.

## Render Blueprint

`render.yaml` defines:

- `krevo-api`
- `krevo-web`
- `krevo-redis`

Secrets are intentionally declared with `sync: false`, so Render prompts for them in the dashboard and no secrets are committed.

## Required External Setup

### 1. MongoDB Atlas

Create a MongoDB Atlas cluster and database for beta.

Required backend env:

```env
MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>/<database>?retryWrites=true&w=majority
```

Use a dedicated beta database. Do not point Render at your local Docker MongoDB.

### 2. GitHub OAuth App

For Render `onrender.com` URLs, configure the OAuth app with:

```text
Homepage URL:
https://krevo-web.onrender.com

Authorization callback URL:
https://krevo-web.onrender.com/auth/callback
```

If you use a custom domain, replace the URLs with that domain.

Required env values:

```env
GITHUB_CLIENT_ID=<from GitHub OAuth app>
GITHUB_CLIENT_SECRET=<from GitHub OAuth app>
GITHUB_REDIRECT_URI=https://krevo-web.onrender.com/auth/callback
VITE_GITHUB_CLIENT_ID=<same client id>
VITE_GITHUB_REDIRECT_URI=https://krevo-web.onrender.com/auth/callback
```

### 3. CORS

Backend env:

```env
CORS_ALLOWED_ORIGINS=https://krevo-web.onrender.com
```

If using custom domains, include both the Render preview URL and custom domain during setup:

```env
CORS_ALLOWED_ORIGINS=https://krevo-web.onrender.com,https://krevo.app
```

### 4. Admin Bootstrap

Backend env:

```env
ADMIN_GITHUB_IDS=153720530,257914593
```

Confirm the real admin GitHub numeric ID before first Render OAuth login.

### 5. Media Uploads

Current required env values:

```env
MULTIBASE_API_URL=<reachable service url>
MULTIBASE_API_KEY=<secret>
```

Known status: this remains blocked until Phase 0.2 swaps uploads to R2/S3 or Multibase is configured with a real reachable service.

## Render Creation Steps

1. Push `render.yaml` to GitHub.
2. In Render, choose **New > Blueprint**.
3. Connect `https://github.com/SamppurnaTH/ShipeSpace`.
4. Select branch `main`.
5. Let Render detect `render.yaml`.
6. Fill all `sync: false` secrets.
7. Create services.
8. Wait for `krevo-api`, `krevo-web`, and `krevo-redis` to deploy.
9. Open `https://krevo-web.onrender.com`.
10. Run `docs/MVP_Manual_QA_Runbook.md` Section 0 preflight.

## First Smoke Checks

Backend:

```powershell
Invoke-WebRequest https://krevo-api.onrender.com/api/actuator/health/liveness
Invoke-WebRequest https://krevo-api.onrender.com/api/health
```

Frontend:

```text
https://krevo-web.onrender.com
```

OAuth:

```text
https://krevo-web.onrender.com/auth
```

## Important Notes

- Keep backend and frontend as separate Render services for beta.
- Do not use Docker Compose on Render for this app.
- Do not expose Redis publicly. Use Render Key Value connection string.
- Do not commit secrets into `render.yaml`.
- Render deploys should become the MVP deployment source of truth; the current GitHub Actions production deploy job is placeholder-only.

