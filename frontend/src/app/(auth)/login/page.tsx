"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { safeNext, useLogin } from "@/features/auth/api";
import { ApiError } from "@/lib/api-client";
import { Button, Card, Field, Input } from "@/components/ui";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const login = useLogin();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    login.mutate({ email, password }, { onSuccess: () => router.replace(safeNext(params.get("next"))) });
  };

  const err = login.error;
  const message =
    err instanceof ApiError && err.status === 429
      ? "Too many attempts. Try again later."
      : err
        ? "Invalid email or password." // generic: never hint which field was wrong
        : null;

  return (
    <main className="mx-auto mt-24 max-w-sm px-4">
      <Card>
        <h1 className="mb-4 text-xl font-semibold">Sign in</h1>
        <form onSubmit={submit} className="space-y-3">
          <Field label="Email">
            <Input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label="Password">
            <Input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          {message && (
            <p role="alert" className="text-sm text-red-700">
              {message}
            </p>
          )}
          <Button type="submit" disabled={login.isPending} className="w-full">
            {login.isPending ? "Signing in..." : "Sign in"}
          </Button>
        </form>
      </Card>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
