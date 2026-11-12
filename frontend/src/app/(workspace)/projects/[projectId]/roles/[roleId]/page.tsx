"use client";

import { useParams } from "next/navigation";
import { useState } from "react";
import { Banner, Button, Card, Dialog, ErrorBox, Loading, StatusBadge } from "@/components/ui";
import { useActivateConfigVersion, useConfigVersions, useCreateConfigVersion, useRole } from "@/features/role/api";
import { ConfigForm } from "@/features/role/config-form";
import { useProject } from "@/features/project/api";
import { formatDate } from "@/lib/format";
import type { ConfigVersion } from "@/types";

export default function RolePage() {
  const { projectId, roleId } = useParams<{ projectId: string; roleId: string }>();
  const project = useProject(projectId);
  const role = useRole(roleId);
  const versions = useConfigVersions(roleId);
  const create = useCreateConfigVersion(roleId);
  const activate = useActivateConfigVersion(roleId, projectId);
  const [target, setTarget] = useState<ConfigVersion | null>(null);
  const [formKey, setFormKey] = useState(0);

  if (project.data && project.data.myRole !== "OWNER") return <Banner tone="warn">Only the project owner can manage roles.</Banner>;

  const sorted = versions.data?.slice().sort((a, b) => (b.versionNo ?? 0) - (a.versionNo ?? 0));
  const active = sorted?.find((v) => v.isActive);
  const base = active ?? sorted?.[0];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{role.data?.name ?? "Role"}</h1>
      {(role.isPending || versions.isPending) && <Loading />}
      <ErrorBox error={role.error ?? versions.error} />
      <Card>
        <h2 className="mb-2 font-semibold">Config versions</h2>
        <ul className="divide-y divide-slate-100">
          {sorted?.map((v) => (
            <li key={v.id} className="flex items-center justify-between py-2 text-sm">
              <span>
                v{v.versionNo} · {formatDate(v.createdAt)} {v.isActive ? <StatusBadge status="APPROVED" /> : null}
                {v.isActive && <span className="ml-2 text-xs text-emerald-700">active</span>}
              </span>
              {!v.isActive && (
                <Button variant="secondary" onClick={() => setTarget(v)}>
                  Activate
                </Button>
              )}
            </li>
          ))}
        </ul>
      </Card>
      <Card>
        <h2 className="mb-3 font-semibold">New config version</h2>
        <p className="mb-3 text-sm text-slate-600">Starts from {base ? `v${base.versionNo}` : "empty"}. Saving creates an inactive version; activate it separately.</p>
        {versions.data && (
          <ConfigForm
            key={`${base?.id}-${formKey}`}
            initial={base}
            pending={create.isPending}
            error={create.error}
            onSubmit={(v) => create.mutate(v, { onSuccess: () => setFormKey((k) => k + 1) })}
          />
        )}
      </Card>
      {target && (
        <Dialog title={`Activate ${role.data?.name} v${target.versionNo}?`} onClose={() => setTarget(null)}>
          <p className="text-sm">New runs of every agent with this role will use v{target.versionNo}. Approved outputs based on the previous version are not changed.</p>
          <ErrorBox error={activate.error} />
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setTarget(null)}>
              Cancel
            </Button>
            <Button disabled={activate.isPending} onClick={() => activate.mutate(target.versionNo!, { onSuccess: () => setTarget(null) })}>
              Activate v{target.versionNo}
            </Button>
          </div>
        </Dialog>
      )}
    </div>
  );
}
