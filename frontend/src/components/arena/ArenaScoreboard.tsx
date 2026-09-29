"use client";

import Image from "next/image";
import Link from "next/link";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import { formatMadridTime, formatPoints } from "@/lib/format";
import type { RoundSummary } from "@/lib/types";
import { AnimatedNumber } from "../fx/AnimatedNumber";
import { CrownIcon } from "../icons";
import { Countdown } from "./Countdown";

/**
 * El marcador de la Arena: la pista azul con sus calles, titular enorme, el contador de fichas y quién
 * va primero.
 * Es la pieza principal de la web y la misma en la portada y en la Arena (cambian el titular y los
 * botones), para que se reconozca en todas partes.
 */
export function ArenaScoreboard({
  round,
  clockOffset,
  live,
  heading: Heading = "h1",
  title,
  intro,
  notice,
  actions,
  showLeader = true,
}: {
  round: RoundSummary | null;
  clockOffset: number;
  live: boolean;
  heading?: "h1" | "h2";
  title: string;
  intro: React.ReactNode;
  /** Aviso corto encima del titular (p. ej. "the winning ad is being reviewed") */
  notice?: { title: string; text: string; href?: string; action?: string } | null;
  actions?: React.ReactNode;
  /** Quién va primero (no hace falta si la clasificación está justo debajo) */
  showLeader?: boolean;
}) {
  const leader = round?.ranking[0] ?? null;

  return (
    <section className="track relative isolate overflow-hidden border-b-2 border-ink text-white">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-10 sm:px-6 sm:py-14 lg:grid-cols-[1fr_auto] lg:items-end lg:gap-14">
        <div className="min-w-0">
          {notice && (
            <p className="animate-fade-up mb-6 max-w-xl rounded-md bg-ink/35 px-4 py-3 text-sm text-white/85 ring-1 ring-white/15">
              <strong className="font-semibold text-white">{notice.title}.</strong> {notice.text}
              {notice.href && notice.action && (
                <Link
                  href={notice.href}
                  className="ml-1 font-semibold text-gold underline underline-offset-4 hover:text-white"
                >
                  {notice.action}
                </Link>
              )}
            </p>
          )}
          <p className="animate-fade-up flex items-center gap-2 text-sm font-semibold text-white/80">
            <span
              className={cn("size-2.5 rounded-full", live ? "animate-pulse-dot bg-live ring-2 ring-white" : "bg-white/40")}
              aria-hidden="true"
            />
            {live ? "Live now" : "Connecting…"}
          </p>
          <Heading className="animate-fade-up mt-3 max-w-[13ch] text-6xl text-balance sm:text-7xl lg:text-8xl [animation-delay:60ms]">
            {title}
          </Heading>
          <div className="animate-fade-up mt-5 max-w-xl text-lg leading-relaxed text-white/85 text-pretty [animation-delay:120ms]">
            {intro}
          </div>
          {actions && (
            <div className="animate-fade-up mt-8 flex flex-wrap gap-3 [animation-delay:180ms]">{actions}</div>
          )}
        </div>

        <div className="animate-fade-up min-w-0 [animation-delay:200ms]">
          {round ? (
            <>
              <p className="mb-2 text-sm font-semibold text-white/85">Bidding closes in</p>
              <Countdown endsAt={round.endsAt} clockOffset={clockOffset} />
              <p className="mt-3 text-sm text-white/75">
                At {formatMadridTime(round.endsAt)} Madrid time. {round.participants}{" "}
                {round.participants === 1 ? "project is" : "projects are"} bidding.
              </p>
              {showLeader && (
                <div className="mt-5 flex items-center gap-3 rounded-lg bg-gold p-3 text-ink ring-2 ring-ink">
                  {leader ? (
                    <>
                      <span className="relative size-11 shrink-0 overflow-hidden rounded-full bg-white ring-2 ring-ink">
                        <Image src={apiUrl(leader.imageUrl)} alt="" fill sizes="44px" className="object-cover" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center gap-1 text-xs font-semibold">
                          <CrownIcon className="size-3.5" /> Leading
                        </span>
                        <span className="block truncate font-semibold">{leader.companyName}</span>
                      </span>
                      <span className="tabular shrink-0 font-display text-2xl font-black">
                        <AnimatedNumber value={leader.totalPoints} format={formatPoints} />
                      </span>
                    </>
                  ) : (
                    <span className="text-sm font-medium">No bids yet. The first bid takes the lead.</span>
                  )}
                </div>
              )}
            </>
          ) : (
            <p className="text-white/85">Today’s Race opens in a few moments.</p>
          )}
        </div>
      </div>
    </section>
  );
}
