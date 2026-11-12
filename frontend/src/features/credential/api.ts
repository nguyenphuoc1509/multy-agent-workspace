"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, listAll } from "@/lib/api-client";
import type { Credential } from "@/types";

export const useCredentials = (pid: string) =>
  useQuery({ queryKey: ["credentials", pid], queryFn: () => listAll<Credential>(`/projects/${pid}/credentials`) });

export function useAddCredential(pid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { label: string; provider: string; apiKey: string }) => api.post<Credential>(`/projects/${pid}/credentials`, v),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["credentials", pid] }),
  });
}

export function useRevokeCredential(pid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.post<Credential>(`/credentials/${id}/revoke`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["credentials", pid] });
      qc.invalidateQueries({ queryKey: ["agents", pid] });
    },
  });
}
