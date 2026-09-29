import Link from "next/link";
import { cn } from "@/lib/cn";

/** Logotipo: una corona (la portada que se gana cada día) sobre un bloque de tinta. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <rect width="32" height="32" rx="5" fill="#0c0f1f" />
      <path d="M6 21V10.5l5.5 5L16 7l4.5 8.5 5.5-5V21z" fill="#ffd23a" />
      <rect x="6" y="23" width="20" height="3" fill="#ffffff" />
    </svg>
  );
}

export function Logo({ className, light = false }: { className?: string; light?: boolean }) {
  return (
    <Link href="/" className={cn("group inline-flex items-center gap-2", className)} aria-label="LaunchCrown, go to the homepage">
      <LogoMark className="size-8" />
      <span className={cn("font-display text-2xl font-black uppercase leading-none tracking-wide", light ? "text-white" : "text-ink")}>
        LaunchCrown
      </span>
    </Link>
  );
}
