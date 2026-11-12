"use client";

import { useParams } from "next/navigation";
import { useState } from "react";
import { Banner, Button, Card, Dialog, Empty, ErrorBox, Field, Input, Loading, Mono, Select } from "@/components/ui";
import { useContextPreview, useCreateAgent, useProjectAgents, useUpdateAgent } from "@/features/agent/api";
import { useCredentials } from "@/features/credential/api";
import { useProject } from "@/features/project/api";
import { useRoles } from "@/features/role/api";
import { shortHash } from "@/lib/format";
import type { Agent } from "@/types";

function Preview({ agent, onClose }: { agent: Agent; onClose: () => void }) {
  const q = useContextPreview(agent.id!, true);
  return (
    <Dialog title={`Context preview - ${agent.name}`} onClose={onClose}>
      {q.isPending && <Loading />}
      <ErrorBox error={q.error} />
      {q.data && (
        <div className="space-y-3 text-sm">
          <p>Approved documents this agent would receive now (estimate {q.data.tokenEstimate} tokens):</p>
          {q.data.items?.length ? (
            <ul className="list-disc pl-5">
              {q.data.items.map((i) => (
                <li key={i.versionId}>
                  {i.documentType} v{i.versionNo} <Mono>{shortHash(i.contentHash)}</Mono> ({i.mode})
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-slate-500">No approved documents yet.</p>
          )}
          {!!q.data.missingDocumentTypes?.length && <p className="text-slate-600">Not available yet: {q.data.missingDocumentTypes.join(", ")}</p>}
          {!!q.data.blockers?.length && <Banner tone="danger">Blockers: {q.data.blockers.map((b) => `${b.reason}${b.documentType ? ` (${b.documentType})` : ""}`).join(", ")}</Banner>}
        </div>
      )}
      <div className="mt-4 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Dialog>
  );
}

function AgentRow({ agent, projectId, owner }: { agent: Agent; projectId: string; owner: boolean }) {
  const creds = useCredentials(projectId);
  const update = useUpdateAgent(projectId, agent.id!);
  const [preview, setPreview] = useState(false);
  const active = creds.data?.filter((c) => !c.revokedAt) ?? [];
  const current = creds.data?.find((c) => c.id === agent.credentialId);
  return (
    <Card className="space-y-2">
      <div className="flex items-center justify-between">
        <div>
          <div className="font-medium">{agent.name}</div>
          <div className="text-xs text-slate-500">
            {agent.provider} · {agent.executorType} · {agent.model}
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setPreview(true)}>
            Context preview
          </Button>
          {owner && (
            <Button variant="secondary" disabled={update.isPending} onClick={() => update.mutate({ isEnabled: !agent.isEnabled })}>
              {agent.isEnabled ? "Disable" : "Enable"}
            </Button>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2 text-sm">
        <span className="text-slate-600">Credential:</span>
        {owner ? (
          <Select aria-label={`Credential for ${agent.name}`} className="w-64" value={agent.credentialId ?? ""} onChange={(e) => e.target.value && update.mutate({ credentialId: e.target.value })}>
            <option value="">{agent.credentialId ? "(unknown)" : "None - runs will be blocked"}</option>
            {current?.revokedAt && <option value={current.id}>{current.label} (revoked)</option>}
            {active.map((c) => (
              <option key={c.id} value={c.id}>
                {c.label} ...{c.keyLast4}
              </option>
            ))}
          </Select>
        ) : (
          <span>{current?.label ?? "none"}</span>
        )}
        {!agent.isEnabled && <span className="text-amber-700">disabled</span>}
      </div>
      <ErrorBox error={update.error} />
      {preview && <Preview agent={agent} onClose={() => setPreview(false)} />}
    </Card>
  );
}

export default function AgentsPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const project = useProject(projectId);
  const agents = useProjectAgents(projectId);
  const roles = useRoles(projectId);
  const creds = useCredentials(projectId);
  const create = useCreateAgent(projectId);
  const owner = project.data?.myRole === "OWNER";
  const [form, setForm] = useState({ roleId: "", name: "", model: "claude-sonnet-5-5", credentialId: "", maxOutputTokens: "", maxRunSeconds: "", maxContextTokens: "" });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const num = (s: string) => (s ? Number(s) : undefined);
    create.mutate(
      {
        roleId: form.roleId,
        name: form.name.trim(),
        provider: "ANTHROPIC",
        model: form.model.trim(),
        credentialId: form.credentialId || undefined,
        params: { maxOutputTokens: num(form.maxOutputTokens), maxRunSeconds: num(form.maxRunSeconds), maxContextTokens: num(form.maxContextTokens) },
      },
      { onSuccess: () => setForm({ ...form, name: "" }) },
    );
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Agents</h1>
      {agents.isPending && <Loading />}
      <ErrorBox error={agents.error} />
      {agents.data?.length === 0 && <Empty>No agents yet. An agent plays a role with a model and a credential.</Empty>}
      <div className="space-y-3">{agents.data?.map((a) => <AgentRow key={a.id} agent={a} projectId={projectId} owner={owner} />)}</div>
      {owner && (
        <Card>
          <h2 className="mb-3 font-semibold">New agent</h2>
          <form onSubmit={submit} className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Role" hint="Cannot be changed later.">
                <Select required value={form.roleId} onChange={(e) => setForm({ ...form, roleId: e.target.value })}>
                  <option value="">Select a role...</option>
                  {roles.data?.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Name">
                <Input required maxLength={100} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </Field>
              <Field label="Provider" hint="Cannot be changed later.">
                <Input value="ANTHROPIC" readOnly />
              </Field>
              <Field label="Model">
                <Input required maxLength={100} value={form.model} onChange={(e) => setForm({ ...form, model: e.target.value })} />
              </Field>
              <Field label="Credential">
                <Select value={form.credentialId} onChange={(e) => setForm({ ...form, credentialId: e.target.value })}>
                  <option value="">None (add one later)</option>
                  {creds.data?.filter((c) => !c.revokedAt).map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.label} ...{c.keyLast4}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="Max output tokens">
                <Input type="number" min={1} value={form.maxOutputTokens} onChange={(e) => setForm({ ...form, maxOutputTokens: e.target.value })} />
              </Field>
              <Field label="Max run seconds">
                <Input type="number" min={1} value={form.maxRunSeconds} onChange={(e) => setForm({ ...form, maxRunSeconds: e.target.value })} />
              </Field>
              <Field label="Max context tokens">
                <Input type="number" min={1000} value={form.maxContextTokens} onChange={(e) => setForm({ ...form, maxContextTokens: e.target.value })} />
              </Field>
            </div>
            <ErrorBox error={create.error} />
            <Button type="submit" disabled={create.isPending || !form.roleId || !form.name.trim()}>
              Create agent
            </Button>
          </form>
        </Card>
      )}
    </div>
  );
}
