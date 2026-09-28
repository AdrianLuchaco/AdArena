"use client";

import Image from "next/image";
import { useEffect, useRef } from "react";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import { formatPoints, ordinal, prettyUrl } from "@/lib/format";
import { ButtonLink } from "../ui/Button";
import { ArrowUpRightIcon, CloseIcon, CrownIcon } from "../icons";
import { medalClasses } from "./medals";

/** Todo lo que se sabe de un proyecto, venga de la Arena de hoy o de un día anterior. */
export interface ProjectDetail {
  id: string;
  position: number;
  companyName: string;
  description: string;
  websiteUrl: string;
  imageUrl: string;
  totalPoints: number;
  /** Parte del total que conserva del día anterior (solo en la Arena de hoy) */
  carriedInPoints?: number;
  /** Cuánto le falta para alcanzar al primero (solo en la Arena de hoy) */
  gapToLeaderPoints?: number;
  /** "live": compite hoy. "past": ya compitió. */
  context: "live" | "past";
  /** Días anteriores: si ese día ganó la portada */
  winner?: boolean;
  /** Días anteriores: "Arena of Saturday 26 September" */
  dateLabel?: string;
}

/**
 * Ficha de un proyecto: imagen grande, descripción completa, lo que ha pujado y su web.
 * Es un <dialog> nativo: se cierra con Escape, con la X o tocando fuera, y el foco del teclado
 * se queda dentro mientras está abierto.
 */
export function ProjectDialog({
  project,
  onClose,
  showBidButton = true,
}: {
  project: ProjectDetail | null;
  onClose: () => void;
  showBidButton?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (project && !dialog.open) {
      dialog.showModal();
      document.documentElement.style.overflow = "hidden";
    } else if (!project && dialog.open) {
      dialog.close();
    }
    return () => {
      document.documentElement.style.overflow = "";
    };
  }, [project]);

  const medal = project ? medalClasses(project.position) : null;
  const leading = project?.context === "live" && project.position === 1;

  return (
    <dialog
      ref={ref}
      className="project-dialog open:animate-dialog-in m-0 mt-auto max-h-[92svh] w-full max-w-none overflow-y-auto rounded-t-xl bg-surface p-0 text-ink shadow-pop ring-2 ring-ink sm:m-auto sm:max-w-2xl sm:rounded-lg"
      aria-label={project ? `About ${project.companyName}` : "Project details"}
      onClose={() => {
        document.documentElement.style.overflow = "";
        onClose();
      }}
      onClick={(event) => {
        // Un clic en el fondo oscuro (fuera de la tarjeta) cierra la ficha
        if (event.target === ref.current) ref.current?.close();
      }}
    >
      {project && medal && (
        <article>
          <div className="relative aspect-[16/10] w-full overflow-hidden bg-canvas sm:aspect-[2/1]">
            <Image
              src={apiUrl(project.imageUrl)}
              alt={`Image of ${project.companyName}`}
              fill
              sizes="(min-width: 640px) 42rem, 100vw"
              className="object-cover"
            />
            <button
              type="button"
              onClick={() => ref.current?.close()}
              className="absolute right-3 top-3 grid size-10 place-items-center rounded-md bg-ink text-white transition hover:bg-brand"
              aria-label="Close"
              autoFocus
            >
              <CloseIcon className="size-5" />
            </button>
          </div>

          <div className="space-y-5 p-6 sm:p-8">
            <div className="flex flex-wrap items-center gap-2">
              <span
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-sm px-2.5 py-1 text-sm font-bold",
                  medal.badge,
                )}
              >
                {(project.winner || leading) && <CrownIcon className="size-4" />}
                {project.winner ? "Won the homepage" : ordinal(project.position)}
              </span>
              <span className="text-sm text-muted">
                {project.context === "live"
                  ? leading
                    ? "Leading today's Arena"
                    : project.gapToLeaderPoints !== undefined
                      ? `${formatPoints(project.gapToLeaderPoints)} behind the leader`
                      : "In today's Arena"
                  : [project.winner ? null : `Finished ${ordinal(project.position)}`, project.dateLabel]
                      .filter(Boolean)
                      .join(", ")}
              </span>
            </div>

            <h2 className="font-display text-4xl font-black uppercase leading-none text-balance break-words sm:text-5xl">
              {project.companyName}
            </h2>

            <p className="whitespace-pre-line text-lg leading-relaxed text-ink-soft text-pretty">{project.description}</p>

            <dl className="grid grid-cols-2 gap-3">
              <div className={cn("rounded-md p-4", medal.soft)}>
                <dt className="text-sm text-muted">{project.context === "live" ? "Bid so far" : "Total bid"}</dt>
                <dd className="tabular mt-1 font-display text-3xl font-black">{formatPoints(project.totalPoints)}</dd>
              </div>
              {project.carriedInPoints ? (
                <div className="rounded-md bg-canvas p-4">
                  <dt className="text-sm text-muted">Carried over from yesterday</dt>
                  <dd className="tabular mt-1 font-display text-3xl font-black">
                    {formatPoints(project.carriedInPoints)}
                  </dd>
                </div>
              ) : (
                <div className="rounded-md bg-canvas p-4">
                  <dt className="text-sm text-muted">Website</dt>
                  <dd className="mt-1 truncate font-semibold">{prettyUrl(project.websiteUrl)}</dd>
                </div>
              )}
            </dl>

            <div className="flex flex-col flex-wrap gap-3 sm:flex-row">
              <a
                href={project.websiteUrl}
                target="_blank"
                rel="noopener noreferrer sponsored"
                className="inline-flex h-13 flex-1 items-center justify-center gap-2 rounded-md bg-ink px-7 font-semibold text-white transition hover:bg-ink-soft"
              >
                Visit {prettyUrl(project.websiteUrl).split("/")[0]}
                <ArrowUpRightIcon className="size-5" />
              </a>
              {project.context === "live" && (
                <ButtonLink href={`/watch/${project.id}`} size="lg" className="flex-1">
                  Watch it and earn points
                </ButtonLink>
              )}
              {project.context === "live" && showBidButton && (
                <ButtonLink href="/arena" size="lg" variant="secondary" className="flex-1">
                  Bid in the Arena
                </ButtonLink>
              )}
            </div>
            <p className="text-xs text-muted">
              Opens in a new tab. AdArena doesn’t review the content of advertisers’ websites.
            </p>
          </div>
        </article>
      )}
    </dialog>
  );
}
