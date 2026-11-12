"use client";

import { useState } from "react";
import { Button, ErrorBox, Field, Input } from "@/components/ui";
import { useAddCredential } from "@/features/credential/api";

/**
 * R4: the key is write-only. It lives in this component's state only until submit settles; the mutation
 * is reset afterwards so TanStack Query's mutation cache does not keep the variables either.
 */
export function AddCredentialForm({ projectId }: { projectId: string }) {
  const add = useAddCredential(projectId);
  const [label, setLabel] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [error, setError] = useState<unknown>(null);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const body = { label: label.trim(), provider: "ANTHROPIC", apiKey };
    setApiKey(""); // gone from the DOM immediately, whatever the outcome
    add.mutate(body, {
      onSuccess: () => setLabel(""),
      onError: (err) => setError(err),
      onSettled: () => add.reset(),
    });
  };

  return (
    <form onSubmit={submit} className="space-y-3" autoComplete="off">
      <Field label="Label">
        <Input required maxLength={100} value={label} onChange={(e) => setLabel(e.target.value)} />
      </Field>
      <Field label="API key (Anthropic)" hint="Shown once. Only the last 4 characters are kept visible afterwards.">
        <Input type="password" required autoComplete="off" spellCheck={false} value={apiKey} onChange={(e) => setApiKey(e.target.value)} />
      </Field>
      <ErrorBox error={error} />
      <Button type="submit" disabled={add.isPending || !label.trim() || !apiKey}>
        Add credential
      </Button>
    </form>
  );
}
