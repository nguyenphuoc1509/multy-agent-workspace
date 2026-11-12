"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Markdown } from "@/components/markdown";
import { Banner, Button, Card, Dialog, ErrorBox, Field, Input, Loading, Mono, StatusBadge, Textarea } from "@/components/ui";
import { ApproveDialog } from "@/features/document/approve-dialog";
import {
  useAddComment,
  useComments,
  useDiscardVersion,
  useDocument,
  useRejectVersion,
  useSaveVersion,
  useSubmitVersion,
  useVersion,
} from "@/features/document/api";
import { useProject } from "@/features/project/api";
import { ApiError } from "@/lib/api-client";
import { formatDate, shortHash } from "@/lib/format";

export default function VersionPage() {
  const { projectId, versionId } = useParams<{ projectId: string; versionId: string }>();
  const project = useProject(projectId);
  const vq = useVersion(versionId);
  const version = vq.data?.version;
  const doc = useDocument(version?.documentId ?? "");
  const comments = useComments(versionId);
  const save = useSaveVersion(versionId);
  const submit = useSubmitVersion(versionId);
  const discard = useDiscardVersion(versionId);
  const reject = useRejectVersion(versionId);
  const addComment = useAddComment(versionId);

  const [draft, setDraft] = useState<string | null>(null); // null = untouched
  const [summary, setSummary] = useState<string | null>(null);
  const [dialog, setDialog] = useState<null | "approve" | "reject" | "discard">(null);
  const [rejectComment, setRejectComment] = useState("");
  const [comment, setComment] = useState("");
  const [staleBanner, setStaleBanner] = useState(false);

  const dirty = draft !== null || summary !== null;
  useEffect(() => {
    if (!dirty) return;
    const h = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", h);
    return () => window.removeEventListener("beforeunload", h);
  }, [dirty]);

  if (vq.isPending) return <Loading what="Loading version" />;
  if (vq.error || !version) return <ErrorBox error={vq.error} />;

  const isOwner = project.data?.myRole === "OWNER";
  const canReview = project.data?.myRole === "OWNER" || project.data?.myRole === "APPROVER";
  const status = version.status ?? "";
  const body = draft ?? version.content ?? "";
  const yaml = version.contentFormat === "application/yaml";
  const saveErr = save.error;

  const doSave = () => {
    if (!vq.data?.etag) return;
    save.mutate(
      { content: body, changeSummary: summary ?? undefined, etag: vq.data.etag },
      {
        onSuccess: () => {
          setDraft(null);
          setSummary(null);
        },
      },
    );
  };

  return (
    <div className="space-y-5">
      <div>
        <Link href={`/projects/${projectId}/documents/${version.documentId}`} className="text-xs text-slate-500 hover:underline" onClick={(e) => dirty && !confirm("Discard unsaved changes?") && e.preventDefault()}>
          {doc.data?.title ?? "Document"}
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold">
            {doc.data?.title} v{version.versionNo}
          </h1>
          <StatusBadge status={status} />
          <Mono>{shortHash(version.contentHash)}</Mono>
        </div>
        <div className="text-sm text-slate-500">
          {version.authorType === "AGENT" ? `Proposed by agent ${version.authorRef}` : "Written by you"} · {formatDate(version.createdAt)}
          {version.sourceMessageId && <> · from message <Mono>{version.sourceMessageId}</Mono></>}
        </div>
        {version.changeSummary && <div className="text-sm">{version.changeSummary}</div>}
      </div>

      {status !== "APPROVED" && (
        <Banner tone="warn">This version is not official. Agents only read approved versions.</Banner>
      )}
      {staleBanner && <Banner tone="warn">Content changed since you opened it. The latest content is shown - read it again before approving.</Banner>}
      {saveErr instanceof ApiError && saveErr.code === "PRECONDITION_FAILED" ? (
        <Banner tone="danger">
          Changed elsewhere.{" "}
          <button className="underline" onClick={() => { vq.refetch(); save.reset(); }}>
            Reload latest
          </button>{" "}
          (your edit stays in the box until you do).
        </Banner>
      ) : (
        <ErrorBox error={saveErr ?? submit.error ?? discard.error ?? reject.error} />
      )}

      <Card>
        {status === "DRAFT" && isOwner ? (
          <div className="space-y-3">
            <Textarea
              aria-label="Content"
              rows={20}
              className="font-mono"
              value={body}
              onChange={(e) => setDraft(e.target.value)}
            />
            <Field label="Change summary">
              <Input value={summary ?? version.changeSummary ?? ""} onChange={(e) => setSummary(e.target.value)} />
            </Field>
            <div className="flex flex-wrap gap-2">
              <Button onClick={doSave} disabled={!dirty || save.isPending}>
                Save
              </Button>
              <Button variant="secondary" onClick={() => submit.mutate()} disabled={dirty || submit.isPending} title={dirty ? "Save first" : undefined}>
                Submit for review
              </Button>
              <Button variant="danger" onClick={() => setDialog("discard")}>
                Discard
              </Button>
            </div>
          </div>
        ) : yaml ? (
          <pre className="overflow-auto text-xs">{version.content}</pre>
        ) : (
          <Markdown>{version.content ?? ""}</Markdown>
        )}
      </Card>

      {status === "IN_REVIEW" && canReview && (
        <div className="flex gap-2">
          <Button onClick={() => setDialog("approve")}>Approve</Button>
          <Button variant="secondary" onClick={() => setDialog("reject")}>
            Reject
          </Button>
        </div>
      )}

      <Card>
        <h2 className="mb-2 font-semibold">Comments</h2>
        {comments.data?.length === 0 && <p className="text-sm text-slate-500">No comments yet.</p>}
        <ul className="space-y-2">
          {comments.data?.map((c) => (
            <li key={c.id} className="rounded border border-slate-200 p-2 text-sm">
              <div className="text-xs text-slate-500">
                {c.authorType} · {formatDate(c.createdAt)}
              </div>
              <Markdown>{c.body ?? ""}</Markdown>
            </li>
          ))}
        </ul>
        {isOwner && (
          <form
            className="mt-3 flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              addComment.mutate({ body: comment.trim() }, { onSuccess: () => setComment("") });
            }}
          >
            <Input aria-label="New comment" value={comment} onChange={(e) => setComment(e.target.value)} />
            <Button type="submit" disabled={!comment.trim() || addComment.isPending}>
              Add
            </Button>
          </form>
        )}
        <ErrorBox error={addComment.error} />
      </Card>

      {dialog === "approve" && doc.data && (
        <ApproveDialog
          projectId={projectId}
          versionId={versionId}
          documentTitle={doc.data.title ?? ""}
          documentType={doc.data.type ?? ""}
          versionNo={version.versionNo ?? 0}
          displayedHash={version.contentHash ?? ""}
          onClose={() => setDialog(null)}
          onStale={() => {
            setStaleBanner(true);
            vq.refetch();
          }}
        />
      )}
      {dialog === "reject" && (
        <Dialog title={`Reject v${version.versionNo}?`} onClose={() => setDialog(null)}>
          <Field label="Comment (required)">
            <Textarea rows={3} value={rejectComment} onChange={(e) => setRejectComment(e.target.value)} />
          </Field>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDialog(null)}>
              Cancel
            </Button>
            <Button variant="danger" disabled={!rejectComment.trim() || reject.isPending} onClick={() => reject.mutate({ comment: rejectComment.trim() }, { onSuccess: () => setDialog(null) })}>
              Reject
            </Button>
          </div>
        </Dialog>
      )}
      {dialog === "discard" && (
        <Dialog title={`Discard draft v${version.versionNo}?`} onClose={() => setDialog(null)}>
          <p className="text-sm">The draft is kept in history as DISCARDED and can no longer be edited.</p>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDialog(null)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={() => discard.mutate(undefined, { onSuccess: () => { setDraft(null); setSummary(null); setDialog(null); } })}>
              Discard
            </Button>
          </div>
        </Dialog>
      )}
    </div>
  );
}
