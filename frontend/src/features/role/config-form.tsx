"use client";

import { useState } from "react";
import { Banner, Button, ErrorBox, Field, Select, Textarea, cx } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { DOC_TYPES, type ConfigVersion, type ContextProfile, type Permission } from "@/types";

const CODES = ["READ_DOCUMENT", "PROPOSE_DOCUMENT", "COMMENT_ON_VERSION", "READ_REPO", "EXPORT_PATCH", "PROPOSE_TASK"] as const;
const ENFORCED = new Set<string>(["READ_DOCUMENT", "PROPOSE_DOCUMENT"]);
const COLUMNS = ["*", ...DOC_TYPES];

type DocRow = { include: boolean; mode: "FULL" | "SUMMARY"; required: boolean };

function toRows(p?: ContextProfile): Record<string, DocRow> {
  const rows: Record<string, DocRow> = {};
  for (const t of DOC_TYPES) rows[t] = { include: false, mode: "FULL", required: false };
  for (const d of p?.documents ?? []) if (d.type) rows[d.type] = { include: true, mode: d.mode ?? "FULL", required: !!d.required };
  return rows;
}

export function ConfigForm({
  initial,
  pending,
  error,
  onSubmit,
}: {
  initial?: ConfigVersion;
  pending: boolean;
  error: unknown;
  onSubmit: (v: { systemPrompt: string; contextProfile: ContextProfile; permissions: Permission[] }) => void;
}) {
  const [prompt, setPrompt] = useState(initial?.systemPrompt ?? "");
  const [rows, setRows] = useState(() => toRows(initial?.contextProfile));
  const [recent, setRecent] = useState(initial?.contextProfile?.recentMessages ?? 20);
  const [perms, setPerms] = useState<Set<string>>(
    () => new Set((initial?.permissions ?? []).map((p) => `${p.code}|${p.resourceType ?? "*"}`)),
  );

  const toggle = (code: string, col: string) =>
    setPerms((s) => {
      const n = new Set(s);
      const k = `${code}|${col}`;
      if (n.has(k)) n.delete(k);
      else n.add(k);
      return n;
    });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      systemPrompt: prompt,
      contextProfile: {
        documents: DOC_TYPES.filter((t) => rows[t]?.include).map((t) => ({ type: t, mode: rows[t]!.mode, required: rows[t]!.required })),
        includeUnapproved: false,
        recentMessages: recent,
      },
      permissions: [...perms].map((k) => {
        const [code, resourceType] = k.split("|");
        return { code: code as Permission["code"], resourceType };
      }),
    });
  };

  const offending = error instanceof ApiError && error.code === "CONTEXT_PROFILE_EXCEEDS_PERMISSION" ? String(error.extra.documentType) : null;

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="System prompt">
        <Textarea required rows={10} value={prompt} onChange={(e) => setPrompt(e.target.value)} />
      </Field>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Context profile (which approved documents the agent sees)</legend>
        <Banner tone="info">SUMMARY currently behaves like FULL (summaries are not implemented yet).</Banner>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-slate-500">
              <th>Document</th>
              <th>Include</th>
              <th>Mode</th>
              <th>Required</th>
            </tr>
          </thead>
          <tbody>
            {DOC_TYPES.map((t) => (
              <tr key={t} className={cx(offending === t && "bg-red-50 ring-1 ring-red-400")} data-offending={offending === t || undefined}>
                <td className="py-1">{t}</td>
                <td>
                  <input type="checkbox" aria-label={`Include ${t}`} checked={rows[t]!.include} onChange={(e) => setRows({ ...rows, [t]: { ...rows[t]!, include: e.target.checked } })} />
                </td>
                <td>
                  <Select aria-label={`Mode ${t}`} className="w-28" value={rows[t]!.mode} onChange={(e) => setRows({ ...rows, [t]: { ...rows[t]!, mode: e.target.value as "FULL" | "SUMMARY" } })}>
                    <option>FULL</option>
                    <option>SUMMARY</option>
                  </Select>
                </td>
                <td>
                  <input type="checkbox" aria-label={`Required ${t}`} checked={rows[t]!.required} onChange={(e) => setRows({ ...rows, [t]: { ...rows[t]!, required: e.target.checked } })} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Field label="Recent messages kept">
          <input type="number" min={0} max={200} className="w-24 rounded border border-slate-300 px-2 py-1 text-sm" value={recent} onChange={(e) => setRecent(Number(e.target.value))} />
        </Field>
      </fieldset>

      <fieldset>
        <legend className="mb-1 text-sm font-medium">Permissions</legend>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-slate-500">
              <th>Permission</th>
              {COLUMNS.map((c) => (
                <th key={c}>{c === "*" ? "All" : c}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {CODES.map((code) => (
              <tr key={code}>
                <td className="py-1">
                  {code}
                  {!ENFORCED.has(code) && <span className="ml-1 text-xs text-slate-500">(stored, not enforced yet)</span>}
                </td>
                {COLUMNS.map((c) => (
                  <td key={c}>
                    <input type="checkbox" aria-label={`${code} ${c}`} checked={perms.has(`${code}|${c}`)} onChange={() => toggle(code, c)} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        <p className="mt-1 text-xs text-slate-500">There is no approve, delete or deploy permission: agents only propose.</p>
      </fieldset>

      {offending ? (
        <Banner tone="danger">
          The context profile includes <strong>{offending}</strong> but READ_DOCUMENT does not cover it. Grant READ_DOCUMENT for {offending} or remove it from the profile.
        </Banner>
      ) : (
        <ErrorBox error={error} />
      )}
      <Button type="submit" disabled={pending || !prompt.trim()}>
        Save as new (inactive) version
      </Button>
    </form>
  );
}
