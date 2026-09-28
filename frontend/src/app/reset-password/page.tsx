import type { Metadata } from "next";
import { Suspense } from "react";
import { ResetPasswordForm } from "@/components/auth/ResetPasswordForm";
import { PageSpinner } from "@/components/ui/Spinner";

export const metadata: Metadata = {
  title: "New password",
  // La URL lleva un token secreto: que nunca se envíe a otras webs ni la indexen buscadores
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default function ResetPasswordPage() {
  // Suspense: el formulario lee ?token= de la URL (useSearchParams)
  return (
    <Suspense fallback={<PageSpinner />}>
      <ResetPasswordForm />
    </Suspense>
  );
}
