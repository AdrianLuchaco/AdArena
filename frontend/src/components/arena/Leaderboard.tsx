"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import { formatPoints, ordinal, prettyUrl } from "@/lib/format";
import type { ProjectEntry, RoundSummary } from "@/lib/types";
import { AnimatedNumber } from "../fx/AnimatedNumber";
import { useFlashOnChange } from "../fx/useFlashOnChange";
import { ButtonLink } from "../ui/Button";
import { ArrowRightIcon, CrownIcon } from "../icons";
import { ProjectDetail, ProjectDialog } from "./ProjectDialog";

/**
 * La clasificación de hoy como una CARRERA: cada proyecto corre por su calle de la pista y su barra
 * llega tan lejos como lo que ha pujado comparado con el primero, que toca la meta (en amarillo).
 * Así se ve de un vistazo quién gana y por cuánto. Siempre de mayor a menor puja (a igualdad, va
 * delante quien llegó antes a ese total) y se actualiza sola: las barras avanzan cuando alguien puja.
 *
 * Con {@code limit} (portada) se ven los primeros y un enlace a la Arena para ver todos.
 * Tocar cualquier calle abre la ficha del proyecto con su descripción completa y su web.
 */
export function Leaderboard({
  round,
  limit,
  showBidButton = true,
}: {
  round: RoundSummary | null;
  limit?: number;
  showBidButton?: boolean;
}) {
  const [selected, setSelected] = useState<ProjectDetail | null>(null);
  const ranking = round?.ranking ?? [];

  if (ranking.length === 0) {
    return (
      <div
        className="track rounded-2xl px-6 py-12 text-center text-white ring-2 ring-ink"
        style={{ "--lane": "64px" } as React.CSSProperties}
      >
        <p className="font-display text-4xl font-black uppercase">No bids yet today</p>
        <p className="mx-auto mt-2 max-w-md text-white/85">The first project to bid goes straight to the front.</p>
        {showBidButton && (
          <ButtonLink href="/arena" variant="gold" className="mt-6">
            Place the first bid
          </ButtonLink>
        )}
      </div>
    );
  }

  const leader = ranking[0];
  const shown = limit ? ranking.slice(0, limit) : ranking;
  const hidden = ranking.length - shown.length;
  const open = (project: ProjectEntry) =>
    setSelected({
      ...project,
      context: "live",
      gapToLeaderPoints: project.position === 1 ? undefined : leader.totalPoints - project.totalPoints,
    });

  return (
    <div className="overflow-hidden rounded-2xl bg-brand text-white shadow-lift ring-2 ring-ink">
      <ol aria-label="Today’s standings, highest bid first">
        {shown.map((project) => (
          <Lane
            key={project.id}
            project={project}
            leaderTotal={leader.totalPoints}
            secondTotal={ranking[1]?.totalPoints}
            onOpen={open}
          />
        ))}
      </ol>
      {hidden > 0 && (
        <Link
          href="/arena"
          className="flex items-center justify-center gap-2 border-t-2 border-white/25 px-4 py-3 text-sm font-semibold transition hover:bg-white/10"
        >
          See all {ranking.length} projects in the Arena
          <ArrowRightIcon className="size-4" />
        </Link>
      )}
      <ProjectDialog project={selected} onClose={() => setSelected(null)} showBidButton={showBidButton} />
    </div>
  );
}

/** Una calle de la pista: número de calle, el proyecto y su barra hasta la meta. */
function Lane({
  project,
  leaderTotal,
  secondTotal,
  onOpen,
}: {
  project: ProjectEntry;
  leaderTotal: number;
  secondTotal?: number;
  onOpen: (project: ProjectEntry) => void;
}) {
  const flashRef = useFlashOnChange<HTMLLIElement>(project.totalPoints);
  const leading = project.position === 1;
  const ratio = leaderTotal > 0 ? Math.min(1, project.totalPoints / leaderTotal) : 0;
  const gap = leaderTotal - project.totalPoints;
  const lead = leading && secondTotal !== undefined ? project.totalPoints - secondTotal : null;

  return (
    <li ref={flashRef} className="relative border-b-2 border-white/25 last:border-b-0">
      {/* La barra: de la salida (izquierda) a la meta (derecha), según lo pujado */}
      <span
        className={cn("lane-bar absolute inset-y-1.5 left-0 rounded-r-full", leading ? "bg-gold" : "bg-white/[0.16]")}
        style={{ width: `calc(3rem + (100% - 3.5rem) * ${ratio})` }}
        aria-hidden="true"
      />
      <div className={cn("relative flex items-center gap-3 px-3 py-3 sm:gap-4 sm:px-4", leading && "text-ink sm:py-4")}>
        <span
          className={cn(
            "w-7 shrink-0 text-center font-display font-black leading-none",
            leading ? "text-4xl sm:text-5xl" : "text-3xl text-white/75",
          )}
          aria-label={`${ordinal(project.position)} place`}
        >
          {project.position}
        </span>
        <span
          className={cn(
            "relative shrink-0 overflow-hidden rounded-full bg-white ring-2",
            leading ? "size-12 ring-ink sm:size-14" : "size-10 ring-white",
          )}
        >
          <Image src={apiUrl(project.imageUrl)} alt="" fill sizes="56px" className="object-cover" />
        </span>
        <span className="min-w-0 flex-1">
          {leading && (
            <span className="flex items-center gap-1 text-xs font-bold">
              <CrownIcon className="size-3.5" /> Leading
            </span>
          )}
          {/* El botón ocupa toda la calle (after:inset-0): se puede tocar en cualquier parte */}
          <button
            type="button"
            onClick={() => onOpen(project)}
            className={cn(
              "block max-w-full text-left after:absolute after:inset-0 after:content-[''] focus-visible:outline-none",
              leading
                ? "font-display text-xl font-black uppercase leading-none break-words sm:text-3xl"
                : "truncate font-semibold",
            )}
            aria-label={`Open ${project.companyName}, ${ordinal(project.position)}`}
          >
            {project.companyName}
          </button>
          <span className={cn("block truncate text-sm", leading ? "text-ink/70" : "text-white/70")}>
            {prettyUrl(project.websiteUrl)}
          </span>
        </span>
        <span className="shrink-0 text-right">
          <span
            className={cn(
              "tabular block font-display font-black leading-none",
              leading ? "text-2xl sm:text-4xl" : "text-xl sm:text-2xl",
            )}
          >
            <AnimatedNumber value={project.totalPoints} format={formatPoints} />
          </span>
          <span className={cn("tabular mt-1 block text-xs", leading ? "font-semibold text-ink/75" : "text-white/70")}>
            {leading
              ? lead === null
                ? "Out in front"
                : lead === 0
                  ? "Tied: earlier bid wins"
                  : `${formatPoints(lead)} ahead`
              : `${formatPoints(gap)} behind`}
          </span>
        </span>
      </div>
    </li>
  );
}

/** Esqueleto de carga con la misma forma que la pista. */
export function LeaderboardSkeleton() {
  return (
    <div className="space-y-1 overflow-hidden rounded-2xl ring-2 ring-ink/10">
      <div className="skeleton h-24" />
      <div className="skeleton h-16" />
      <div className="skeleton h-16" />
    </div>
  );
}
