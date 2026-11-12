# Report: BE M2-M3, FE F0-F3 (MVP for trial)

## 1. Done
- **M2** credentials (AES-256-GCM, write-only keys, revoke), roles (versioned configs, 5 defaults), agents, context preview.
- **M3a-d** conversations/messages (idempotent via clientRequestId), deterministic context assembly + immutable snapshots, runs (queue + worker with SKIP LOCKED, cancel/retry, SSE, budget and time limits, blocked reasons), Anthropic SSE adapter (tested against a local server), promote (message -> agent-authored DRAFT), usage endpoint.
- **F0-F3** Next.js app: login/CSRF, projects, documents with approval (hash-bound, STALE/GATE handling, diff, comments), credentials, roles (config editor, permission matrix, activation), agents (+context preview), chat with streaming, runs and snapshot view, promote dialog.
- Backend: 167 tests green in CI. Frontend: lint, typecheck, 19 unit tests, build green in CI.

## 2. Deviations
- Version page route is `/projects/{pid}/versions/{vid}` (snapshots only know the version id).
- Document editor is a monospace textarea (no CodeMirror) to keep the MVP small; YAML/Markdown modes can be added later.
- New custom role starts with config v1 active.
- Revocation is handled at run pre-flight (BLOCKED `CREDENTIAL_REVOKED`); the outbox dispatcher/consumers arrive in M4.
- No MSW/Playwright yet; unit tests use mocked fetch. Not covered: full browser flows against a real backend.
- CSP: only basic security headers; nonce-based CSP is M5.

## 3. Not built (by plan)
Tasks, attention inbox, audit screen (M4/F4), hardening (M5/F5). SUMMARY mode behaves like FULL.

## 4. Open questions (proposed default)
1. VIEWER cannot comment (default: keep; only OWNER writes).
2. Roles/agents/credentials are OWNER-only even for reads (default: keep).
3. Add CodeMirror for editing? (default: later, if the textarea hurts).

## 5. Risks
Streaming through proxies: handled by a route handler (verified with a mock stream incl. gzip); a reverse proxy in front must also disable buffering. `BACKEND_URL` is read at build/start time.
