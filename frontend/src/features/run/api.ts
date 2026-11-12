"use client";

import { useQuery } from "@tanstack/react-query";
import { api, listAll } from "@/lib/api-client";
import type { Run, Snapshot } from "@/types";

export const useRuns = (pid: string, status?: string) =>
  useQuery({ queryKey: ["runs", pid, status ?? "all"], queryFn: () => listAll<Run>(`/projects/${pid}/runs`, { status }), refetchInterval: 5_000 });

export const useRun = (rid: string) =>
  useQuery({
    queryKey: ["run", rid],
    queryFn: () => api.get<Run>(`/runs/${rid}`),
    refetchInterval: (q) => (q.state.data?.status === "QUEUED" || q.state.data?.status === "RUNNING" ? 2_000 : false),
  });

export const useSnapshot = (rid: string, enabled: boolean) =>
  useQuery({ queryKey: ["snapshot", rid], queryFn: () => api.get<Snapshot>(`/runs/${rid}/snapshot`), enabled });
