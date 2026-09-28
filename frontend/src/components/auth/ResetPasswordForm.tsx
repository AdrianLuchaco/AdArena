"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, resetPassword } from "@/lib/api";
import { Alert } from "../ui/Alert";
import { Button, ButtonLink } from "../ui/Button";
import { TextField } from "../ui/Field";
import { AuthCard } from "./AuthCard";

const MIN_PASSWORD = 10;

/** Paso 2: con el enlace del email, el usuario elige una contraseña nueva. */
export function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  // El token se guarda en memoria y se quita de la barra de direcciones (así no queda en el historial)
  const [token] = useState(() => searchParams.get("token") ?? "");
  useEffect(() => {
    if (searchParams.get("token")) router.replace("/reset-password");
  }, [router, searchParams]);

  const [password, setPassword] = useState("");
  const [repeat, setRepeat] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    if (password !== repeat) {
      setFieldErrors({ repeat: "The two passwords don’t match." });
      return;
    }
    setSubmitting(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.newPassword) {
        setFieldErrors({ password: e.fieldErrors.newPassword });
      } else {
        setError(e instanceof ApiError ? e.message : "We couldn’t change your password.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  const footer = (
    <Link href="/forgot-password" className="font-semibold text-brand hover:underline">
      Request a new link
    </Link>
  );

  if (!token) {
    return (
      <AuthCard title="Invalid link" subtitle="Open the full link from the email we sent you." footer={footer}>
        <ButtonLink href="/forgot-password" className="w-full">
          Request a new link
        </ButtonLink>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Choose a new password" subtitle="Changing it logs you out on every device." footer={footer}>
      {done ? (
        <div className="space-y-5">
          <Alert tone="success" title="Password changed">
            You can now log in with your new password.
          </Alert>
          <ButtonLink href="/login" className="w-full">
            Log in
          </ButtonLink>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-5" noValidate>
          {error && <Alert tone="danger">{error}</Alert>}
          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            hint={
              password.length > 0 && password.length < MIN_PASSWORD
                ? `${MIN_PASSWORD - password.length} more characters to go.`
                : "At least 10 characters. A phrase you can remember works best."
            }
          />
          <TextField
            label="Repeat it"
            type="password"
            autoComplete="new-password"
            value={repeat}
            onChange={(e) => setRepeat(e.target.value)}
            error={fieldErrors.repeat}
          />
          <Button type="submit" size="lg" className="w-full" loading={submitting}>
            Change password
          </Button>
        </form>
      )}
    </AuthCard>
  );
}
