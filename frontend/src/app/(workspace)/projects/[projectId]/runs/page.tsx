"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { Card, Empty, ErrorBox, Loading, Select, StatusBadge } from "@/components/ui";
import { useRuns } from "@/features/run/api";
import { formatDate } from "@/lib/format";

const STATUSES = ["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "BLOCKED", "CANCELLED"];

export default function RunsPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [status, setStatus] = useState("");
  const runs = useRuns(projectId, status || undefined);
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Runs</h1>
        <Select aria-label="Filter by status" className="w-44" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s}>{s}</option>
          ))}
        </Select>
      </div>
      {runs.isPending && <Loading />}
      <ErrorBox error={runs.error} />
      {runs.data?.length === 0 && <Empty>No runs yet. Runs appear when you send a message to an agent.</Empty>}
      <ul className="space-y-2">
        {runs.data?.map((r) => (
          <li key={r.id}>
            <Link href={`/projects/${projectId}/runs/${r.id}`}>
              <Card className="flex items-center justify-between hover:border-indigo-400">
                <div className="text-sm">
                  <div className="font-mono text-xs">{r.id}</div>
                  <div className="text-slate-500">
                    {r.model} · {formatDate(r.createdAt)} · {(r.inputTokens ?? 0) + (r.outputTokens ?? 0)} tokens
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {r.blockReason && <span className="text-xs text-amber-700">{r.blockReason}</span>}
                  {r.errorCode && <span className="text-xs text-red-700">{r.errorCode}</span>}
                  <StatusBadge status={r.status ?? ""} />
                </div>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
