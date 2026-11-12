"use client";

import Link from "next/link";
import { useState } from "react";
import { useCreateProject, useProjects } from "@/features/project/api";
import { Button, Card, Empty, ErrorBox, Field, Input, Loading, Textarea } from "@/components/ui";
import { formatDate } from "@/lib/format";

export default function ProjectsPage() {
  const projects = useProjects();
  const create = useCreateProject();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [budget, setBudget] = useState("");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    create.mutate(
      { name: name.trim(), description: description.trim() || undefined, dailyTokenBudget: budget ? Number(budget) : undefined },
      {
        onSuccess: () => {
          setName("");
          setDescription("");
          setBudget("");
        },
      },
    );
  };

  return (
    <main className="mx-auto max-w-4xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold">Projects</h1>
      {projects.isPending && <Loading what="Loading projects" />}
      <ErrorBox error={projects.error} />
      {projects.data && projects.data.length === 0 && (
        <Empty>
          No projects yet. Create one below, then add a credential, write and approve your requirements, and create an agent to chat with.
        </Empty>
      )}
      <ul className="grid gap-3 sm:grid-cols-2">
        {projects.data?.map((p) => (
          <li key={p.id}>
            <Link href={`/projects/${p.id}`}>
              <Card className="hover:border-indigo-400">
                <div className="font-medium">{p.name}</div>
                <div className="text-sm text-slate-600">{p.description}</div>
                <div className="mt-2 text-xs text-slate-500">
                  {p.myRole} · created {formatDate(p.createdAt)}
                </div>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
      <Card>
        <h2 className="mb-3 font-semibold">New project</h2>
        <form onSubmit={submit} className="space-y-3">
          <Field label="Name">
            <Input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Description">
            <Textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field label="Daily token budget (optional)" hint="Runs are blocked once today's input + output tokens would exceed it.">
            <Input type="number" min={1} value={budget} onChange={(e) => setBudget(e.target.value)} />
          </Field>
          <ErrorBox error={create.error} />
          <Button type="submit" disabled={create.isPending || !name.trim()}>
            Create project
          </Button>
        </form>
      </Card>
    </main>
  );
}
