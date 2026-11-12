"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Card, Empty, ErrorBox, Loading } from "@/components/ui";
import { useRoles } from "@/features/role/api";

export default function RolesPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const roles = useRoles(projectId);
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Roles</h1>
      <p className="text-sm text-slate-600">A role defines what an agent does, what it is allowed to do and which approved documents it sees. Several agents can share a role.</p>
      {roles.isPending && <Loading />}
      <ErrorBox error={roles.error} />
      {roles.data?.length === 0 && <Empty>No roles.</Empty>}
      <ul className="grid gap-3 sm:grid-cols-2">
        {roles.data?.map((r) => (
          <li key={r.id}>
            <Link href={`/projects/${projectId}/roles/${r.id}`}>
              <Card className="hover:border-indigo-400">
                <div className="font-medium">{r.name}</div>
                <div className="text-xs text-slate-500">
                  {r.code} · {r.isBuiltin ? "built-in" : "custom"} · active config {r.activeConfigVersion ? `v${r.activeConfigVersion.versionNo}` : "none"}
                </div>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
