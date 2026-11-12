"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useProjectAgents } from "@/features/agent/api";
import { useProject, useProjectDocuments, useUsage } from "@/features/project/api";
import { Card, Loading, StatusBadge } from "@/components/ui";
import { DOC_TYPES } from "@/types";

export default function OverviewPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const project = useProject(projectId);
  const docs = useProjectDocuments(projectId);
  const usage = useUsage(projectId);
  const agents = useProjectAgents(projectId);
  const base = `/projects/${projectId}`;
  const used = (usage.data?.inputTokens ?? 0) + (usage.data?.outputTokens ?? 0);
  const budget = usage.data?.dailyTokenBudget;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{project.data?.name}</h1>
      <Card>
        <h2 className="mb-2 font-semibold">Official documents</h2>
        {docs.isPending ? (
          <Loading />
        ) : (
          <ul className="grid gap-2 sm:grid-cols-2">
            {DOC_TYPES.map((t) => {
              const of = docs.data?.filter((d) => d.type === t) ?? [];
              const approved = of.filter((d) => d.currentApprovedVersionId).length;
              return (
                <li key={t} className="flex items-center justify-between rounded border border-slate-200 px-3 py-2 text-sm">
                  <Link href={`${base}/documents?type=${t}`} className="font-medium hover:underline">
                    {t}
                  </Link>
                  {approved > 0 ? <StatusBadge status="APPROVED" /> : <span className="text-slate-500">no approved version</span>}
                </li>
              );
            })}
          </ul>
        )}
      </Card>
      <Card>
        <h2 className="mb-2 font-semibold">Today&apos;s usage</h2>
        {usage.isPending ? (
          <Loading />
        ) : (
          <p className="text-sm">
            {used.toLocaleString()} tokens ({usage.data?.inputTokens?.toLocaleString()} in / {usage.data?.outputTokens?.toLocaleString()} out)
            {budget ? ` of ${budget.toLocaleString()} (${Math.max(0, budget - used).toLocaleString()} remaining)` : " - no daily budget set"}
          </p>
        )}
      </Card>
      <Card>
        <h2 className="mb-2 font-semibold">Agents</h2>
        {agents.isPending ? (
          <Loading />
        ) : agents.data?.length ? (
          <ul className="space-y-1 text-sm">
            {agents.data.map((a) => (
              <li key={a.id}>
                {a.name} · {a.model} · {a.isEnabled ? "enabled" : "disabled"}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-slate-500">
            No agents yet. <Link className="text-indigo-700 underline" href={`${base}/agents`}>Create one</Link>.
          </p>
        )}
      </Card>
    </div>
  );
}
