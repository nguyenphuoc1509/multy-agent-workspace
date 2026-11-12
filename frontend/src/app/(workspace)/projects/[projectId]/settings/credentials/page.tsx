"use client";

import { useParams } from "next/navigation";
import { useState } from "react";
import { Banner, Button, Card, Dialog, Empty, ErrorBox, Loading } from "@/components/ui";
import { AddCredentialForm } from "@/features/credential/add-credential-form";
import { useCredentials, useRevokeCredential } from "@/features/credential/api";
import { useProject } from "@/features/project/api";
import { formatDate } from "@/lib/format";
import type { Credential } from "@/types";

export default function CredentialsPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const project = useProject(projectId);
  const creds = useCredentials(projectId);
  const revoke = useRevokeCredential(projectId);
  const [target, setTarget] = useState<Credential | null>(null);

  if (project.data && project.data.myRole !== "OWNER") return <Banner tone="warn">Only the project owner can manage credentials.</Banner>;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Credentials</h1>
      {creds.isPending && <Loading />}
      <ErrorBox error={creds.error} />
      {creds.data?.length === 0 && <Empty>No credentials yet. Add an API key so agents can call the provider.</Empty>}
      <ul className="space-y-2">
        {creds.data?.map((c) => (
          <li key={c.id}>
            <Card className="flex items-center justify-between">
              <div>
                <div className="font-medium">{c.label}</div>
                <div className="text-sm text-slate-500">
                  {c.provider} · ends with <code className="font-mono">{c.keyLast4}</code> · added {formatDate(c.createdAt)}
                </div>
              </div>
              {c.revokedAt ? (
                <span className="text-sm text-slate-500">Revoked {formatDate(c.revokedAt)}</span>
              ) : (
                <Button variant="danger" onClick={() => setTarget(c)}>
                  Revoke
                </Button>
              )}
            </Card>
          </li>
        ))}
      </ul>
      <Card>
        <h2 className="mb-3 font-semibold">Add credential</h2>
        <AddCredentialForm projectId={projectId} />
      </Card>
      {target && (
        <Dialog title="Revoke credential?" onClose={() => setTarget(null)}>
          <p className="text-sm">
            Revoke <strong>{target.label}</strong> (ends with {target.keyLast4}). Agents using it will be blocked until you pick another credential. This cannot be undone.
          </p>
          <ErrorBox error={revoke.error} />
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setTarget(null)}>
              Cancel
            </Button>
            <Button variant="danger" disabled={revoke.isPending} onClick={() => revoke.mutate(target.id!, { onSuccess: () => setTarget(null) })}>
              Revoke
            </Button>
          </div>
        </Dialog>
      )}
    </div>
  );
}
