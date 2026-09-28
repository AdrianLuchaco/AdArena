"use client";

import Image from "next/image";
import Link from "next/link";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import { formatPoints, prettyUrl } from "@/lib/format";
import type { TaskItem } from "@/lib/types";
import { Alert } from "../ui/Alert";
import { ButtonLink } from "../ui/Button";
import { ArrowRightIcon, CheckIcon } from "../icons";
import { SiteAvatar } from "../viewer/SiteAvatar";
import { ModeChip } from "./EarnProjectsView";
import { useEarnData } from "./EarnShell";
import { platformColor, platformSurface, PlatformBadge } from "./PlatformBadge";

/**
 * Bonus links: los enlaces que promocionan otros usuarios (canales, perfiles, webs). Cada uno da
 * sus puntos tras mirarlo 10 s, una vez al día.
 */
export function TasksView() {
  const { tasks: overview } = useEarnData();
  const { rules } = overview;
  const limitReached = overview.tasksDoneToday >= overview.tasksPerDay;
  const pending = overview.tasks.filter((task) => task.state !== "DONE");
  const done = overview.tasks.filter((task) => task.state === "DONE");

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-4xl">Community links</h2>
          <p className="mt-1 max-w-2xl text-ink-soft">
            Watch each link for {rules.taskMinSeconds} seconds and earn +{rules.taskRewardPoints}. You earn by watching;
            following or liking is up to you.{" "}
            <Link href="/promote" className="font-semibold text-brand hover:underline">
              Want your own link here? Promote it for free
            </Link>
          </p>
        </div>
        <p className="tabular rounded-sm bg-surface px-3 py-2 text-sm font-semibold ring-1 ring-line">
          Today: {overview.tasksDoneToday} of {overview.tasksPerDay}, {formatPoints(overview.earnedToday)}
        </p>
      </div>

      {limitReached && (
        <Alert tone="success" title={`You’ve done all ${overview.tasksPerDay} for today`}>
          There’ll be more tomorrow. Meanwhile, you can keep earning with the Arena websites.
        </Alert>
      )}

      {overview.tasks.length === 0 ? (
        <div className="rounded-lg border-2 border-dashed border-ink/30 px-6 py-14 text-center">
          <p className="font-display text-3xl font-black uppercase">No links right now</p>
          <p className="mx-auto mt-2 max-w-md text-ink-soft">Come back later, or be the first: promote your link for free.</p>
          <ButtonLink href="/promote" className="mt-6">
            Promote my link
          </ButtonLink>
        </div>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[...pending, ...done].map((task) => (
            <li key={task.id}>
              <TaskTile task={task} disabled={limitReached && task.state !== "DONE"} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function TaskTile({ task, disabled }: { task: TaskItem; disabled: boolean }) {
  const done = task.state === "DONE";
  const content = (
    <>
      <span className={cn("relative block aspect-[16/8] overflow-hidden", !task.site.imageUrl && platformSurface(task.platform))}>
        {task.site.imageUrl ? (
          <>
            <Image src={apiUrl(task.site.imageUrl)} alt="" fill sizes="(min-width: 1024px) 22rem, (min-width: 640px) 50vw, 100vw" className="object-cover" />
            <span className="absolute left-3 top-3">
              <PlatformBadge platform={task.platform} label={task.platformLabel} />
            </span>
          </>
        ) : (
          <span className="absolute inset-0 grid place-items-center font-display text-4xl font-black uppercase opacity-90">
            {task.platformLabel}
          </span>
        )}
        <span
          className={cn(
            "tabular absolute right-3 top-3 rounded-sm px-2.5 py-1 text-sm font-extrabold",
            done ? "bg-success text-white" : "bg-gold text-ink ring-1 ring-ink",
          )}
        >
          {done ? (
            <span className="inline-flex items-center gap-1">
              <CheckIcon className="size-4" /> Done
            </span>
          ) : (
            `+${task.rewardPoints}`
          )}
        </span>
      </span>
      <span className="flex flex-1 flex-col gap-2 p-4">
        <span className="flex items-center gap-2.5">
          <SiteAvatar
            name={task.site.siteName || task.title}
            iconUrl={task.site.iconUrl}
            color={task.site.themeColor ?? platformColor(task.platform)}
            className="size-8 rounded-sm text-sm"
          />
          <span className="min-w-0">
            <span className="block truncate font-bold">{task.title}</span>
            <span className="block truncate text-xs text-muted">{prettyUrl(task.url)}</span>
          </span>
        </span>
        {task.description && <span className="line-clamp-2 text-sm text-ink-soft">{task.description}</span>}
        <span className="mt-auto flex items-center justify-between gap-2 pt-2">
          <ModeChip mode={task.site.mode} />
          <span
            className={cn(
              "inline-flex items-center gap-1.5 rounded-md px-4 py-2 text-sm font-semibold transition",
              done ? "bg-canvas text-ink-soft" : "bg-ink text-white group-hover:bg-brand",
            )}
          >
            {done ? "Watched today" : task.state === "STARTED" ? "Finish watching" : "Watch and earn"}
            {!done && <ArrowRightIcon className="size-4" />}
          </span>
        </span>
      </span>
    </>
  );
  const className = cn(
    "group flex h-full flex-col overflow-hidden rounded-lg bg-surface ring-2 ring-ink/15 transition",
    done ? "opacity-70" : "hover:ring-ink",
  );
  if (disabled || done) {
    return <div className={className}>{content}</div>;
  }
  return (
    <Link href={`/watch/link/${task.id}`} className={className}>
      {content}
    </Link>
  );
}
