"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { openRunStream } from "@/lib/sse";

export type StreamPhase = "idle" | "streaming" | "completed" | "failed" | "blocked" | "lost";

export interface StreamState {
  runId: string | null;
  phase: StreamPhase;
  text: string;
  code?: string;
  stale?: boolean;
}

const IDLE: StreamState = { runId: null, phase: "idle", text: "" };

/**
 * Attaches to a run's SSE stream. Streamed text is provisional: when the run ends, onFinished lets the
 * caller reload the saved messages over REST, which replace it.
 */
export function useRunStream(onFinished: (s: StreamState) => void) {
  const [state, setState] = useState<StreamState>(IDLE);
  const closeRef = useRef<(() => void) | null>(null);
  const finished = useRef(onFinished);
  useEffect(() => {
    finished.current = onFinished;
  });

  const detach = useCallback(() => {
    closeRef.current?.();
    closeRef.current = null;
  }, []);

  const attach = useCallback(
    (runId: string) => {
      detach();
      setState({ runId, phase: "streaming", text: "" });
      const end = (patch: Partial<StreamState>) =>
        setState((s) => {
          const next = { ...s, ...patch };
          queueMicrotask(() => finished.current(next));
          return next;
        });
      closeRef.current = openRunStream(runId, {
        onDelta: (t) => setState((s) => ({ ...s, text: s.text + t })),
        onCompleted: (d) => end({ phase: "completed", stale: d.stale }),
        onFailed: (code) => end({ phase: "failed", code }),
        onBlocked: (reason) => end({ phase: "blocked", code: reason }),
        onGiveUp: () => end({ phase: "lost" }),
      });
    },
    [detach],
  );

  const reset = useCallback(() => {
    detach();
    setState(IDLE);
  }, [detach]);

  useEffect(() => detach, [detach]);
  return { state, attach, reset };
}
