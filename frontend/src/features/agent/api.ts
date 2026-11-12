"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, listAll } from "@/lib/api-client";
import type { Agent, AgentParams, ContextPreview } from "@/types";

export const useProjectAgents = (pid: string) =>
  useQuery({ queryKey: ["agents", pid], queryFn: () => listAll<Agent>(`/projects/${pid}/agents`) });

export const useContextPreview = (aid: string, enabled: boolean) =>
  useQuery({ queryKey: ["context-preview", aid], queryFn: () => api.get<ContextPreview>(`/agents/${aid}/context-preview`), enabled });

export function useCreateAgent(pid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { roleId: string; name: string; provider: string; model: string; params?: AgentParams; credentialId?: string }) =>
      api.post<Agent>(`/projects/${pid}/agents`, v),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["agents", pid] }),
  });
}

export function useUpdateAgent(pid: string, aid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { name?: string; model?: string; params?: AgentParams; credentialId?: string; isEnabled?: boolean }) =>
      api.patch<Agent>(`/agents/${aid}`, v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["agents", pid] });
      qc.invalidateQueries({ queryKey: ["context-preview", aid] });
    },
  });
}
