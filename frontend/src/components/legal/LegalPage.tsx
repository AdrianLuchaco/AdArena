import { CONTACT_EMAIL } from "@/lib/legal";

/** Maquetación común de las páginas legales (texto largo, cómodo de leer). */
export function LegalPage({ title, updated, children }: { title: string; updated: string; children: React.ReactNode }) {
  return (
    <article className="mx-auto w-full max-w-3xl flex-1 px-4 py-12 sm:px-6 sm:py-16">
      <h1 className="text-5xl sm:text-6xl">{title}</h1>
      <p className="mt-2 text-sm text-muted">Last updated: {updated}</p>
      <div className="mt-8 space-y-6 leading-relaxed text-ink-soft [&_a]:font-semibold [&_a]:text-ink [&_a]:underline [&_h2]:mt-10 [&_h2]:text-3xl [&_h2]:text-ink [&_li]:ml-5 [&_li]:list-disc [&_strong]:text-ink">
        {children}
      </div>
      <p className="mt-12 border-t border-line pt-6 text-sm text-muted">
        Questions about this page? Write to us at{" "}
        <a href={`mailto:${CONTACT_EMAIL}`} className="font-semibold text-ink underline">
          {CONTACT_EMAIL}
        </a>
        .
      </p>
    </article>
  );
}
