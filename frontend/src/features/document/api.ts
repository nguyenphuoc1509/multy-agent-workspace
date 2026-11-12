"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, listAll, request } from "@/lib/api-client";
import type { Comment, Decision, Doc, DocType, Version, VersionSummary } from "@/types";

export const useDocument = (did: string) =>
  useQuery({ queryKey: ["document", did], queryFn: () => api.get<Doc>(`/documents/${did}`), enabled: !!did });

export const useVersions = (did: string) =>
  useQuery({ queryKey: ["versions", did], queryFn: () => listAll<VersionSummary>(`/documents/${did}/versions`) });

/** The ETag (= content hash) travels with the body; PATCH needs it as If-Match. */
export const useVersion = (vid: string) =>
  useQuery({
    queryKey: ["version", vid],
    queryFn: () => request<Version>(`/document-versions/${vid}`).then((r) => ({ version: r.data, etag: r.etag })),
  });

export const useComments = (vid: string) =>
  useQuery({ queryKey: ["comments", vid], queryFn: () => listAll<Comment>(`/document-versions/${vid}/comments`) });

function useInvalidate() {
  const qc = useQueryClient();
  return (v?: { documentId?: string; id?: string }, pid?: string) => {
    if (v?.documentId) {
      qc.invalidateQueries({ queryKey: ["document", v.documentId] });
      qc.invalidateQueries({ queryKey: ["versions", v.documentId] });
    }
    if (v?.id) qc.invalidateQueries({ queryKey: ["version", v.id] });
    qc.invalidateQueries({ queryKey: ["documents"] });
    if (pid) qc.invalidateQueries({ queryKey: ["usage", pid] });
  };
}

export function useCreateDocument(pid: string) {
  const inv = useInvalidate();
  return useMutation({
    mutationFn: (v: { type: DocType; title: string; content: string; changeSummary?: string }) =>
      api.post<Doc>(`/projects/${pid}/documents`, v),
    onSuccess: () => inv(),
  });
}

export function useCreateVersion(did: string) {
  const inv = useInvalidate();
  return useMutation({
    mutationFn: (v: { fromVersionId?: string; changeSummary?: string }) => api.post<Version>(`/documents/${did}/versions`, v),
    onSuccess: (r) => inv(r),
  });
}

export function useSaveVersion(vid: string) {
  const inv = useInvalidate();
  return useMutation({
    mutationFn: (v: { content: string; changeSummary?: string; etag: string }) =>
      request<Version>(`/document-versions/${vid}`, {
        method: "PATCH",
        body: { content: v.content, changeSummary: v.changeSummary },
        headers: { "If-Match": v.etag },
      }).then((r) => ({ version: r.data, etag: r.etag })),
    onSuccess: (r) => inv(r.version),
  });
}

export function useSubmitVersion(vid: string) {
  const inv = useInvalidate();
  return useMutation({ mutationFn: () => api.post<Version>(`/document-versions/${vid}/submit`), onSuccess: (v) => inv(v) });
}

export function useDiscardVersion(vid: string) {
  const inv = useInvalidate();
  return useMutation({ mutationFn: () => api.post<Version>(`/document-versions/${vid}/discard`), onSuccess: (v) => inv(v) });
}

export function useApproveVersion(vid: string) {
  const inv = useInvalidate();
  return useMutation({
    // R1: always the hash of the content the user is looking at.
    mutationFn: (v: { expectedHash: string; comment?: string }) => api.post<Decision>(`/document-versions/${vid}/approve`, v),
    onSuccess: (d) => inv(d.version),
    onError: () => inv({ id: vid }),
  });
}

export function useRejectVersion(vid: string) {
  const inv = useInvalidate();
  return useMutation({
    mutationFn: (v: { comment: string }) => api.post<Decision>(`/document-versions/${vid}/reject`, v),
    onSuccess: (d) => inv(d.version),
  });
}

export function useAddComment(vid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { body: string }) => api.post<Comment>(`/document-versions/${vid}/comments`, v),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["comments", vid] }),
  });
}
