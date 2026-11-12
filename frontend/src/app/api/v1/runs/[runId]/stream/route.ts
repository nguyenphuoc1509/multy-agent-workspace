/** Streams the backend SSE response through untouched (rewrites may buffer; this handler does not). */
export const dynamic = "force-dynamic";

const backend = process.env.BACKEND_URL ?? "http://localhost:8080";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function GET(req: Request, ctx: { params: Promise<{ runId: string }> }) {
  const { runId } = await ctx.params;
  if (!UUID.test(runId)) return new Response("Not found", { status: 404 });
  const upstream = await fetch(`${backend}/api/v1/runs/${runId}/stream`, {
    headers: { cookie: req.headers.get("cookie") ?? "", accept: "text/event-stream" },
    signal: req.signal,
    cache: "no-store",
  });
  if (!upstream.ok || !upstream.body) {
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" },
    });
  }
  return new Response(upstream.body, {
    status: 200,
    headers: {
      "content-type": "text/event-stream; charset=utf-8",
      "cache-control": "no-cache, no-transform",
      "x-accel-buffering": "no",
    },
  });
}
