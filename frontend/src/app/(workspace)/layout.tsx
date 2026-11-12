"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useLogout, useMe } from "@/features/auth/api";
import { onUnauthenticated } from "@/lib/api-client";
import { Button, Loading } from "@/components/ui";

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  const me = useMe();
  const logout = useLogout();
  const router = useRouter();

  useEffect(() => {
    if (me.isError) onUnauthenticated();
  }, [me.isError]);

  if (me.isPending || me.isError) {
    return (
      <div className="p-6">
        <Loading what="Checking session" />
      </div>
    );
  }
  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-2">
        <Link href="/projects" className="font-semibold">
          Multi-AI Workspace
        </Link>
        <div className="flex items-center gap-3 text-sm">
          <span className="text-slate-600">{me.data?.displayName ?? me.data?.email}</span>
          <Button variant="secondary" onClick={() => logout.mutate(undefined, { onSettled: () => router.replace("/login") })}>
            Sign out
          </Button>
        </div>
      </header>
      <div className="flex-1">{children}</div>
    </div>
  );
}
