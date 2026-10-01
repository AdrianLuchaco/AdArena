import type { Metadata } from "next";
import { pageJsonLd, pageMetadata } from "@/lib/site";
import Link from "next/link";
import { ArenaClient } from "@/components/arena/ArenaClient";
import { JsonLd } from "@/components/seo/JsonLd";

const TITLE = "Today’s Race: bid for tomorrow’s homepage";
const DESCRIPTION =
  "Live standings and countdown. Startups and side projects bid Crown Points to take over the LaunchCrown homepage for 24 hours. Free to join.";

export const metadata: Metadata = pageMetadata({ title: TITLE, description: DESCRIPTION, path: "/race" });

const RULES: [string, string][] = [
  ["One homepage a day", "The Race closes every night at 00:00 (Madrid time). The highest total bid wins tomorrow’s homepage for 24 hours."],
  ["Bids add up", "You can bid several times a day. Each bid adds to your total, and we tell you the moment someone outbids you."],
  ["No last-second sniping", "A bid in the last 2 minutes pushes the close back 2 more minutes, so everyone gets a chance to answer."],
  ["Never lose everything", "If you don’t win, you keep 50% of your bid as a head start for the next day’s Race."],
];

/**
 * La Race: la clasificación en directo (ArenaClient, con datos) y, debajo, las reglas básicas ya
 * escritas en el HTML para que Google entienda la página sin ejecutar JavaScript.
 */
export default function ArenaPage() {
  return (
    <>
      <JsonLd data={pageJsonLd({ path: "/race", name: TITLE, description: DESCRIPTION, breadcrumb: [["The Race", "/race"]] })} />
      {/* El h1, ya en el HTML para Google; el titular grande ("Today’s Race") llega con los datos */}
      <h1 className="sr-only">Today’s Race: bid Crown Points for tomorrow’s LaunchCrown homepage</h1>
      <ArenaClient />
      <section className="mx-auto w-full max-w-5xl px-4 pb-14 sm:px-6">
        <h2 className="text-3xl sm:text-4xl">How the daily Race works</h2>
        <dl className="mt-5 grid gap-5 sm:grid-cols-2">
          {RULES.map(([title, text]) => (
            <div key={title} className="rounded-md bg-surface p-5 ring-1 ring-line">
              <dt className="font-bold text-ink">{title}</dt>
              <dd className="mt-1 text-sm leading-relaxed text-ink-soft">{text}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-5 text-sm text-ink-soft">
          Crown Points are free: you earn them by{" "}
          <Link href="/earn" className="font-semibold text-brand hover:underline">
            discovering other startups
          </Link>
          . Full details in{" "}
          <Link href="/how-it-works" className="font-semibold text-brand hover:underline">
            how it works
          </Link>
          .
        </p>
      </section>
    </>
  );
}
