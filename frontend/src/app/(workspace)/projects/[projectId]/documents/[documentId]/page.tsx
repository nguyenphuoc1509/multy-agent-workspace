"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { useQueries } from "@tanstack/react-query";
import { DiffView } from "@/components/diff-view";
import { Button, Card, ErrorBox, Loading, Select, StatusBadge } from "@/components/ui";
import { useCreateVersion, useDocument, useVersions } from "@/features/document/api";
import { useProject } from "@/features/project/api";
import { ApiError, api } from "@/lib/api-client";
import { formatDate, shortHash } from "@/lib/format";
import type { Version } from "@/types";

export default function DocumentPage() {
  const { projectId, documentId } = useParams<{ projectId: string; documentId: string }>();
  const router = useRouter();
  const doc = useDocument(documentId);
  const versions = useVersions(documentId);
  const project = useProject(projectId);
  const createVersion = useCreateVersion(documentId);
  const [a, setA] = useState("");
  const [b, setB] = useState("");
  const canWrite = project.data?.myRole === "OWNER";
  const open = doc.data?.openVersionId;
  const base = `/projects/${projectId}/versions`;

  const bodies = useQueries({
    queries: [a, b].map((id) => ({
      queryKey: ["version", id, "body"],
      queryFn: () => api.get<Version>(`/document-versions/${id}`),
      enabled: !!id,
    })),
  });

  const newDraft = (fromVersionId?: string) =>
    createVersion.mutate({ fromVersionId }, { onSuccess: (v) => router.push(`${base}/${v.id}`) });

  const err = createVersion.error;
  return (
    <div className="space-y-6">
      <div>
        <Link href={`/projects/${projectId}/documents`} className="text-xs text-slate-500 hover:underline">
          Documents
        </Link>
        <h1 className="text-2xl font-semibold">{doc.data?.title ?? "..."}</h1>
        <div className="text-sm text-slate-500">{doc.data?.type}</div>
      </div>
      {(doc.isPending || versions.isPending) && <Loading />}
      <ErrorBox error={doc.error ?? versions.error} />
      {canWrite && (
        <div className="flex flex-wrap items-center gap-3">
          <Button onClick={() => newDraft()} disabled={!!open || createVersion.isPending}>
            New draft
          </Button>
          {open && (
            <span className="text-sm text-slate-600">
              This document already has an open version.{" "}
              <Link className="text-indigo-700 underline" href={`${base}/${open}`}>
                Open it
              </Link>
            </span>
          )}
        </div>
      )}
      {err instanceof ApiError && err.code === "OPEN_VERSION_EXISTS" ? (
        <p role="alert" className="text-sm text-red-700">
          An open version exists.{" "}
          <Link className="underline" href={`${base}/${String(err.extra.openVersionId)}`}>
            Go to it
          </Link>
        </p>
      ) : (
        <ErrorBox error={err} />
      )}
      <Card>
        <h2 className="mb-2 font-semibold">Versions</h2>
        <ul className="divide-y divide-slate-100">
          {versions.data
            ?.slice()
            .sort((x, y) => (y.versionNo ?? 0) - (x.versionNo ?? 0))
            .map((v) => (
              <li key={v.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                <div className="flex items-center gap-3">
                  <Link href={`${base}/${v.id}`} className="font-medium text-indigo-700 hover:underline">
                    v{v.versionNo}
                  </Link>
                  <StatusBadge status={v.status ?? ""} />
                  <span className="text-slate-500">
                    {v.authorType} · {formatDate(v.createdAt)} · <code className="font-mono text-xs">{shortHash(v.contentHash)}</code>
                  </span>
                  {v.changeSummary && <span className="text-slate-600">{v.changeSummary}</span>}
                </div>
                {canWrite && !open && v.status !== "DRAFT" && v.status !== "IN_REVIEW" && (
                  <Button variant="secondary" onClick={() => newDraft(v.id)} disabled={createVersion.isPending}>
                    New draft from this version
                  </Button>
                )}
              </li>
            ))}
        </ul>
      </Card>
      <Card>
        <h2 className="mb-2 font-semibold">Compare versions</h2>
        <div className="mb-3 flex gap-3">
          {[
            ["From", a, setA],
            ["To", b, setB],
          ].map(([label, val, set]) => (
            <Select key={label as string} aria-label={label as string} value={val as string} onChange={(e) => (set as (v: string) => void)(e.target.value)}>
              <option value="">{label as string}...</option>
              {versions.data?.map((v) => (
                <option key={v.id} value={v.id}>
                  v{v.versionNo} ({v.status})
                </option>
              ))}
            </Select>
          ))}
        </div>
        {a && b && bodies.every((q) => q.data) ? <DiffView from={bodies[0]?.data?.content ?? ""} to={bodies[1]?.data?.content ?? ""} /> : <p className="text-sm text-slate-500">Pick two versions.</p>}
      </Card>
    </div>
  );
}
