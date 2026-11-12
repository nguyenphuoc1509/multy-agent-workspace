import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, request } from "@/lib/api-client";
import { safeNext } from "@/features/auth/api";

function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...headers } });
}

describe("api-client", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=tok123";
  });
  afterEach(() => {
    vi.restoreAllMocks();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("sends the CSRF header on mutations only", async () => {
    const f = vi.spyOn(globalThis, "fetch").mockImplementation(async () => json(200, { ok: true }));
    await request("/x", { method: "POST", body: { a: 1 } });
    await request("/x");
    const post = f.mock.calls[0]![1]!.headers as Record<string, string>;
    const get = f.mock.calls[1]![1]!.headers as Record<string, string>;
    expect(post["X-XSRF-TOKEN"]).toBe("tok123");
    expect(get["X-XSRF-TOKEN"]).toBeUndefined();
  });

  it("fetches a CSRF cookie first when none exists", async () => {
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    const f = vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (String(url).startsWith("/api/v1/me")) document.cookie = "XSRF-TOKEN=fresh";
      return json(200, {});
    });
    await request("/x", { method: "POST" });
    expect(String(f.mock.calls[0]![0])).toBe("/api/v1/me");
    expect((f.mock.calls[1]![1]!.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("fresh");
  });

  it("parses problem+json into a typed ApiError with extras", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      json(409, { type: "urn:x", title: "Conflict", status: 409, code: "STALE_CONTENT", detail: "changed", currentHash: "sha256:abc" }),
    );
    const err = await request("/x", { method: "POST" }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 409, code: "STALE_CONTENT", extra: { currentHash: "sha256:abc" } });
  });

  it("maps validation fields", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json(400, { code: "VALIDATION_FAILED", detail: "bad", fields: [{ field: "name", message: "required" }] }));
    const err = (await request("/x", { method: "POST" }).catch((e) => e)) as ApiError;
    expect(err.fields).toEqual([{ field: "name", message: "required" }]);
  });

  it("returns the ETag", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(json(200, { id: "1" }, { ETag: '"sha256:aa"' }));
    expect((await request("/x")).etag).toBe('"sha256:aa"');
  });

  it("only follows same-origin relative targets after login", () => {
    expect(safeNext("/projects/1")).toBe("/projects/1");
    expect(safeNext("//evil.com")).toBe("/projects");
    expect(safeNext("https://evil.com")).toBe("/projects");
    expect(safeNext(null)).toBe("/projects");
  });
});
