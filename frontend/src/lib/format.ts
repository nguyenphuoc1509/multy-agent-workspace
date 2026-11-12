export function formatDate(iso?: string | null): string {
  if (!iso) return "-";
  return new Date(iso).toLocaleString();
}

/** "sha256:9a1f..." -> "9a1f3c2b" */
export function shortHash(hash?: string | null): string {
  if (!hash) return "-";
  return hash.replace(/^sha256:/, "").slice(0, 8);
}

export function newRequestId(): string {
  return crypto.randomUUID();
}
