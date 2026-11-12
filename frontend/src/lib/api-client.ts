/** One fetch wrapper: CSRF header on mutations, problem+json -> ApiError, 401 -> login. */

export interface FieldIssue {
  field: string;
  message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: FieldIssue[];
  readonly extra: Record<string, unknown>;

  constructor(status: number, code: string, detail: string, fields: FieldIssue[] = [], extra: Record<string, unknown> = {}) {
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = fields;
    this.extra = extra;
  }
}

const MUTATING = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  for (const part of document.cookie.split(";")) {
    const [k, ...v] = part.trim().split("=");
    if (k === name) return decodeURIComponent(v.join("="));
  }
  return null;
}

async function parseError(res: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    body = (await res.json()) as Record<string, unknown>;
  } catch {
    /* not json */
  }
  const { type: _t, title, status: _s, code, detail, fields, ...extra } = body;
  void _t;
  void _s;
  return new ApiError(
    res.status,
    typeof code === "string" ? code : `HTTP_${res.status}`,
    typeof detail === "string" ? detail : typeof title === "string" ? title : `Request failed (${res.status})`,
    Array.isArray(fields) ? (fields as FieldIssue[]) : [],
    extra,
  );
}

async function ensureCsrf(): Promise<string | null> {
  let token = readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch("/api/v1/me", { credentials: "same-origin" }); // any GET issues the cookie, even a 401 one
    token = readCookie("XSRF-TOKEN");
  }
  return token;
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  headers?: Record<string, string>;
  /** Do not redirect to /login on 401 (used by the session probe). */
  noRedirect?: boolean;
}

export interface ApiResponse<T> {
  data: T;
  etag: string | null;
  status: number;
}

export function onUnauthenticated(): void {
  if (typeof window === "undefined") return;
  if (window.location.pathname.startsWith("/login")) return;
  const next = encodeURIComponent(window.location.pathname + window.location.search);
  // Full navigation on purpose: it drops all client state of the dead session.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.href = `/login?next=${next}`;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<ApiResponse<T>> {
  const method = (opts.method ?? "GET").toUpperCase();
  const url = new URL(`/api/v1${path}`, typeof window === "undefined" ? "http://localhost" : window.location.origin);
  for (const [k, v] of Object.entries(opts.query ?? {})) {
    if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
  }
  const headers: Record<string, string> = { Accept: "application/json", ...(opts.headers ?? {}) };
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  if (MUTATING.has(method)) {
    const token = await ensureCsrf();
    if (token) headers["X-XSRF-TOKEN"] = token;
  }
  const res = await fetch(url.pathname + url.search, {
    method,
    headers,
    credentials: "same-origin",
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
  if (!res.ok) {
    const err = await parseError(res);
    if (err.status === 401 && !opts.noRedirect) onUnauthenticated();
    throw err;
  }
  const etag = res.headers.get("ETag");
  const text = res.status === 204 ? "" : await res.text();
  return { data: (text ? JSON.parse(text) : undefined) as T, etag, status: res.status };
}

export const api = {
  get: <T>(path: string, query?: RequestOptions["query"]) => request<T>(path, { query }).then((r) => r.data),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body }).then((r) => r.data),
  patch: <T>(path: string, body: unknown, headers?: Record<string, string>) =>
    request<T>(path, { method: "PATCH", body, headers }).then((r) => r.data),
};

/** Fetches every page of a cursor list (small lists only: roles, agents, credentials...). */
export async function listAll<T>(path: string, query: RequestOptions["query"] = {}): Promise<T[]> {
  const out: T[] = [];
  let cursor: string | undefined;
  for (let i = 0; i < 50; i++) {
    const page = await api.get<{ items: T[]; nextCursor?: string | null }>(path, { limit: 200, ...query, cursor });
    out.push(...page.items);
    if (!page.nextCursor) break;
    cursor = page.nextCursor;
  }
  return out;
}

/** User-facing text for an error; never includes request bodies. */
export function describeError(e: unknown): string {
  if (e instanceof ApiError) {
    switch (e.code) {
      case "FORBIDDEN":
        return "You do not have permission to do this.";
      case "NOT_FOUND":
        return "Not found.";
      case "PROVIDER_REJECTED_KEY":
        return "The provider rejected this key.";
      case "PRECONDITION_FAILED":
        return "This content changed elsewhere. Reload before saving.";
      default:
        return e.fields.length ? `${e.message}: ${e.fields.map((f) => `${f.field} ${f.message}`).join("; ")}` : e.message;
    }
  }
  return "Network error. Check your connection and try again.";
}
