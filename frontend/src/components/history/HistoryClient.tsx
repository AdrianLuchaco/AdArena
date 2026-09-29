"use client";

import Image from "next/image";
import { useCallback, useEffect, useState } from "react";
import { ApiError, getHistory } from "@/lib/api";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import { formatPoints, formatLongDate, prettyUrl } from "@/lib/format";
import type { PastOutcome, PastProject, PastRound } from "@/lib/types";
import { medalClasses } from "../arena/medals";
import { ProjectDetail, ProjectDialog } from "../arena/ProjectDialog";
import { Alert } from "../ui/Alert";
import { Button, ButtonLink } from "../ui/Button";
import { CrownIcon, TrophyIcon } from "../icons";

const OUTCOME_TEXT: Record<Exclude<PastOutcome, "WINNER">, string> = {
  PENDING_REVIEW: "The winning ad for this day is being reviewed.",
  NO_BIDS: "Nobody took part that day, so the homepage stayed free.",
  NO_WINNER: "No ad was published that day.",
};

/** Ganadores y proyectos de días anteriores, del más reciente al más antiguo. */
export function HistoryClient() {
  const [rounds, setRounds] = useState<PastRound[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ProjectDetail | null>(null);

  const load = useCallback(async (pageToLoad: number) => {
    setLoading(true);
    try {
      const result = await getHistory(pageToLoad);
      setRounds((current) => (pageToLoad === 0 ? result.items : [...current, ...result.items]));
      setPage(result.page);
      setTotalPages(result.totalPages);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t load the history.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(0), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const open = (round: PastRound, project: PastProject) =>
    setSelected({
      ...project,
      position: project.rank,
      context: "past",
      winner: round.winner?.id === project.id,
      dateLabel: `Race of ${formatLongDate(round.roundDate)}`,
    });

  return (
    <div className="mx-auto w-full max-w-5xl flex-1 px-4 py-12 sm:px-6 sm:py-16">
      <h1 className="text-6xl sm:text-7xl">Winners</h1>
      <p className="mt-3 max-w-2xl text-lg text-ink-soft">
        Every day one project wins the homepage. Here are the winners and everyone who competed, with their ads as they
        were that day. Tap any of them to see more.
      </p>

      {error && (
        <Alert tone="danger" className="mt-8">
          {error}
        </Alert>
      )}

      {!loading && !error && rounds.length === 0 && (
        <div className="mt-10 rounded-lg border-2 border-dashed border-ink/30 px-6 py-14 text-center">
          <p className="font-display text-3xl font-black uppercase">No winners yet</p>
          <p className="mx-auto mt-2 max-w-md text-ink-soft">
            The first winner shows up here as soon as the first Race closes. Will it be your project?
          </p>
          <ButtonLink href="/race" className="mt-6">
            Go to the Race
          </ButtonLink>
        </div>
      )}

      <div className="mt-10 space-y-10">
        {rounds.map((round) => (
          <RoundCard key={round.roundDate} round={round} onOpen={(project) => open(round, project)} />
        ))}
        {loading && <div className="skeleton h-72 rounded-lg" />}
      </div>

      {!loading && page + 1 < totalPages && (
        <div className="mt-10 text-center">
          <Button variant="secondary" onClick={() => void load(page + 1)}>
            Show earlier days
          </Button>
        </div>
      )}

      <ProjectDialog project={selected} onClose={() => setSelected(null)} />
    </div>
  );
}

function RoundCard({ round, onOpen }: { round: PastRound; onOpen: (project: PastProject) => void }) {
  const winner = round.winner;
  return (
    <article>
      <h2 className="text-4xl">{formatLongDate(round.showcaseDate)}</h2>
      <p className="mt-1 text-sm text-muted">Bidding closed on {formatLongDate(round.roundDate)}</p>

      {winner ? (
        <WinnerPlate winner={winner} onOpen={() => onOpen(winner)} />
      ) : (
        <p className="mt-3 rounded-md bg-surface px-5 py-4 text-ink-soft ring-1 ring-line">
          {OUTCOME_TEXT[round.outcome as Exclude<PastOutcome, "WINNER">]}
        </p>
      )}

      {round.projects.length > 0 && (
        <div className="mt-3 overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
          <table className="w-full table-fixed border-collapse text-left">
            <caption className="sr-only">Final standings that day, highest bid first</caption>
            <thead>
              <tr className="border-b border-line bg-canvas text-sm text-muted">
                <th scope="col" className="w-[4.25rem] py-2.5 pl-4 font-medium sm:w-24 sm:pl-6">
                  Place
                </th>
                <th scope="col" className="py-2.5 font-medium">
                  Project
                </th>
                <th scope="col" className="w-28 py-2.5 pr-4 text-right font-medium sm:w-36 sm:pr-6">
                  Bid
                </th>
              </tr>
            </thead>
            <tbody>
              {round.projects.map((project) => {
                const isWinner = winner?.id === project.id;
                return (
                  <tr
                    key={project.id}
                    onClick={() => onOpen(project)}
                    className={cn(
                      "cursor-pointer border-b border-line transition last:border-b-0 hover:bg-canvas",
                      isWinner && "bg-gold-soft/70 hover:bg-gold-soft",
                    )}
                  >
                    <td className="py-2.5 pl-4 sm:pl-6">
                      <span
                        className={cn(
                          "tabular grid size-8 place-items-center rounded-sm font-display text-base font-black",
                          // Solo el ganador lleva oro: si se rechazó al 1.º, el oro es para quien ocupó la portada
                          isWinner ? "bg-gold text-ink" : medalClasses(99).badge,
                        )}
                      >
                        {isWinner ? <CrownIcon className="size-4" /> : project.rank}
                      </span>
                    </td>
                    <td className="py-2.5 pr-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <span className="relative size-10 shrink-0 overflow-hidden rounded-sm bg-canvas">
                          <Image src={apiUrl(project.imageUrl)} alt="" fill sizes="40px" className="object-cover" />
                        </span>
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            onOpen(project);
                          }}
                          className="min-w-0 truncate text-left font-semibold hover:underline"
                        >
                          {project.companyName}
                        </button>
                      </div>
                    </td>
                    <td className="tabular py-2.5 pr-4 text-right font-semibold sm:pr-6">{formatPoints(project.totalPoints)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </article>
  );
}

/** El ganador del día: la misma franja amarilla que lleva quien va primero en la Arena. */
function WinnerPlate({ winner, onOpen }: { winner: PastProject; onOpen: () => void }) {
  return (
    <div
      className="group relative mt-3 grid overflow-hidden rounded-lg bg-gold shadow-lift ring-2 ring-ink md:grid-cols-[1fr_1.1fr]"
    >
      <div className="relative aspect-[16/10] overflow-hidden border-b-2 border-ink bg-canvas md:aspect-auto md:min-h-64 md:border-b-0 md:border-r-2">
        <Image
          src={apiUrl(winner.imageUrl)}
          alt={`Image of ${winner.companyName}`}
          fill
          sizes="(min-width: 768px) 28rem, 100vw"
          className="object-cover transition duration-500 group-hover:scale-[1.03]"
        />
        <span className="absolute left-4 top-4 inline-flex items-center gap-1.5 rounded-sm bg-ink px-3 py-1.5 text-sm font-bold text-gold">
          <CrownIcon className="size-4" /> Winner
        </span>
      </div>
      <div className="flex min-w-0 flex-col gap-3 p-6 sm:p-8">
        <p className="inline-flex items-center gap-2 text-sm font-semibold">
          <TrophyIcon className="size-4" /> Won the homepage with {formatPoints(winner.totalPoints)}
        </p>
        <h3 className="font-display text-4xl font-black uppercase leading-none text-balance break-words sm:text-5xl">
          <button
            type="button"
            onClick={onOpen}
            className="text-left after:absolute after:inset-0 after:content-[''] focus-visible:outline-none"
          >
            {winner.companyName}
          </button>
        </h3>
        <p className="line-clamp-4 whitespace-pre-line leading-relaxed text-ink/80">{winner.description}</p>
        <p className="mt-auto flex items-center justify-between gap-3 pt-2 text-sm">
          <span className="truncate text-ink/70">{prettyUrl(winner.websiteUrl)}</span>
          <span className="shrink-0 font-semibold underline decoration-2 underline-offset-4">More about it</span>
        </p>
      </div>
    </div>
  );
}
