import { describe, expect, it, vi } from "vitest";
import { openRunStream } from "@/lib/sse";

class FakeES {
  listeners = new Map<string, (e: Event) => void>();
  onerror: (() => void) | null = null;
  closed = false;
  addEventListener(name: string, fn: (e: Event) => void) {
    this.listeners.set(name, fn);
  }
  close() {
    this.closed = true;
  }
  emit(name: string, data: unknown) {
    this.listeners.get(name)?.(new MessageEvent(name, { data: JSON.stringify(data) }));
  }
}

function setup() {
  const sources: FakeES[] = [];
  const factory = vi.fn((url: string) => {
    void url;
    const s = new FakeES();
    sources.push(s);
    return s as unknown as EventSource;
  });
  return { sources, factory };
}

describe("openRunStream", () => {
  it("delivers deltas then the completed event and closes", () => {
    const { sources, factory } = setup();
    const h = { onDelta: vi.fn(), onCompleted: vi.fn() };
    openRunStream("r1", h, factory, 0);
    sources[0]!.emit("delta", { text: "Hel" });
    sources[0]!.emit("delta", { text: "lo" });
    sources[0]!.emit("run.completed", { messageId: "m", seq: 2, usage: { inputTokens: 1, outputTokens: 2 }, stale: false });
    expect(h.onDelta.mock.calls.map((c) => c[0])).toEqual(["Hel", "lo"]);
    expect(h.onCompleted).toHaveBeenCalledOnce();
    expect(sources[0]!.closed).toBe(true);
    expect(factory).toHaveBeenCalledWith("/api/v1/runs/r1/stream");
  });

  it("reconnects once after a drop and then gets the terminal event", async () => {
    const { sources, factory } = setup();
    const h = { onFailed: vi.fn(), onGiveUp: vi.fn() };
    openRunStream("r1", h, factory, 0);
    sources[0]!.onerror?.();
    await new Promise((r) => setTimeout(r, 5));
    expect(sources).toHaveLength(2);
    sources[1]!.emit("run.failed", { code: "CANCELLED" });
    expect(h.onFailed).toHaveBeenCalledWith("CANCELLED");
    expect(h.onGiveUp).not.toHaveBeenCalled();
  });

  it("gives up after the second drop", async () => {
    const { sources, factory } = setup();
    const h = { onGiveUp: vi.fn() };
    openRunStream("r1", h, factory, 0);
    sources[0]!.onerror?.();
    await new Promise((r) => setTimeout(r, 5));
    sources[1]!.onerror?.();
    expect(h.onGiveUp).toHaveBeenCalledOnce();
    expect(sources).toHaveLength(2);
  });

  it("reports blocked runs", () => {
    const { sources, factory } = setup();
    const h = { onBlocked: vi.fn() };
    openRunStream("r1", h, factory, 0);
    sources[0]!.emit("run.blocked", { blockReason: "BUDGET_EXCEEDED" });
    expect(h.onBlocked).toHaveBeenCalledWith("BUDGET_EXCEEDED");
  });
});
