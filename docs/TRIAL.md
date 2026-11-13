# Trying the MVP locally

You need: Docker, JDK 21, Maven 3.9+, Node 22+. Four terminals (or run steps 1-2 once).

## 1. Database
```powershell
docker compose up -d
```

## 2. Backend (terminal 2, folder `backend`)
```powershell
$env:WORKSPACE_BOOTSTRAP_EMAIL="you@example.com"
$env:WORKSPACE_BOOTSTRAP_PASSWORD="at-least-12-chars"
$env:WORKSPACE_MASTER_KEY=[Convert]::ToBase64String((1..32 | % { Get-Random -Maximum 256 }))
$env:WORKSPACE_LLM_FAKE="true"        # no real key needed; remove this line to call Anthropic for real
mvn spring-boot:run
```
Keep the master key: it encrypts the API keys you store (lose it = add the keys again).
The first user is created automatically (only when the user table is empty).

## 3. Frontend (terminal 3, folder `frontend`)
```powershell
npm install
npm run dev
```
Open http://localhost:3000 and sign in with the e-mail/password above.

## Optional: use your Claude login instead of an API key (Claude Code CLI)
Needs the official CLI installed and logged in on the same machine (`claude` works in a terminal). Start the backend with
```powershell
$env:WORKSPACE_LLM_CLAUDE_CODE_ENABLED="true"
```
then create an agent with provider **Claude Code (local login)** (model e.g. `sonnet`); it needs no credential.
The CLI is started per run with all tools disabled, no MCP/skills/settings, in an empty temp folder: the agent can only produce text.
Check Anthropic's current terms for your plan before relying on this for anything beyond personal use. Limits: no token cap per run, `maxOutputTokens` is not enforced, history is sent as one transcript.

## 4. Walk through
1. **Projects** > create a project.
2. **Credentials** > add a key. With `WORKSPACE_LLM_FAKE=true` any text works except one starting with `sk-bad` (rejected). With a real key, it is validated against Anthropic.
3. **Documents** > create a REQUIREMENTS draft > *Submit for review* > *Approve* (confirm dialog shows the exact version and hash). Try creating ARCHITECTURE first: approval is refused until REQUIREMENTS is approved.
4. **Agents** > create an agent (e.g. role *Architect*, model `claude-sonnet-5-5`, pick the credential). *Context preview* shows what it will see.
5. **Chat** > new conversation with that agent > send a message. The answer streams in. Fake mode replies `[fake] You said: ...`; try `[slow]` (cancel it), `[fail-once]`, `[fail-permanent]`.
6. On an agent message: **Promote to draft** (ARCHITECT may propose ARCHITECTURE/API_CONTRACT/ADR) > open the draft > submit > approve. Approve a new REQUIREMENTS version and the older conversation shows a *stale* warning.
7. **Runs** > open a run: model used, tokens, and the exact documents/versions/hashes the agent saw.

## Not in this MVP yet
Tasks, attention inbox, audit screen, and the outbox event consumers (M4); hardening such as CSP nonces (M5).
