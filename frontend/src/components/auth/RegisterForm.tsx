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

const MIN_PASSWORD = 10;

export function RegisterForm() {
  const { register, status } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNextPath(searchParams.get("next"));

  // Si ya tiene sesión, no tiene sentido enseñarle este formulario
  useEffect(() => {
    if (status === "authenticated") router.replace(next);
  }, [status, router, next]);

  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      await register({ displayName, email, password, acceptTerms });
      router.replace(next);
    } catch (e) {
      if (e instanceof ApiError) {
        // Si el error es de un campo concreto, lo mostramos debajo de ese campo
        if (Object.keys(e.fieldErrors).length > 0) {
          setFieldErrors(e.fieldErrors);
        } else if (e.code === "EMAIL_TAKEN" || e.code === "PASSWORD_TOO_LONG") {
          setFieldErrors({ [e.code === "EMAIL_TAKEN" ? "email" : "password"]: e.message });
        } else {
          setError(e.message);
        }
      } else {
        setError("We couldn’t create your account. Try again.");
      }
      setSubmitting(false);
    }
  }

  const passwordShort = password.length > 0 && password.length < MIN_PASSWORD;

  return (
    <AuthCard
      title="Sign up"
      subtitle="It’s free, and you get 200 Arena Points to start."
      footer={
        <>
          Already have an account?{" "}
          <Link href={`/login?next=${encodeURIComponent(next)}`} className="font-semibold text-brand hover:underline">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {error && <Alert tone="danger">{error}</Alert>}
        <TextField
          label="Your name"
          autoComplete="name"
          placeholder="Alex Morgan"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          error={fieldErrors.displayName}
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          inputMode="email"
          placeholder="you@email.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          hint={passwordShort ? `${MIN_PASSWORD - password.length} more characters to go.` : "At least 10 characters. A phrase you can remember works best."}
        />

        <div>
          <label className="flex cursor-pointer items-start gap-3 text-sm leading-relaxed text-ink-soft">
            <input
              type="checkbox"
              checked={acceptTerms}
              onChange={(e) => setAcceptTerms(e.target.checked)}
              className="mt-0.5 size-5 shrink-0 cursor-pointer rounded accent-brand"
            />
            <span>
              I accept the{" "}
              <Link href="/legal/terms" target="_blank" className="font-semibold text-ink underline">
                Terms and conditions
              </Link>{" "}
              and the{" "}
              <Link href="/legal/privacy" target="_blank" className="font-semibold text-ink underline">
                Privacy policy
              </Link>
              . I understand that if I don’t win in the Arena, I keep 50% of the points I bid for the next day and the
              other 50% is lost.
            </span>
          </label>
          {fieldErrors.acceptTerms && <p className="mt-1.5 text-[13px] text-danger">{fieldErrors.acceptTerms}</p>}
        </div>

        <Button type="submit" size="lg" className="w-full" loading={submitting} disabled={!acceptTerms}>
          Create account
        </Button>
      </form>
    </AuthCard>
  );
}
