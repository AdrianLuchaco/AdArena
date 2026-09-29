import type { Metadata } from "next";
import { pageMetadata } from "@/lib/site";
import { Suspense } from "react";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { PageSpinner } from "@/components/ui/Spinner";

export const metadata: Metadata = pageMetadata({
  title: "Sign up free",
  description:
    "Create your free LaunchCrown account and get 200 Crown Points to start bidding for the homepage. No credit card, no money involved.",
  path: "/signup",
});

export default function RegisterPage() {
  return (
    <Suspense fallback={<PageSpinner />}>
      <RegisterForm />
    </Suspense>
  );
}
