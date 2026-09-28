import type { Metadata } from "next";
import { Suspense } from "react";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { PageSpinner } from "@/components/ui/Spinner";

export const metadata: Metadata = { title: "Sign up" };

export default function RegisterPage() {
  return (
    <Suspense fallback={<PageSpinner />}>
      <RegisterForm />
    </Suspense>
  );
}
