# workspace-web (frontend)

Next.js (App Router, TypeScript strict) + TanStack Query + Tailwind. Talks only to its own origin; `/api/*` is proxied to Spring Boot.

## Run
```bash
npm install
npm run dev            # http://localhost:3000 ; backend expected at http://localhost:8080
```
Another backend URL: set `BACKEND_URL` **before** `npm run dev` / `npm run build` (Next.js evaluates rewrites at start/build time).

## Scripts
| Script | What |
|---|---|
| `npm run gen:api` | regenerate `src/types/api.ts` from `../docs/api/openapi.yaml` (never edit it by hand) |
| `npm run check:api` | fails if `src/types/api.ts` is stale relative to the contract (CI runs it) |
| `npm run check` | lint + typecheck + unit tests |
| `npm run build` | production build |

## Notes
- **SSE** (`/api/v1/runs/{id}/stream`) is served by a route handler (`src/app/api/v1/runs/[runId]/stream/route.ts`), not by the rewrite:
  proxies may buffer or compress event streams. Verified against a mock backend: deltas arrive incrementally even with `Accept-Encoding: gzip`.
- API keys are write-only in the UI: cleared from component state on submit, mutation cache reset (`add-credential-form.tsx`, tested).
- Agent/document/comment Markdown is rendered without raw HTML, links limited to http/https/mailto, remote images blocked (`components/markdown.tsx`, tested).
- Version pages live at `/projects/{pid}/versions/{vid}` (the brief nests them under the document; a run snapshot only knows the version id).
