"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiError, requestPasswordReset } from "@/lib/api";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { TextField } from "../ui/Field";
import { AuthCard } from "./AuthCard";

/** Paso 1: el usuario escribe su email y le mandamos un enlace (si la cuenta existe). */
export function ForgotPasswordForm() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldError(undefined);
    try {
      await requestPasswordReset(email);
      setSent(true);
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.email) {
        setFieldError(e.fieldErrors.email);
      } else {
        setError(e instanceof ApiError ? e.message : "We couldn’t send the link. Try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title="Forgot your password?"
      subtitle="Enter your account’s email and we’ll send you a link to choose a new one."
      footer={
        <>
          Remembered it?{" "}
          <Link href="/login" className="font-semibold text-brand hover:underline">
            Log in
          </Link>
        </>
      }
    >
      {sent ? (
        <Alert tone="success" title="Check your email">
          If there’s an account for {email}, we’ve sent a link to change your password. It expires in 60 minutes. Check
          your spam folder too.
        </Alert>
      ) : (
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
            error={fieldError}
            required
          />
          <Button type="submit" size="lg" className="w-full" loading={submitting}>
            Send me the link
          </Button>
        </form>
      )}
    </AuthCard>
  );
}
