import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginForm } from "@/components/auth/LoginForm";
import { PageSpinner } from "@/components/ui/Spinner";

export const metadata: Metadata = { title: "Log in" };

export default function LoginPage() {
  // Suspense: el formulario lee ?next= de la URL (useSearchParams)
  return (
    <Suspense fallback={<PageSpinner />}>
      <LoginForm />
    </Suspense>
  );
}
