"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, listAll, request } from "@/lib/api-client";
import type { Conversation, Message, PromoteResponse, Run, SendMessageResponse, DocType } from "@/types";

export const useConversations = (pid: string) =>
  useQuery({ queryKey: ["conversations", pid], queryFn: () => listAll<Conversation>(`/projects/${pid}/conversations`) });

export const useConversation = (cid: string) =>
  useQuery({ queryKey: ["conversation", cid], queryFn: () => api.get<Conversation>(`/conversations/${cid}`) });

/** All messages in seq order, paged with afterSeq. */
async function fetchMessages(cid: string): Promise<Message[]> {
  const out: Message[] = [];
  let after = 0;
  for (let i = 0; i < 100; i++) {
    const page = await api.get<{ items: Message[]; nextCursor?: string | null }>(`/conversations/${cid}/messages`, { afterSeq: after, limit: 200 });
    out.push(...page.items);
    if (!page.nextCursor) break;
    after = Number(page.nextCursor);
  }
  return out;
}

export const useMessages = (cid: string) => useQuery({ queryKey: ["messages", cid], queryFn: () => fetchMessages(cid) });

export function useCreateConversation(pid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { agentId: string; title?: string }) => api.post<Conversation>(`/agents/${v.agentId}/conversations`, { title: v.title }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["conversations", pid] }),
  });
}

/** Idempotent: the caller passes the same clientRequestId when it retries the same submit. */
export function sendMessage(cid: string, content: string, clientRequestId: string) {
  return request<SendMessageResponse>(`/conversations/${cid}/messages`, { method: "POST", body: { content, clientRequestId } }).then((r) => r.data);
}

export function useRunActions(cid: string, pid: string) {
  const qc = useQueryClient();
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["conversation", cid] });
    qc.invalidateQueries({ queryKey: ["messages", cid] });
    qc.invalidateQueries({ queryKey: ["conversations", pid] });
    qc.invalidateQueries({ queryKey: ["usage", pid] });
    qc.invalidateQueries({ queryKey: ["runs", pid] });
  };
  const cancel = useMutation({ mutationFn: (rid: string) => api.post<Run>(`/runs/${rid}/cancel`), onSuccess: refresh });
  const retry = useMutation({ mutationFn: (rid: string) => api.post<Run>(`/runs/${rid}/retry`), onSuccess: refresh });
  return { cancel, retry, refresh };
}

export function usePromote(cid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { messageId: string; documentId?: string; newDocument?: { type: DocType; title: string }; changeSummary?: string }) =>
      api.post<PromoteResponse>(`/messages/${v.messageId}/promote`, {
        target: v.documentId ? { documentId: v.documentId } : { newDocument: v.newDocument },
        changeSummary: v.changeSummary,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["documents"] });
      qc.invalidateQueries({ queryKey: ["messages", cid] });
    },
  });
}
