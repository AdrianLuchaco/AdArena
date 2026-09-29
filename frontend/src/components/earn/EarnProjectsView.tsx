"use client";

import Image from "next/image";
import Link from "next/link";
import { apiUrl } from "@/lib/config";
import { ordinal } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { EarnProject } from "@/lib/types";
import { ButtonLink } from "../ui/Button";
import { ArrowRightIcon, CheckIcon, EyeIcon, WindowIcon } from "../icons";
import { SiteAvatar } from "../viewer/SiteAvatar";
import { useEarnData } from "./EarnShell";

/** "Arena websites": la siguiente que te conviene ver y todas las de hoy. */
export function EarnProjectsView() {
  const { overview } = useEarnData();
  const { rules } = overview;
  const others = overview.projects.filter((project) => !project.own);
  const pending = others.filter((project) => project.pointsEarnedToday < project.dailyCap);
  const spotlight = pending[0] ?? null;
  const rest = others.filter((project) => project.id !== spotlight?.id);

  if (others.length === 0) {
    return (
      <div className="rounded-lg border-2 border-dashed border-ink/30 px-6 py-14 text-center">
        <p className="font-display text-3xl font-black uppercase">No websites to watch yet today</p>
        <p className="mx-auto mt-2 max-w-md text-ink-soft">
          As soon as someone bids in the Race, their website shows up here. Meanwhile, try the bonus links.
        </p>
        <ButtonLink href="/earn/links" className="mt-6">
          Go to bonus links
        </ButtonLink>
      </div>
    );
  }

  return (
    <div className="space-y-10">
      {spotlight ? (
        <Spotlight project={spotlight} />
      ) : (
        <div className="flex items-center gap-4 rounded-lg bg-success-soft p-6 ring-2 ring-success">
          <span className="grid size-12 place-items-center rounded-md bg-success text-white">
            <CheckIcon className="size-6" />
          </span>
          <div>
            <p className="font-display text-2xl font-black uppercase">You’ve watched every website today</p>
            <p className="text-ink-soft">You can earn their points again tomorrow. Meanwhile, try the bonus links.</p>
          </div>
        </div>
      )}

      {/* El destacado ya está arriba: la lista muestra el resto, sin repetirlo */}
      {rest.length > 0 && (
        <section>
          <div className="flex flex-wrap items-end justify-between gap-2">
            <h2 className="text-4xl">{spotlight ? "More websites today" : "Bidding today"}</h2>
            <p className="text-sm text-muted">
              +{rules.tickPoints} every {rules.tickSeconds} s, +{rules.bonusPoints} at {rules.bonusAfterSeconds} s, up
              to {rules.dailyCapPerProject} per website
            </p>
          </div>
          <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {rest.map((project) => (
              <li key={project.id}>
                <ProjectCard project={project} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <HowItCounts />
    </div>
  );
}

/** La siguiente web que te conviene ver, en grande. */
function Spotlight({ project }: { project: EarnProject }) {
  const image = project.site.imageUrl ?? project.imageUrl;
  const remaining = project.dailyCap - project.pointsEarnedToday;
  return (
    <Link
      href={`/watch/${project.id}`}
      className="group grid overflow-hidden rounded-lg bg-night text-white shadow-lift ring-2 ring-ink md:grid-cols-[1.2fr_1fr]"
    >
      <span className="relative block aspect-[16/10] overflow-hidden md:aspect-auto md:min-h-80">
        <Image
          src={apiUrl(image)}
          alt=""
          fill
          sizes="(min-width: 768px) 36rem, 100vw"
          className="object-cover transition duration-700 group-hover:scale-[1.03]"
          priority
        />
        <span className="absolute inset-0 bg-linear-to-t from-night/70 via-transparent to-transparent md:bg-linear-to-r md:from-transparent md:via-transparent md:to-night/60" />
        <span className="absolute left-4 top-4 rounded-sm bg-white px-3 py-1 text-sm font-bold text-ink">
          {ordinal(project.position)} in the Race
        </span>
      </span>
      <span className="flex flex-col justify-center gap-4 p-6 sm:p-8">
        <span className="text-sm font-semibold text-gold">
          {project.pointsEarnedToday > 0 ? "Pick up where you left off" : "Up next for you"}
        </span>
        <span className="flex items-center gap-3">
          <SiteAvatar
            name={project.site.siteName || project.companyName}
            iconUrl={project.site.iconUrl}
            color={project.site.themeColor}
            className="size-11"
          />
          <span className="min-w-0">
            <span className="block truncate font-display text-4xl font-black uppercase leading-none">
              {project.companyName}
            </span>
            <span className="block truncate text-sm text-white/60">{project.site.domain}</span>
          </span>
        </span>
        <span className="line-clamp-3 text-white/75">{project.site.description ?? project.description}</span>
        <span className="flex flex-wrap gap-2 text-sm">
          <span className="tabular rounded-sm bg-gold px-3 py-1 font-bold text-ink">+{remaining} to earn</span>
          <ModeChip mode={project.site.mode} dark />
        </span>
        <span className="mt-2 inline-flex h-13 w-fit items-center gap-2 rounded-md bg-brand px-7 font-semibold text-white ring-2 ring-white/20 transition group-hover:bg-brand-dark">
          {project.pointsEarnedToday > 0 ? "Keep watching" : "Start watching"}
          <ArrowRightIcon className="size-5" />
        </span>
      </span>
    </Link>
  );
}

function ProjectCard({ project }: { project: EarnProject }) {
  const done = project.pointsEarnedToday >= project.dailyCap;
  const started = project.pointsEarnedToday > 0;
  const image = project.site.imageUrl ?? project.imageUrl;
  return (
    <Link
      href={`/watch/${project.id}`}
      className={cn(
        "group flex h-full flex-col overflow-hidden rounded-lg bg-surface ring-2 ring-ink/15 transition hover:ring-ink",
        done && "opacity-80",
      )}
    >
      <span className="relative aspect-[16/9] overflow-hidden bg-canvas">
        <Image
          src={apiUrl(image)}
          alt=""
          fill
          sizes="(min-width: 1024px) 22rem, (min-width: 640px) 50vw, 100vw"
          className="object-cover"
        />
        <span className="absolute left-3 top-3 rounded-sm bg-white px-2 py-0.5 text-xs font-bold text-ink">
          {ordinal(project.position)}
        </span>
        {done && (
          <span className="absolute inset-0 grid place-items-center bg-night/55">
            <span className="animate-pop inline-flex items-center gap-1.5 rounded-sm bg-success px-4 py-2 text-sm font-bold text-white">
              <CheckIcon className="size-4" /> {project.dailyCap} earned
            </span>
          </span>
        )}
      </span>
      <span className="flex flex-1 flex-col gap-3 p-4">
        <span className="flex items-center gap-2.5">
          <SiteAvatar
            name={project.site.siteName || project.companyName}
            iconUrl={project.site.iconUrl}
            color={project.site.themeColor}
            className="size-8 rounded-sm text-sm"
          />
          <span className="min-w-0">
            <span className="block truncate font-bold">{project.companyName}</span>
            <span className="block truncate text-xs text-muted">{project.site.domain}</span>
          </span>
        </span>
        <span className="line-clamp-2 text-sm text-ink-soft">{project.site.description ?? project.description}</span>
        <span className="mt-auto space-y-1.5">
          <span className="flex items-center justify-between text-sm">
            <ModeChip mode={project.site.mode} />
            <span className="tabular font-semibold">
              {project.pointsEarnedToday} / {project.dailyCap}
            </span>
          </span>
          <span className="block h-2 overflow-hidden rounded-sm bg-ink/10">
            <span
              className={cn("block h-full transition-[width]", done ? "bg-success" : "bg-brand")}
              style={{
                width: `${Math.min(100, (project.pointsEarnedToday / project.dailyCap) * 100)}%`,
              }}
            />
          </span>
        </span>
        <span
          className={cn(
            "inline-flex items-center justify-center gap-1.5 rounded-md px-4 py-2.5 text-sm font-semibold transition",
            done ? "bg-canvas text-ink-soft" : "bg-ink text-white group-hover:bg-brand",
          )}
        >
          {done
            ? "Watch again"
            : started
              ? `Keep watching (+${project.dailyCap - project.pointsEarnedToday})`
              : `Watch and earn ${project.dailyCap}`}
        </span>
      </span>
    </Link>
  );
}

/** Dónde se ve la web: dentro de LaunchCrown o en su propia ventana. */
export function ModeChip({ mode, dark = false }: { mode: "FRAME" | "WINDOW"; dark?: boolean }) {
  const Icon = mode === "FRAME" ? EyeIcon : WindowIcon;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-sm px-2 py-1 text-xs font-semibold",
        dark ? "bg-white/12 text-white/85" : "bg-canvas text-ink-soft",
      )}
      title={mode === "FRAME" ? "The website shows inside LaunchCrown" : "The website opens in its own window"}
    >
      <Icon className="size-3.5" />
      {mode === "FRAME" ? "Shows here" : "Opens separately"}
    </span>
  );
}

/** Una línea sobre cómo cuenta el tiempo; la explicación completa está en la guía. */
function HowItCounts() {
  return (
    <p className="border-l-4 border-brand pl-4 text-ink-soft">
      Points only count while you’re looking at the website: switching tabs or apps pauses the count, and ten tabs at
      once earn the same as one.{" "}
      <Link href="/how-it-works#watching" className="font-semibold text-brand hover:underline">
        How the count works
      </Link>
    </p>
  );
}
