import Link from "next/link";
import { ArrowRightIcon } from "../icons";

/**
 * Piezas de las páginas de contenido (About, guías, comparativas): el mismo aspecto que "How it works"
 * (cabecera azul de pista, títulos grandes, listas con líneas) para que se reconozca la web.
 */

export const contentLinkClass = "font-semibold text-brand hover:underline";

/** Cabecera azul con el título (h1), una entradilla y la fecha de la última revisión. */
export function ContentHero({ eyebrow, title, intro, updated }: { eyebrow?: string; title: string; intro: string; updated?: string }) {
  return (
    <header className="track relative isolate border-b-2 border-ink text-white">
      <div className="mx-auto max-w-6xl px-4 py-14 sm:px-6 sm:py-20">
        {eyebrow && <p className="mb-3 text-sm font-semibold uppercase tracking-wide text-gold">{eyebrow}</p>}
        <h1 className="max-w-[18ch] text-5xl text-balance sm:text-7xl">{title}</h1>
        <p className="mt-5 max-w-2xl text-lg leading-relaxed text-white/85 sm:text-xl">{intro}</p>
        {updated && <p className="mt-6 text-sm text-white/70">Last updated: {updated}</p>}
      </div>
    </header>
  );
}

/** El cuerpo: una columna cómoda de leer. */
export function ContentBody({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto w-full max-w-4xl space-y-16 px-4 py-12 sm:px-6 lg:py-16">{children}</div>;
}

export function ContentSection({
  id,
  title,
  intro,
  children,
}: {
  id?: string;
  title: string;
  intro?: React.ReactNode;
  children?: React.ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-24">
      <h2 className="text-4xl sm:text-5xl">{title}</h2>
      {intro && <div className="mt-4 max-w-3xl text-lg leading-relaxed text-ink-soft">{intro}</div>}
      {children && <div className="mt-6 space-y-5 leading-relaxed text-ink-soft">{children}</div>}
    </section>
  );
}

/** Lista de definiciones: el nombre a la izquierda y la explicación a la derecha. */
export function DefinitionRows({ items }: { items: [React.ReactNode, React.ReactNode][] }) {
  return (
    <dl className="divide-y divide-line border-y-2 border-ink">
      {items.map(([term, text], index) => (
        <div key={index} className="grid gap-1 py-4 sm:grid-cols-[13rem_1fr] sm:gap-6">
          <dt className="font-bold text-ink">{term}</dt>
          <dd>{text}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Tabla con cabecera oscura (como la de los puntos en "How it works"). Se desplaza de lado en el móvil. */
export function ContentTable({ caption, head, rows }: { caption: string; head: string[]; rows: React.ReactNode[][] }) {
  return (
    <div className="overflow-x-auto rounded-lg bg-surface ring-2 ring-ink">
      <table className="w-full min-w-[36rem] text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-ink text-white">
          <tr>
            {head.map((cell) => (
              <th key={cell} scope="col" className="px-4 py-3 font-semibold">
                {cell}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((row, index) => (
            <tr key={index}>
              {row.map((cell, cellIndex) =>
                cellIndex === 0 ? (
                  <th key={cellIndex} scope="row" className="px-4 py-3.5 align-top font-semibold text-ink">
                    {cell}
                  </th>
                ) : (
                  <td key={cellIndex} className="px-4 py-3.5 align-top text-ink-soft">
                    {cell}
                  </td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Preguntas que se abren al pulsarlas. */
export function Questions({ items }: { items: [string, React.ReactNode][] }) {
  return (
    <div className="divide-y divide-line overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
      {items.map(([question, answer]) => (
        <details key={question} className="group px-5 py-4 open:bg-canvas/60">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold text-ink">
            {question}
            <span className="grid size-7 shrink-0 place-items-center rounded-sm bg-canvas text-lg leading-none transition group-open:rotate-45">
              +
            </span>
          </summary>
          <div className="mt-2 text-sm leading-relaxed">{answer}</div>
        </details>
      ))}
    </div>
  );
}

/** La llamada final, en amarillo (la misma que cierra "How it works"). */
export function ReadyBox({ title = "Ready?", text }: { title?: string; text: string }) {
  return (
    <section className="rounded-lg bg-gold p-8 shadow-lift ring-2 ring-ink sm:p-10">
      <p className="font-display text-5xl font-black uppercase leading-none">{title}</p>
      <p className="mt-3 max-w-xl text-ink/80">{text}</p>
      <div className="mt-6 flex flex-wrap gap-3">
        <Link
          href="/signup"
          className="inline-flex h-12 items-center gap-2 rounded-md bg-ink px-6 font-semibold text-white hover:bg-brand"
        >
          Sign up free <ArrowRightIcon className="size-5" />
        </Link>
        <Link
          href="/race"
          className="inline-flex h-12 items-center rounded-md px-6 font-semibold ring-2 ring-ink hover:bg-white/40"
        >
          See today’s Race
        </Link>
      </div>
    </section>
  );
}

/** Enlaces a las otras páginas de contenido, al final de cada una. */
export function RelatedPages({ links }: { links: [string, string][] }) {
  return (
    <nav aria-label="Related pages" className="border-t-2 border-ink pt-6">
      <p className="text-sm font-semibold text-muted">Keep reading</p>
      <ul className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:gap-x-8">
        {links.map(([label, href]) => (
          <li key={href}>
            <Link href={href} className={`inline-flex items-center gap-1.5 ${contentLinkClass}`}>
              {label} <ArrowRightIcon className="size-4" />
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
