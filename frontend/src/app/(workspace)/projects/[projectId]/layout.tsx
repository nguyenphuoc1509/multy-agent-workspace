"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { useProject } from "@/features/project/api";
import { cx, ErrorBox, Loading } from "@/components/ui";

const NAV = [
  ["", "Overview"],
  ["/documents", "Documents"],
  ["/chat", "Chat"],
  ["/agents", "Agents"],
  ["/roles", "Roles"],
  ["/runs", "Runs"],
  ["/settings/credentials", "Credentials"],
] as const;

export default function ProjectLayout({ children }: { children: React.ReactNode }) {
  const { projectId } = useParams<{ projectId: string }>();
  const path = usePathname();
  const project = useProject(projectId);
  const base = `/projects/${projectId}`;

  return (
    <div className="flex min-h-[calc(100vh-49px)]">
      <nav aria-label="Project" className="w-52 shrink-0 border-r border-slate-200 bg-white p-3">
        <div className="mb-3">
          <Link href="/projects" className="text-xs text-slate-500 hover:underline">
            All projects
          </Link>
          <div className="truncate font-semibold">{project.data?.name ?? "..."}</div>
          {project.data && <div className="text-xs text-slate-500">You are {project.data.myRole}</div>}
        </div>
        <ul className="space-y-1">
          {NAV.map(([suffix, label]) => {
            const href = base + suffix;
            const active = suffix === "" ? path === base : path.startsWith(href);
            return (
              <li key={label}>
                <Link href={href} aria-current={active ? "page" : undefined} className={cx("block rounded px-2 py-1 text-sm", active ? "bg-indigo-50 font-medium text-indigo-700" : "hover:bg-slate-100")}>
                  {label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <div className="min-w-0 flex-1 p-6">
        {project.isPending && <Loading what="Loading project" />}
        <ErrorBox error={project.error} />
        {project.data && children}
      </div>
    </div>
  );
}
