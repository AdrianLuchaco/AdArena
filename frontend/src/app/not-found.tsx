import type { Metadata } from "next";
import { ButtonLink } from "@/components/ui/Button";
import { NO_INDEX } from "@/lib/site";

// Título propio (antes heredaba el de la portada) y fuera de Google
export const metadata: Metadata = { title: "Page not found", ...NO_INDEX };

export default function NotFound() {
  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col items-center justify-center gap-4 px-4 py-24 text-center">
      <p className="font-display text-9xl font-black leading-none text-brand">404</p>
      <h1 className="text-4xl">This page doesn’t exist</h1>
      <p className="text-ink-soft">The link may be mistyped, or the page may have moved.</p>
      <ButtonLink href="/" className="mt-2">
        Back to the homepage
      </ButtonLink>
    </div>
  );
}
