"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { Button, Card, Empty, ErrorBox, Field, Input, Loading, Select } from "@/components/ui";
import { useProjectAgents } from "@/features/agent/api";
import { useConversations, useCreateConversation } from "@/features/chat/api";
import { useProject } from "@/features/project/api";
import { formatDate } from "@/lib/format";

export default function ChatListPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const router = useRouter();
  const project = useProject(projectId);
  const convs = useConversations(projectId);
  const agents = useProjectAgents(projectId);
  const create = useCreateConversation(projectId);
  const [agentId, setAgentId] = useState("");
  const [title, setTitle] = useState("");
  const owner = project.data?.myRole === "OWNER";
  const agentName = (id?: string) => agents.data?.find((a) => a.id === id)?.name ?? "agent";

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Chat</h1>
      <p className="text-sm text-slate-600">Conversations are not official documents. Promote an agent message to a draft if you want to keep it.</p>
      {convs.isPending && <Loading />}
      <ErrorBox error={convs.error} />
      {convs.data?.length === 0 && <Empty>No conversations yet. Pick an agent below to start one.</Empty>}
      <ul className="space-y-2">
        {convs.data?.map((c) => (
          <li key={c.id}>
            <Link href={`/projects/${projectId}/chat/${c.id}`}>
              <Card className="flex items-center justify-between hover:border-indigo-400">
                <div>
                  <div className="font-medium">{c.title}</div>
                  <div className="text-xs text-slate-500">
                    {agentName(c.agentId)} · {c.lastSeq} messages · {formatDate(c.createdAt)}
                  </div>
                </div>
                <div className="flex gap-2 text-xs">
                  {c.activeRunId && <span className="rounded bg-sky-100 px-2 py-0.5 text-sky-800">replying</span>}
                  {c.stale && (
                    <span className="rounded bg-amber-100 px-2 py-0.5 text-amber-800" title={(c.staleDocumentTypes ?? []).join(", ")}>
                      stale: {(c.staleDocumentTypes ?? []).join(", ")}
                    </span>
                  )}
                </div>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
      {owner && (
        <Card>
          <h2 className="mb-3 font-semibold">New conversation</h2>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              create.mutate({ agentId, title: title.trim() || undefined }, { onSuccess: (c) => router.push(`/projects/${projectId}/chat/${c.id}`) });
            }}
          >
            <Field label="Agent">
              <Select required value={agentId} onChange={(e) => setAgentId(e.target.value)}>
                <option value="">Select an agent...</option>
                {agents.data?.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} ({a.model}){a.isEnabled ? "" : " - disabled"}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Title (optional)">
              <Input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
            </Field>
            <ErrorBox error={create.error} />
            <Button type="submit" disabled={!agentId || create.isPending}>
              Start conversation
            </Button>
          </form>
        </Card>
      )}
    </div>
  );
}
