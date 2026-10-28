# M0 report: skeleton, auth, projects

Brief: `BE-agent-brief.txt` v1.0 (2026-10-26). Report format: brief section 16.

## Headline
**Nothing has been compiled or run.** The sandbox this was written in has no Maven Central access, no Maven, no
JDK compiler of the right version (JRE 21 only) and no Docker. A syntax-only `javac` pass found no syntax errors
(every remaining error is a missing-dependency error), which proves nothing about types or behaviour.
Per brief section 16, **no M0 acceptance criterion is claimed as met**. Treat this as a first draft that must be
built once on a real machine.

## 1. Done (mapped to the brief)
- **Section 3/15 M0**: Maven project, Spring Boot 4.1.0, `docker-compose.yml` (PostgreSQL 16 only), README with run/test steps.
- **Section 5**: Flyway `V1__identity_project_shared.sql`, copied from the brief unchanged.
- **Section 4**: module layout `shared`, `identity`, `project` with `web/application/domain/infrastructure/published`.
  `ArchitectureTest` encodes rules 1-6 and the allowed-dependency matrix for all brief modules (rules for
  modules that do not exist yet use `allowEmptyShould`, so they activate automatically).
- **Section 13**: session-cookie auth (JSON login controller), bcrypt via the delegating encoder, session id
  rotation and CSRF rotation on login, cookie-to-header CSRF, HttpOnly + SameSite=Lax session cookie, no CORS,
  first-user bootstrap from env, failed-login throttle, logout invalidates the session.
- **Section 12**: problem+json errors (`code`, `fields`), `{items,nextCursor}` lists, `GET /me`,
  `POST /auth/login|logout`, `POST/GET /projects`, `GET/PATCH /projects/{pid}`. Non-member and non-existent
  project both return 404. `ProjectAccess` (in `project.published`) is ready for later milestones.
- **Section 14**: tests listed in the README. Covered from the mandatory list: non-member 404, CSRF required,
  login throttling, cross-user isolation on projects, V1 constraints against real PostgreSQL, ArchUnit.

## 2. Deviations and gaps
1. **`docs/api/openapi.yaml` is not committed.** I could not run the app. `OpenApiExportTest` writes it on the first
   `mvn verify`; commit the result. M0 acceptance ("openapi.yaml published") is therefore not met yet.
2. **Audit rows are not written** (`LOGIN_SUCCEEDED/FAILED`, `PROJECT_CREATED`): `audit_log` is defined in V9,
   which comes later in the agreed migration order. See open question 1.
3. **Documentation rule not met.** Section 0 requires reading official docs for Spring Boot, Flyway,
   Testcontainers, OpenAPI libraries. I only had web search snippets (see section 6). Spring Security 7,
   Hibernate 7, Flyway and Testcontainers 2 APIs were written from memory and need compiling to confirm.
4. **Java version**: `pom.xml` says 25 (current LTS, assumed supported by Boot 4.1). Not verified against the Boot docs.
5. No ADRs were needed; no brief decision was changed.

## 3. Tests
Run `mvn verify` (Docker required). Written, never executed: `ArchitectureTest` (8 tests), `AuthApiTest` (13),
`FirstUserBootstrapTest` (1), `ProjectApiTest` (9), `SchemaTest` (4), `OpenApiExportTest` (1).
Not covered yet: log redaction, input-size limits, concurrency tests (nothing concurrent exists in M0 except
the throttle), problem+json shape validation against `openapi.yaml`.

## 4. Open questions (each with my default)
1. **M1 needs `audit_log` and `outbox_event` but they are in V9.** M1 says "audit + outbox written in the same
   transaction". *Default:* at the start of M1 add one migration containing the V9 tables, accept that it is no
   longer last in the order, and write an ADR. Until then login and project creation are not audited.
2. **Shared kernel.** The brief says `shared` is a dependency of identity only by name and that `llm` depends on
   nothing. *Default:* every module may depend on `shared`; the ArchUnit matrix is encoded that way.
3. **Login needs a prior GET** to obtain the XSRF cookie. *Default:* keep login CSRF-protected; the FE brief
   should state the handshake (README has it). Alternative: exempt `POST /auth/login`.
4. **My additions not in the brief:** 429 `LOGIN_THROTTLED` (5 failures / 15 min per email, in memory, which also
   lets someone lock the owner out for 15 minutes), max password 72 bytes (bcrypt limit), bootstrap password
   minimum 12 characters, extra codes `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `NOT_ACCEPTABLE`,
   `INTERNAL_ERROR`. *Default:* keep all; tell me which to drop.
5. **PATCH /projects**: an absent field means unchanged, so `dailyTokenBudget` cannot be cleared once set.
   *Default:* leave it; propose JSON Merge Patch semantics later.
6. **Malformed UUID in a path** returns 400 `VALIDATION_FAILED`, not 404. *Default:* keep.
7. **Secure cookie** is controlled by `WORKSPACE_COOKIE_SECURE` (default false), not auto-detected.
8. Bootstrap is env-only; the brief also mentions "a CLI command". *Default:* env only for the MVP.
9. Any logged-in user may create projects (single-user MVP). *Default:* keep.

## 5. Risks noticed
- Most likely first-build failures (APIs I could not check): Boot 4 test package for `@AutoConfigureMockMvc`
  (`org.springframework.boot.webmvc.test.autoconfigure`), Testcontainers 2 class `org.testcontainers.postgresql.PostgreSQLContainer`,
  Spring Security 7 `CsrfAuthenticationStrategy` / `CookieCsrfTokenRepository.setCookieCustomizer`,
  `ddl-auto: validate` against Hibernate 7 column types, `ProblemDetail` extra properties under Jackson 3.
- springdoc 3.0.3 targets Boot 4.0.x; compatibility with 4.1.0 is unconfirmed.
- Session and throttle state are in memory: restart logs everyone out and resets throttling (fine for one instance).
- Timestamps are truncated to microseconds in the app so keyset cursors match PostgreSQL precision; do the same in later modules.
- DB password default `workspace` is for local compose only.

## 6. Libraries, versions, sources
| Item | Pinned | How I got it |
|---|---|---|
| Spring Boot | 4.1.0 (released 2026-06-10) | web search (versionlog.com, a Spring Boot 4 overview). A newer patch may exist. |
| Starter names (`spring-boot-starter-webmvc`, `-flyway`, `-webmvc-test`) | managed by Boot | search snippets of the Spring Boot 4.0 Migration Guide |
| springdoc-openapi | 3.0.3 | GitHub release notes via dependabot PR pages |
| ArchUnit | 1.5.0 | dependabot PRs merged Aug 2026 |
| Testcontainers | managed by Boot (2.x naming `testcontainers-postgresql`) | migration-guide summary |
| Flyway, Hibernate, Spring Security, PostgreSQL driver | managed by Boot BOM | not read |
| Official docs read in full | none | sandbox could not fetch them; please verify |

## 7. OpenAPI file
`docs/api/openapi.yaml`: **not yet generated** (see deviation 1). It is written by `OpenApiExportTest`.
