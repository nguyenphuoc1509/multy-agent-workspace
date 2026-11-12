"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useCreateDocument } from "@/features/document/api";
import { useProject, useProjectDocuments } from "@/features/project/api";
import { Button, Card, Empty, ErrorBox, Field, Input, Loading, Select, StatusBadge, Textarea } from "@/components/ui";
import { DOC_TYPES, type DocType } from "@/types";

function Documents() {
  const { projectId } = useParams<{ projectId: string }>();
  const router = useRouter();
  const type = useSearchParams().get("type") ?? undefined;
  const project = useProject(projectId);
  const docs = useProjectDocuments(projectId, type);
  const create = useCreateDocument(projectId);
  const [form, setForm] = useState<{ type: DocType; title: string; content: string; changeSummary: string }>({
    type: "REQUIREMENTS",
    title: "",
    content: "",
    changeSummary: "",
  });
  const canWrite = project.data?.myRole === "OWNER";

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    create.mutate(
      { ...form, title: form.title.trim(), changeSummary: form.changeSummary || undefined },
      { onSuccess: (d) => router.push(`/projects/${projectId}/documents/${d.id}`) },
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Documents</h1>
        <Select aria-label="Filter by type" className="w-48" value={type ?? ""} onChange={(e) => router.push(`/projects/${projectId}/documents${e.target.value ? `?type=${e.target.value}` : ""}`)}>
          <option value="">All types</option>
          {DOC_TYPES.map((t) => (
            <option key={t}>{t}</option>
          ))}
        </Select>
      </div>
      {docs.isPending && <Loading what="Loading documents" />}
      <ErrorBox error={docs.error} />
      {docs.data?.length === 0 && <Empty>No documents yet. Start with REQUIREMENTS: nothing else can be approved before it.</Empty>}
      <ul className="space-y-2">
        {docs.data?.map((d) => (
          <li key={d.id}>
            <Link href={`/projects/${projectId}/documents/${d.id}`}>
              <Card className="flex items-center justify-between hover:border-indigo-400">
                <div>
                  <div className="font-medium">{d.title}</div>
                  <div className="text-xs text-slate-500">{d.type}</div>
                </div>
                <div className="flex gap-2">
                  {d.currentApprovedVersionId ? <StatusBadge status="APPROVED" /> : <span className="text-xs text-slate-500">no approved version</span>}
                  {d.openVersionId && <StatusBadge status="DRAFT" />}
                </div>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
      {canWrite && (
        <Card>
          <h2 className="mb-3 font-semibold">New document</h2>
          <form onSubmit={submit} className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Type">
                <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as DocType })}>
                  {DOC_TYPES.map((t) => (
                    <option key={t}>{t}</option>
                  ))}
                </Select>
              </Field>
              <Field label="Title">
                <Input required maxLength={200} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
              </Field>
            </div>
            <Field label={form.type === "API_CONTRACT" ? "Content (OpenAPI YAML)" : "Content (Markdown)"}>
              <Textarea rows={10} className="font-mono" value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} />
            </Field>
            <Field label="Change summary">
              <Input maxLength={2000} value={form.changeSummary} onChange={(e) => setForm({ ...form, changeSummary: e.target.value })} />
            </Field>
            <ErrorBox error={create.error} />
            <Button type="submit" disabled={create.isPending || !form.title.trim()}>
              Create draft
            </Button>
          </form>
        </Card>
      )}
    </div>
  );
}

export default function DocumentsPage() {
  return (
    <Suspense>
      <Documents />
    </Suspense>
  );
}
