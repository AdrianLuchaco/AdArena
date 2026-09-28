import { Alert } from "../ui/Alert";

/** Maquetación común de las páginas legales (texto largo, cómodo de leer). */
export function LegalPage({ title, updated, children }: { title: string; updated: string; children: React.ReactNode }) {
  return (
    <article className="mx-auto w-full max-w-3xl flex-1 px-4 py-12 sm:px-6 sm:py-16">
      <h1 className="text-5xl sm:text-6xl">{title}</h1>
      <p className="mt-2 text-sm text-muted">Last updated: {updated}</p>
      <Alert tone="warning" title="Draft version" className="mt-6">
        This text is a draft and will be completed before launch after a professional review.
      </Alert>
      <div className="mt-8 space-y-6 leading-relaxed text-ink-soft [&_h2]:mt-10 [&_h2]:text-3xl [&_h2]:text-ink [&_li]:ml-5 [&_li]:list-disc [&_strong]:text-ink">
        {children}
      </div>
    </article>
  );
}
