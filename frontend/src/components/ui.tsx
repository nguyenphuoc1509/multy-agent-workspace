"use client";

import { useEffect, useRef } from "react";
import { describeError } from "@/lib/api-client";

export function cx(...c: Array<string | false | null | undefined>): string {
  return c.filter(Boolean).join(" ");
}

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" };

export function Button({ variant = "primary", className, ...p }: ButtonProps) {
  const styles = {
    primary: "bg-indigo-600 text-white hover:bg-indigo-700",
    secondary: "bg-white text-slate-800 border border-slate-300 hover:bg-slate-100",
    danger: "bg-red-600 text-white hover:bg-red-700",
  }[variant];
  return (
    <button
      {...p}
      className={cx("rounded px-3 py-1.5 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50", styles, className)}
    />
  );
}

export function Card({ className, ...p }: React.HTMLAttributes<HTMLDivElement>) {
  return <div {...p} className={cx("rounded-lg border border-slate-200 bg-white p-4 shadow-sm", className)} />;
}

export function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium text-slate-700">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  );
}

const inputCls = "w-full rounded border border-slate-300 bg-white px-2 py-1.5 text-sm";
export const Input = (p: React.InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={cx(inputCls, p.className)} />;
export const Select = (p: React.SelectHTMLAttributes<HTMLSelectElement>) => <select {...p} className={cx(inputCls, p.className)} />;
export const Textarea = (p: React.TextareaHTMLAttributes<HTMLTextAreaElement>) => (
  <textarea {...p} className={cx(inputCls, p.className)} />
);

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <div role="alert" className="rounded border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-800">
      {describeError(error)}
    </div>
  );
}

export function Banner({ tone = "info", children }: { tone?: "info" | "warn" | "danger"; children: React.ReactNode }) {
  const t = { info: "border-sky-300 bg-sky-50 text-sky-900", warn: "border-amber-300 bg-amber-50 text-amber-900", danger: "border-red-300 bg-red-50 text-red-900" }[tone];
  return (
    <div role="status" className={cx("rounded border px-3 py-2 text-sm", t)}>
      {children}
    </div>
  );
}

export function Loading({ what = "Loading" }: { what?: string }) {
  return (
    <div aria-busy="true" className="animate-pulse text-sm text-slate-500">
      {what}...
    </div>
  );
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <div className="rounded border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">{children}</div>;
}

const TONES: Record<string, string> = {
  APPROVED: "bg-emerald-100 text-emerald-800 border-emerald-300",
  DRAFT: "bg-amber-100 text-amber-800 border-amber-300",
  IN_REVIEW: "bg-sky-100 text-sky-800 border-sky-300",
  REJECTED: "bg-slate-100 text-slate-500 border-slate-300",
  SUPERSEDED: "bg-slate-100 text-slate-500 border-slate-300",
  DISCARDED: "bg-slate-100 text-slate-500 border-slate-300",
  QUEUED: "bg-slate-100 text-slate-700 border-slate-300",
  RUNNING: "bg-sky-100 text-sky-800 border-sky-300",
  SUCCEEDED: "bg-emerald-100 text-emerald-800 border-emerald-300",
  FAILED: "bg-red-100 text-red-800 border-red-300",
  BLOCKED: "bg-amber-100 text-amber-800 border-amber-300",
  CANCELLED: "bg-slate-100 text-slate-500 border-slate-300",
};

export const STATUS_LABEL: Record<string, string> = {
  APPROVED: "Approved (official)",
  DRAFT: "Draft - not official",
  IN_REVIEW: "In review - not official",
  REJECTED: "Rejected",
  SUPERSEDED: "Superseded",
  DISCARDED: "Discarded",
};

export function StatusBadge({ status }: { status: string }) {
  return (
    <span className={cx("inline-block rounded border px-2 py-0.5 text-xs font-medium", TONES[status] ?? "bg-slate-100 text-slate-700 border-slate-300")}>
      {STATUS_LABEL[status] ?? status}
    </span>
  );
}

export function Mono({ children }: { children: React.ReactNode }) {
  return <code className="rounded bg-slate-100 px-1 font-mono text-xs">{children}</code>;
}

/** Accessible modal: Escape closes, focus moves into the dialog. */
export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label={title} className="max-h-[90vh] w-full max-w-lg overflow-auto rounded-lg bg-white p-5 shadow-xl">
        <h2 className="mb-3 text-lg font-semibold">{title}</h2>
        {children}
      </div>
    </div>
  );
}
