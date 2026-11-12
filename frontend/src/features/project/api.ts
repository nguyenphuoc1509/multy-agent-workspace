"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, listAll } from "@/lib/api-client";
import type { Doc, Project, Usage } from "@/types";

export const useProjects = () => useQuery({ queryKey: ["projects"], queryFn: () => listAll<Project>("/projects") });

export const useProject = (pid: string) =>
  useQuery({ queryKey: ["project", pid], queryFn: () => api.get<Project>(`/projects/${pid}`) });

export function useCreateProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { name: string; description?: string; dailyTokenBudget?: number }) => api.post<Project>("/projects", v),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["projects"] }),
  });
}

export const useUsage = (pid: string) =>
  useQuery({ queryKey: ["usage", pid], queryFn: () => api.get<Usage>(`/projects/${pid}/usage`), refetchInterval: 30_000 });

export const useProjectDocuments = (pid: string, type?: string) =>
  useQuery({
    queryKey: ["documents", pid, type ?? "all"],
    queryFn: () => listAll<Doc>(`/projects/${pid}/documents`, { type }),
  });
