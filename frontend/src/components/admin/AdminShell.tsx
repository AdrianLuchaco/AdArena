"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { cn } from "@/lib/cn";
import { ButtonLink } from "../ui/Button";
import { PageSpinner } from "../ui/Spinner";

const SECTIONS = [
  { href: "/admin", label: "Overview" },
  { href: "/admin/moderation", label: "Moderation" },
  { href: "/admin/promotions", label: "Promotions" },
  { href: "/admin/settings", label: "Settings" },
  { href: "/admin/audit-log", label: "Audit log" },
];

/**
 * Estructura del panel de administración. Si quien entra no es administrador, no ve nada.
 * (La protección de verdad está en el backend: cada ruta /api/admin exige el rol ADMIN.)
 */
export function AdminShell({ children }: { children: React.ReactNode }) {
  const { user, status } = useAuth();
  const pathname = usePathname();

  if (status !== "authenticated") return <PageSpinner label="Checking your session…" />;
  if (user?.role !== "ADMIN") {
    return (
      <div className="mx-auto flex w-full max-w-lg flex-1 flex-col items-center justify-center gap-4 px-4 py-24 text-center">
        <h1 className="text-4xl">This area is for admins only</h1>
        <ButtonLink href="/">Back to the homepage</ButtonLink>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 sm:py-10">
      <h1 className="text-5xl sm:text-6xl">Admin</h1>
      <nav className="-mx-4 my-8 flex overflow-x-auto border-b-2 border-ink px-4 sm:mx-0 sm:px-0" aria-label="Admin sections">
        {SECTIONS.map((section) => {
          const active = pathname === section.href;
          return (
            <Link
              key={section.href}
              href={section.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "-mb-0.5 shrink-0 border-b-4 px-4 py-3 text-sm font-semibold transition",
                active ? "border-brand text-ink" : "border-transparent text-ink-soft hover:text-ink",
              )}
            >
              {section.label}
            </Link>
          );
        })}
      </nav>
      {children}
    </div>
  );
}
