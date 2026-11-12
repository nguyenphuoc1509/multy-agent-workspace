"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Banner, Button, Card, ErrorBox, Loading, Mono, StatusBadge } from "@/components/ui";
import { useRunActions } from "@/features/chat/api";
import { blockHelp } from "@/features/chat/block-help";
import { useProject } from "@/features/project/api";
import { useRun, useSnapshot } from "@/features/run/api";
import { formatDate, shortHash } from "@/lib/format";

export default function RunPage() {
  const { projectId, runId } = useParams<{ projectId: string; runId: string }>();
  const run = useRun(runId);
  const project = useProject(projectId);
  const snapshot = useSnapshot(runId, !!run.data?.snapshotId);
  const actions = useRunActions(run.data?.conversationId ?? "", projectId);
  const r = run.data;
  const owner = project.data?.myRole === "OWNER";
  const active = r?.status === "QUEUED" || r?.status === "RUNNING";

  if (run.isPending) return <Loading />;
  if (!r) return <ErrorBox error={run.error} />;
  const s = snapshot.data;

  return (
    <div className="space-y-5">
      <div>
        <Link href={`/projects/${projectId}/runs`} className="text-xs text-slate-500 hover:underline">
          Runs
        </Link>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold">Run</h1>
          <StatusBadge status={r.status ?? ""} />
        </div>
        <Mono>{r.id}</Mono>
      </div>
      {r.blockReason && (
        <Banner tone="warn">
          Blocked: {blockHelp(r.blockReason, projectId).text}{" "}
          <Link className="underline" href={blockHelp(r.blockReason, projectId).href}>
            Fix it
          </Link>
        </Banner>
      )}
      {r.errorCode && <Banner tone="danger">Failed with {r.errorCode}.</Banner>}
      <Card>
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-slate-500">Model used</dt>
          <dd>{r.model}</dd>
          <dt className="text-slate-500">Attempts</dt>
          <dd>{r.attempt}</dd>
          <dt className="text-slate-500">Tokens (in / out)</dt>
          <dd>
            {r.inputTokens ?? "-"} / {r.outputTokens ?? "-"}
          </dd>
          <dt className="text-slate-500">Created</dt>
          <dd>{formatDate(r.createdAt)}</dd>
          <dt className="text-slate-500">Finished</dt>
          <dd>{formatDate(r.finishedAt)}</dd>
          {r.conversationId && (
            <>
              <dt className="text-slate-500">Conversation</dt>
              <dd>
                <Link className="text-indigo-700 underline" href={`/projects/${projectId}/chat/${r.conversationId}`}>
                  Open
                </Link>
              </dd>
            </>
          )}
        </dl>
        {owner && (
          <div className="mt-3 flex gap-2">
            {active && (
              <Button variant="secondary" onClick={() => actions.cancel.mutate(r.id!, { onSuccess: () => run.refetch() })}>
                Cancel run
              </Button>
            )}
            {(r.status === "FAILED" || r.status === "BLOCKED" || r.status === "CANCELLED") && (
              <Button onClick={() => actions.retry.mutate(r.id!)}>Retry</Button>
            )}
          </div>
        )}
        {actions.retry.data && (
          <p className="mt-2 text-sm">
            New run started:{" "}
            <Link className="text-indigo-700 underline" href={`/projects/${projectId}/runs/${actions.retry.data.id}`}>
              open it
            </Link>
          </p>
        )}
        <ErrorBox error={actions.cancel.error ?? actions.retry.error} />
      </Card>

      <Card>
        <h2 className="mb-2 font-semibold">Context snapshot - what the agent saw</h2>
        {!r.snapshotId && <p className="text-sm text-slate-500">No snapshot: the run ended before its context was assembled.</p>}
        {snapshot.isPending && r.snapshotId && <Loading />}
        <ErrorBox error={snapshot.error} />
        {s && (
          <div className="space-y-3 text-sm">
            <p className="text-slate-600">
              Assembler {s.assemblerVersion} · about {s.tokenEstimate} tokens · history up to message #{s.lastMessageSeq ?? "-"}
            </p>
            {s.items?.length ? (
              <table className="w-full">
                <thead>
                  <tr className="text-left text-xs text-slate-500">
                    <th>Document</th>
                    <th>Version</th>
                    <th>Hash</th>
                    <th>Mode</th>
                  </tr>
                </thead>
                <tbody>
                  {s.items.map((i) => (
                    <tr key={i.documentVersionId}>
                      <td>{i.documentType}</td>
                      <td>
                        <Link className="text-indigo-700 underline" href={`/projects/${projectId}/versions/${i.documentVersionId}`}>
                          v{i.versionNo}
                        </Link>
                      </td>
                      <td>
                        <Mono>{shortHash(i.contentHash)}</Mono>
                      </td>
                      <td>{i.inclusionMode}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p className="text-slate-500">No approved documents were available.</p>
            )}
            {!!s.missingDocumentTypes?.length && <p>Missing document types: {s.missingDocumentTypes.join(", ")}</p>}
            {!!s.dependencyRunIds?.length && <p>Depends on runs: {s.dependencyRunIds.join(", ")}</p>}
          </div>
        )}
      </Card>
    </div>
  );
}
