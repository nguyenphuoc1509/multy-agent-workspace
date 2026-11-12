"use client";

import Link from "next/link";
import { useState } from "react";
import { Banner, Button, Dialog, ErrorBox, Field, Input, Select } from "@/components/ui";
import { usePromote } from "@/features/chat/api";
import { useProjectDocuments } from "@/features/project/api";
import { ApiError } from "@/lib/api-client";
import { DOC_TYPES, type DocType, type Message } from "@/types";

export function PromoteDialog({ projectId, conversationId, message, onClose }: { projectId: string; conversationId: string; message: Message; onClose: () => void }) {
  const docs = useProjectDocuments(projectId);
  const promote = usePromote(conversationId);
  const [mode, setMode] = useState<"existing" | "new">("new");
  const [documentId, setDocumentId] = useState("");
  const [type, setType] = useState<DocType>("ADR");
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const err = promote.error;
  const done = promote.data;

  const go = () =>
    promote.mutate({
      messageId: message.id!,
      documentId: mode === "existing" ? documentId : undefined,
      newDocument: mode === "new" ? { type, title: title.trim() } : undefined,
      changeSummary: summary.trim() || undefined,
    });

  return (
    <Dialog title="Promote to draft" onClose={onClose}>
      <p className="mb-3 text-sm text-slate-600">
        This copies the message into a new <strong>draft</strong> version. It is not official until you submit and approve it.
      </p>
      {done ? (
        <div className="space-y-3">
          <Banner tone="info">
            Draft v{done.versionNo} created.{" "}
            <Link className="underline" href={`/projects/${projectId}/versions/${done.versionId}`}>
              Open it
            </Link>
          </Banner>
          <div className="flex justify-end">
            <Button onClick={onClose}>Close</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex gap-4 text-sm">
            {(["new", "existing"] as const).map((m) => (
              <label key={m} className="flex items-center gap-1">
                <input type="radio" name="mode" checked={mode === m} onChange={() => setMode(m)} />
                {m === "new" ? "New document" : "Existing document"}
              </label>
            ))}
          </div>
          {mode === "new" ? (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Type">
                <Select value={type} onChange={(e) => setType(e.target.value as DocType)}>
                  {DOC_TYPES.map((t) => (
                    <option key={t}>{t}</option>
                  ))}
                </Select>
              </Field>
              <Field label="Title">
                <Input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
              </Field>
            </div>
          ) : (
            <Field label="Document">
              <Select value={documentId} onChange={(e) => setDocumentId(e.target.value)}>
                <option value="">Select...</option>
                {docs.data?.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.title} ({d.type})
                  </option>
                ))}
              </Select>
            </Field>
          )}
          <Field label="Change summary (optional)">
            <Input value={summary} onChange={(e) => setSummary(e.target.value)} />
          </Field>
          {err instanceof ApiError && err.code === "ROLE_CANNOT_PROPOSE" ? (
            <Banner tone="danger">
              This agent&apos;s role has no PROPOSE_DOCUMENT permission for that document type. Edit the role&apos;s permissions, or pick a different type or an agent with another role.
            </Banner>
          ) : err instanceof ApiError && err.code === "OPEN_VERSION_EXISTS" ? (
            <Banner tone="danger">
              That document already has an open version.{" "}
              <Link className="underline" href={`/projects/${projectId}/versions/${String(err.extra.openVersionId)}`}>
                Open it
              </Link>
            </Banner>
          ) : (
            <ErrorBox error={err} />
          )}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={go} disabled={promote.isPending || (mode === "new" ? !title.trim() : !documentId)}>
              Create draft
            </Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}
