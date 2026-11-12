"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, listAll } from "@/lib/api-client";
import type { ConfigVersion, ContextProfile, Permission, Role } from "@/types";

export const useRoles = (pid: string) => useQuery({ queryKey: ["roles", pid], queryFn: () => listAll<Role>(`/projects/${pid}/roles`) });

export const useRole = (rid: string) => useQuery({ queryKey: ["role", rid], queryFn: () => api.get<Role>(`/roles/${rid}`) });

export const useConfigVersions = (rid: string) =>
  useQuery({ queryKey: ["config-versions", rid], queryFn: () => listAll<ConfigVersion>(`/roles/${rid}/config-versions`) });

export function useCreateConfigVersion(rid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { systemPrompt: string; contextProfile: ContextProfile; permissions: Permission[] }) =>
      api.post<ConfigVersion>(`/roles/${rid}/config-versions`, v),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["config-versions", rid] }),
  });
}

export function useActivateConfigVersion(rid: string, pid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (versionNo: number) => api.post<ConfigVersion>(`/roles/${rid}/config-versions/${versionNo}/activate`),
    onSuccess: () => {
      for (const k of [["config-versions", rid], ["role", rid], ["roles", pid]]) qc.invalidateQueries({ queryKey: k });
    },
  });
}
