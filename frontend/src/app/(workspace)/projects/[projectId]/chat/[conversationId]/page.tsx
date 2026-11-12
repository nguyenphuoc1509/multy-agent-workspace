"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Markdown } from "@/components/markdown";
import { Banner, Button, Card, ErrorBox, Loading, Textarea } from "@/components/ui";
import { sendMessage, useConversation, useMessages, useRunActions } from "@/features/chat/api";
import { blockHelp } from "@/features/chat/block-help";
import { PromoteDialog } from "@/features/chat/promote-dialog";
import { useRunStream } from "@/features/chat/use-run-stream";
import { useProject } from "@/features/project/api";
import { ApiError } from "@/lib/api-client";
import { newRequestId } from "@/lib/format";
import type { Message } from "@/types";

export default function ConversationPage() {
  const { projectId, conversationId } = useParams<{ projectId: string; conversationId: string }>();
  const project = useProject(projectId);
  const conv = useConversation(conversationId);
  const messages = useMessages(conversationId);
  const actions = useRunActions(conversationId, projectId);
  const { state, attach, reset } = useRunStream(() => actions.refresh());
  const [text, setText] = useState("");
  const [pendingMsg, setPendingMsg] = useState<{ content: string; afterSeq: number } | null>(null); // optimistic USER message
  const [sendError, setSendError] = useState<unknown>(null);
  const [sending, setSending] = useState(false);
  const [promote, setPromote] = useState<Message | null>(null);
  const requestId = useRef<{ content: string; id: string } | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const owner = project.data?.myRole === "OWNER";
  const activeRunId = conv.data?.activeRunId ?? null;
  const streaming = state.phase === "streaming";

  const lastSeq = messages.data?.at(-1)?.seq ?? 0;
  // Shown until the saved USER message (a higher seq than when we sent) arrives from REST.
  const pending =
    pendingMsg && !messages.data?.some((m) => m.role === "USER" && m.content === pendingMsg.content && (m.seq ?? 0) > pendingMsg.afterSeq)
      ? pendingMsg.content
      : null;

  // Re-attach after a reload / when another tab started a run.
  useEffect(() => {
    if (activeRunId && state.runId !== activeRunId && state.phase !== "streaming") attach(activeRunId);
  }, [activeRunId, state.runId, state.phase, attach]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages.data?.length, state.text, pending]);

  const send = async () => {
    const content = text.trim();
    if (!content || sending) return;
    // The same submit keeps its clientRequestId when retried, so a duplicate never creates a second message.
    if (requestId.current?.content !== content) requestId.current = { content, id: newRequestId() };
    setSending(true);
    setSendError(null);
    reset();
    setPendingMsg({ content, afterSeq: lastSeq });
    try {
      const res = await sendMessage(conversationId, content, requestId.current.id);
      requestId.current = null;
      setText("");
      if (res.run?.id) attach(res.run.id);
      actions.refresh();
    } catch (e) {
      setSendError(e); // the typed text stays in the box
      setPendingMsg(null);
      if (e instanceof ApiError && e.code === "ACTIVE_RUN_EXISTS") conv.refetch();
    } finally {
      setSending(false);
    }
  };

  if (conv.isPending) return <Loading />;
  if (conv.error) return <ErrorBox error={conv.error} />;

  const failedRun = state.phase === "failed" && state.code !== "CANCELLED";
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-3">
      <div>
        <Link href={`/projects/${projectId}/chat`} className="text-xs text-slate-500 hover:underline">
          All conversations
        </Link>
        <h1 className="text-xl font-semibold">{conv.data?.title}</h1>
      </div>
      <Banner>Conversation is not an official document.</Banner>
      {conv.data?.stale && (
        <Banner tone="warn">
          The last answer used documents that have changed since: {(conv.data.staleDocumentTypes ?? []).join(", ")}. Ask again to use the latest approved versions.
        </Banner>
      )}

      <div className="space-y-3" aria-live="polite">
        {messages.isPending && <Loading />}
        {messages.data?.length === 0 && !pending && <p className="text-sm text-slate-500">No messages yet. Say something to the agent.</p>}
        {messages.data?.map((m) => (
          <Card key={m.id} className={m.role === "USER" ? "ml-10 bg-indigo-50" : "mr-10"}>
            <div className="mb-1 flex items-center justify-between text-xs text-slate-500">
              <span>{m.role}</span>
              {m.role === "AGENT" && (
                <span className="flex gap-3">
                  {m.agentRunId && (
                    <Link className="text-indigo-700 hover:underline" href={`/projects/${projectId}/runs/${m.agentRunId}`}>
                      Run &amp; context
                    </Link>
                  )}
                  {owner && (
                    <button className="text-indigo-700 hover:underline" onClick={() => setPromote(m)}>
                      Promote to draft
                    </button>
                  )}
                </span>
              )}
            </div>
            {m.role === "AGENT" ? <Markdown>{m.content ?? ""}</Markdown> : <p className="whitespace-pre-wrap break-words text-sm">{m.content}</p>}
          </Card>
        ))}
        {pending !== null && (
          <Card className="ml-10 bg-indigo-50 opacity-70">
            <div className="mb-1 text-xs text-slate-500">USER (sending...)</div>
            <p className="whitespace-pre-wrap break-words text-sm">{pending}</p>
          </Card>
        )}
        {streaming && (
          <Card className="mr-10 border-dashed">
            <div className="mb-1 text-xs text-slate-500">AGENT (streaming - provisional)</div>
            {state.text ? <Markdown>{state.text}</Markdown> : <p className="text-sm text-slate-500">Thinking...</p>}
          </Card>
        )}
        <div ref={endRef} />
      </div>

      {state.phase === "blocked" && (
        <Banner tone="warn">
          Run blocked: {blockHelp(state.code ?? "", projectId).text}{" "}
          <Link className="underline" href={blockHelp(state.code ?? "", projectId).href}>
            Fix it
          </Link>
          {state.runId && owner && (
            <Button variant="secondary" className="ml-2" onClick={() => actions.retry.mutate(state.runId!, { onSuccess: (r) => r.id && attach(r.id) })}>
              Retry
            </Button>
          )}
        </Banner>
      )}
      {failedRun && (
        <Banner tone="danger">
          The run failed ({state.code}).
          {state.runId && owner && (
            <Button variant="secondary" className="ml-2" onClick={() => actions.retry.mutate(state.runId!, { onSuccess: (r) => r.id && attach(r.id) })}>
              Retry
            </Button>
          )}
        </Banner>
      )}
      {state.phase === "failed" && state.code === "CANCELLED" && <Banner>Run cancelled.</Banner>}
      {state.phase === "lost" && <Banner tone="warn">Lost the live connection. The saved result is shown after a refresh.</Banner>}
      <ErrorBox error={actions.retry.error ?? actions.cancel.error} />

      {owner ? (
        <form
          className="space-y-2"
          onSubmit={(e) => {
            e.preventDefault();
            void send();
          }}
        >
          {sendError instanceof ApiError && sendError.code === "ACTIVE_RUN_EXISTS" ? (
            <Banner tone="warn">The agent is still replying. Wait for it to finish (or cancel it).</Banner>
          ) : (
            <ErrorBox error={sendError} />
          )}
          <Textarea
            aria-label="Message"
            rows={3}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) void send();
            }}
            placeholder="Message the agent (Ctrl+Enter to send)"
          />
          <div className="flex items-center gap-2">
            <Button type="submit" disabled={!text.trim() || sending || streaming || !!activeRunId}>
              {streaming || activeRunId ? "Agent is replying..." : "Send"}
            </Button>
            {(streaming || activeRunId) && (state.runId ?? activeRunId) && (
              <Button type="button" variant="secondary" onClick={() => actions.cancel.mutate((state.runId ?? activeRunId)!)}>
                Cancel
              </Button>
            )}
            <span className="text-xs text-slate-500">{lastSeq} messages</span>
          </div>
        </form>
      ) : (
        <Banner>You can read this conversation but only the project owner can send messages.</Banner>
      )}

      {promote && <PromoteDialog projectId={projectId} conversationId={conversationId} message={promote} onClose={() => setPromote(null)} />}
    </div>
  );
}
