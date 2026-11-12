"use client";

import Link from "next/link";
import { useState } from "react";
import { Banner, Button, Dialog, ErrorBox, Field, Input, Mono } from "@/components/ui";
import { useApproveVersion } from "@/features/document/api";
import { ApiError } from "@/lib/api-client";
import { shortHash } from "@/lib/format";

interface Props {
  projectId: string;
  versionId: string;
  documentTitle: string;
  documentType: string;
  versionNo: number;
  /** Hash of the content currently displayed (R1): this is what gets approved. */
  displayedHash: string;
  onClose: () => void;
  onStale: () => void;
}

export function ApproveDialog({ projectId, versionId, documentTitle, documentType, versionNo, displayedHash, onClose, onStale }: Props) {
  const approve = useApproveVersion(versionId);
  const [comment, setComment] = useState("");
  const err = approve.error;

  const confirm = () =>
    approve.mutate(
      { expectedHash: displayedHash, comment: comment.trim() || undefined },
      {
        onSuccess: onClose,
        onError: (e) => e instanceof ApiError && e.code === "STALE_CONTENT" && onStale(),
      },
    );

  return (
    <Dialog title="Approve this version?" onClose={onClose}>
      <p className="mb-2 text-sm">
        You are approving <strong>{documentTitle}</strong> ({documentType}) <strong>v{versionNo}</strong>, content hash <Mono>{shortHash(displayedHash)}</Mono>.
        It becomes the official version agents read.
      </p>
      <Field label="Comment (optional)">
        <Input value={comment} onChange={(e) => setComment(e.target.value)} />
      </Field>
      <div className="mt-3 space-y-2">
        {err instanceof ApiError && err.code === "GATE_NOT_SATISFIED" && (
          <Banner tone="danger">
            A prerequisite document has no approved version:
            <ul className="ml-4 list-disc">
              {((err.extra.missing as Array<{ documentType: string }>) ?? []).map((m) => (
                <li key={m.documentType}>
                  <Link className="underline" href={`/projects/${projectId}/documents?type=${m.documentType}`}>
                    {m.documentType}
                  </Link>
                </li>
              ))}
            </ul>
          </Banner>
        )}
        {err instanceof ApiError && err.code === "STALE_CONTENT" && (
          <Banner tone="warn">The content changed since you opened it. It was reloaded - read it again before approving.</Banner>
        )}
        {!(err instanceof ApiError && (err.code === "GATE_NOT_SATISFIED" || err.code === "STALE_CONTENT")) && <ErrorBox error={err} />}
      </div>
      <div className="mt-4 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button onClick={confirm} disabled={approve.isPending}>
          Approve v{versionNo}
        </Button>
      </div>
    </Dialog>
  );
}
