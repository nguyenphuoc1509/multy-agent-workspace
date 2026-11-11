# workspace-backend (Milestones 0-1)

Backend for the multi-agent software development workspace. Implements **M0** (skeleton, session login, CSRF,
projects) and **M1** (documents core, no AI) of `BE-agent-brief.txt` (v1.0).

> **Status:** `mvn verify` runs on every push and pull request in GitHub Actions (`.github/workflows/ci.yml`,
> Testcontainers + real PostgreSQL). Reports: `docs/reports/M0-report.md` (historical, written before the first
> build) and `docs/reports/M1-report.md`. Next: M2 (credentials, roles, agents).

## Requirements
- JDK 21 (`java.version` in `pom.xml`; CI uses the same)
- Maven 3.9+
- Docker (local PostgreSQL via compose, and Testcontainers for the tests)

## Run locally
```bash
docker compose up -d                     # PostgreSQL 16 only
export WORKSPACE_BOOTSTRAP_EMAIL=you@example.com
export WORKSPACE_BOOTSTRAP_PASSWORD='at-least-12-chars-max-72-bytes'
mvn spring-boot:run
```
Windows PowerShell: `$env:WORKSPACE_BOOTSTRAP_EMAIL="you@example.com"` (one command per line).

The first user is created at startup **only if `app_user` is empty** and both variables are set.
There is no public registration. The password is never logged.

The application and tests use UTC as the JVM default time zone. This avoids PostgreSQL connection failures when the host JDK reports an obsolete time zone ID such as `Asia/Saigon`, which newer PostgreSQL time-zone databases may reject.

| Variable | Default | Purpose |
|---|---|---|
| `WORKSPACE_DB_URL` / `_USER` / `_PASSWORD` | local compose values | database |
| `WORKSPACE_BOOTSTRAP_EMAIL` / `_PASSWORD` / `_DISPLAY_NAME` | empty / empty / `Owner` | first user |
| `WORKSPACE_COOKIE_SECURE` | `false` | set `true` everywhere except localhost |
| `WORKSPACE_OPENAPI_ENABLED` | `false` | exposes `/v3/api-docs*` (the export test enables it itself) |

| `WORKSPACE_MASTER_KEY` | none (**required**) | encrypts provider API keys (AES-256-GCM); the app refuses to start without it |
| `WORKSPACE_LLM_FAKE` | `false` | `true` = scripted fake provider, no network (demos without a real key) |

Generate a master key once and keep it out of git: `openssl rand -base64 32`
(PowerShell: `[Convert]::ToBase64String((1..32 | % { Get-Random -Maximum 256 }))`).
Losing it makes stored API keys unrecoverable; they must be added again.

## Test
```bash
mvn verify          # needs Docker; real PostgreSQL through Testcontainers, no H2
```
- `ArchitectureTest` – brief section 4 rules 1-6 plus the allowed-dependency matrix.
- `AuthApiTest`, `FirstUserBootstrapTest`, `ProjectApiTest`, `SchemaTest` – HTTP + DB behaviour.
- `OpenApiExportTest` – regenerates `docs/api/openapi.yaml`. In CI run
  `git diff --exit-code docs/api/openapi.yaml` afterwards so an uncommitted contract change fails the build.

## Auth handshake for the frontend (session cookie + CSRF)
1. Any GET (for example `GET /api/v1/me`, which returns 401 when logged out) sets the readable `XSRF-TOKEN` cookie.
2. Every mutating request, **including login**, must send that value in the `X-XSRF-TOKEN` header.
3. `POST /api/v1/auth/login {email,password}` returns the user and sets the `JSESSIONID` cookie
   (HttpOnly, SameSite=Lax). The session id and the CSRF token are both rotated on login.
4. Same-origin only (Next.js proxies `/api`); there is deliberately no CORS configuration.
5. Failed logins are throttled (5 per 15 minutes per email, then 429 `LOGIN_THROTTLED`).

## Layout
`com.workspace.<module>.{web,application,domain,infrastructure,published}`; other modules may only use
`published`. Modules so far: `shared`, `identity`, `project`, `audit`, `event`, `document`.
