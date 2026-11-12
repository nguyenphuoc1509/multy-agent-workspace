import type { NextConfig } from "next";

// NOTE: rewrites are evaluated when `next build` / `next dev` starts, so BACKEND_URL must be set then.
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // The browser only talks to its own origin; /api/* goes to Spring Boot. The SSE stream has its own route
  // handler (src/app/api/v1/runs/[runId]/stream/route.ts) because rewrite proxies may buffer event streams.
  async rewrites() {
    // Everything except the SSE stream (handled by the route handler so it is never buffered or compressed).
    return [{ source: "/api/:path((?!v1/runs/[^/]+/stream$).*)", destination: `${backend}/api/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "same-origin" },
          { key: "X-Frame-Options", value: "DENY" },
        ],
      },
    ];
  },
};

export default nextConfig;
