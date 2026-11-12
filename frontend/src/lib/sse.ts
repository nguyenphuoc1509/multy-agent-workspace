/** EventSource wrapper for GET /runs/{id}/stream. Deltas are never replayed; reconnect ONCE, then give up. */

export interface CompletedEvent {
  messageId: string;
  seq: number;
  usage: { inputTokens: number; outputTokens: number };
  stale: boolean;
}

export interface RunStreamHandlers {
  onStarted?: (d: { runId: string; snapshotId: string; model: string }) => void;
  onDelta?: (text: string) => void;
  onCompleted?: (d: CompletedEvent) => void;
  onFailed?: (code: string) => void;
  onBlocked?: (reason: string) => void;
  /** Connection lost twice: the caller falls back to REST (reload run + messages). */
  onGiveUp?: () => void;
}

export type EventSourceFactory = (url: string) => EventSource;

export function openRunStream(
  runId: string,
  h: RunStreamHandlers,
  factory: EventSourceFactory = (u) => new EventSource(u),
  reconnectDelayMs = 500,
): () => void {
  let es: EventSource | null = null;
  let reconnected = false;
  let closed = false;
  let timer: ReturnType<typeof setTimeout> | undefined;

  const close = () => {
    closed = true;
    if (timer) clearTimeout(timer);
    es?.close();
  };
  const json = (e: Event) => JSON.parse((e as MessageEvent<string>).data) as Record<string, unknown>;

  const connect = () => {
    es = factory(`/api/v1/runs/${runId}/stream`);
    es.addEventListener("run.started", (e) => h.onStarted?.(json(e) as never));
    es.addEventListener("delta", (e) => h.onDelta?.(String(json(e).text ?? "")));
    es.addEventListener("run.completed", (e) => {
      close();
      h.onCompleted?.(json(e) as unknown as CompletedEvent);
    });
    es.addEventListener("run.failed", (e) => {
      close();
      h.onFailed?.(String(json(e).code ?? "INTERNAL_ERROR"));
    });
    es.addEventListener("run.blocked", (e) => {
      close();
      h.onBlocked?.(String(json(e).blockReason ?? ""));
    });
    es.onerror = () => {
      if (closed) return;
      es?.close();
      if (reconnected) {
        closed = true;
        h.onGiveUp?.();
        return;
      }
      reconnected = true;
      timer = setTimeout(() => !closed && connect(), reconnectDelayMs);
    };
  };

  connect();
  return close;
}
