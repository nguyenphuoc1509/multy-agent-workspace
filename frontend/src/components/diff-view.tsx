"use client";

import { diffLines } from "diff";
import { cx } from "./ui";

/** Client-side line diff of two version bodies (there is no diff endpoint). */
export function DiffView({ from, to }: { from: string; to: string }) {
  const parts = diffLines(from, to);
  return (
    <pre className="overflow-auto rounded border border-slate-200 bg-white text-xs" aria-label="Diff">
      {parts.map((p, i) => {
        const lines = p.value.replace(/\n$/, "").split("\n");
        return lines.map((l, j) => (
          <div key={`${i}-${j}`} className={cx("px-2", p.added && "bg-emerald-50 text-emerald-900", p.removed && "bg-red-50 text-red-900")}>
            <span className="select-none pr-2 text-slate-400">{p.added ? "+" : p.removed ? "-" : " "}</span>
            {l}
          </div>
        ));
      })}
    </pre>
  );
}
