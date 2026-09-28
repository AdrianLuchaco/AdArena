"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { safeNextPath } from "@/lib/navigation";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { TextField } from "../ui/Field";
import { AuthCard } from "./AuthCard";

export function LoginForm() {
  const { login, status } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNextPath(searchParams.get("next"));

  // Si ya tiene sesión, no tiene sentido enseñarle este formulario
  useEffect(() => {
    if (status === "authenticated") router.replace(next);
  }, [status, router, next]);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      await login(email, password);
      router.replace(next);
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message);
        setFieldErrors(e.fieldErrors);
      } else {
        setError("We couldn’t log you in. Try again.");
      }
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title="Log in to AdArena"
      subtitle="Manage your ad and your bids."
      footer={
        <>
          No account yet?{" "}
          <Link href={`/signup?next=${encodeURIComponent(next)}`} className="font-semibold text-brand hover:underline">
            Sign up free
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {error && <Alert tone="danger">{error}</Alert>}
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          inputMode="email"
          placeholder="you@email.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          required
        />
        <div className="space-y-1.5">
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            required
          />
          <Link href="/forgot-password" className="inline-block text-sm font-semibold text-brand hover:underline">
            Forgot your password?
          </Link>
        </div>
        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          Log in
        </Button>
      </form>
    </AuthCard>
  );
}
