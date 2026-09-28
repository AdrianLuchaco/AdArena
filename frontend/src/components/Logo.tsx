import Link from "next/link";
import { cn } from "@/lib/cn";

/** Logotipo: un podio visto de frente (el 1.º en amarillo) sobre un bloque de tinta. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <rect width="32" height="32" rx="5" fill="#0c0f1f" />
      <rect x="6" y="17" width="6" height="9" fill="#ffffff" />
      <rect x="13" y="8" width="6" height="18" fill="#ffd23a" />
      <rect x="20" y="20" width="6" height="6" fill="#ffffff" />
    </svg>
  );
}

export function Logo({ className, light = false }: { className?: string; light?: boolean }) {
  return (
    <Link href="/" className={cn("group inline-flex items-center gap-2", className)} aria-label="AdArena, go to the homepage">
      <LogoMark className="size-8" />
      <span className={cn("font-display text-2xl font-black uppercase leading-none tracking-wide", light ? "text-white" : "text-ink")}>
        AdArena
      </span>
    </Link>
  );
}
