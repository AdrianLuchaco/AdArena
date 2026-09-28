"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { PageSpinner } from "../ui/Spinner";

/**
 * Envuelve las páginas privadas. Si no hay sesión, manda a "Entrar" y luego te devuelve aquí.
 * (La seguridad real está en el backend: esto solo evita mostrar una página vacía.)
 */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [status, router, pathname]);

  if (status !== "authenticated") {
    return <PageSpinner label={status === "loading" ? "Checking your session…" : "Redirecting…"} />;
  }
  return <>{children}</>;
}
