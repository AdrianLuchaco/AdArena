"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { ApiError, getEarnOverview, getTasks } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { cn } from "@/lib/cn";
import { formatPoints } from "@/lib/format";
import type { EarnOverview, TasksOverview } from "@/lib/types";
import { AnimatedNumber } from "../fx/AnimatedNumber";
import { Alert } from "../ui/Alert";
import { ButtonLink } from "../ui/Button";
import { PageSpinner } from "../ui/Spinner";
import { ArrowRightIcon } from "../icons";

interface EarnData {
  overview: EarnOverview;
  tasks: TasksOverview;
  reload: () => Promise<void>;
}

const EarnDataContext = createContext<EarnData | null>(null);

/** Los datos de "Earn points" (proyectos de hoy y enlaces extra), cargados una vez para todas sus páginas. */
export function useEarnData(): EarnData {
  const data = useContext(EarnDataContext);
  if (!data) throw new Error("useEarnData must be used inside EarnShell");
  return data;
}

/**
 * Estructura de "Earn points": tu marcador del día (tus puntos y lo ganado frente a lo que aún
 * puedes ganar) y dos pestañas, una por cada forma de ganar. Sin sesión, invita a crear cuenta.
 */
export function EarnShell({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const pathname = usePathname();
  const [overview, setOverview] = useState<EarnOverview | null>(null);
  const [tasks, setTasks] = useState<TasksOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [nextOverview, nextTasks] = await Promise.all([getEarnOverview(), getTasks()]);
      setOverview(nextOverview);
      setTasks(nextTasks);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t load your points.");
    }
  }, []);

  useEffect(() => {
    if (status !== "authenticated") return;
    const timer = setTimeout(() => void reload(), 0);
    return () => clearTimeout(timer);
  }, [status, reload]);

  return (
    <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 sm:py-10">
      <div className="max-w-2xl">
        <h1 className="text-6xl sm:text-7xl">Earn points</h1>
        <p className="mt-3 text-lg leading-relaxed text-ink-soft">
          Watch the websites of today’s projects and the links other people promote. Every second you watch counts.
          Then{" "}
          <Link href="/arena" className="font-semibold text-ink underline decoration-brand decoration-2 underline-offset-4">
            bid your points for the homepage
          </Link>
          . Free, no real money.
        </p>
      </div>

      {status === "loading" ? (
        <PageSpinner />
      ) : status === "anonymous" ? (
        <AnonymousInvite pathname={pathname} />
      ) : error ? (
        <Alert tone="danger" className="mt-8">
          {error}
        </Alert>
      ) : !overview || !tasks ? (
        <PageSpinner label="Loading your points…" />
      ) : (
        <EarnDataContext.Provider value={{ overview, tasks, reload }}>
          <DayMeter overview={overview} tasks={tasks} />
          <Tabs pathname={pathname} overview={overview} tasks={tasks} />
          <div className="mt-8">{children}</div>
        </EarnDataContext.Provider>
      )}
    </div>
  );
}

/** Tu marcador del día: tus puntos y lo ganado hoy frente a lo que aún puedes ganar. */
function DayMeter({ overview, tasks }: { overview: EarnOverview; tasks: TasksOverview }) {
  const others = overview.projects.filter((p) => !p.own);
  const viewsMax = others.reduce((sum, p) => sum + p.dailyCap, 0);
  const pendingTasks = tasks.tasks.filter((t) => t.state !== "DONE").length;
  const tasksLeft = Math.max(0, Math.min(tasks.tasksPerDay - tasks.tasksDoneToday, pendingTasks));
  const tasksMax = tasks.earnedToday + tasksLeft * overview.rules.taskRewardPoints;
  const earned = overview.earnedTodayFromViews + tasks.earnedToday;
  const max = Math.max(1, viewsMax + tasksMax);

  return (
    <section className="mt-8 grid gap-6 rounded-2xl bg-brand p-6 text-white shadow-lift ring-2 ring-ink sm:p-8 lg:grid-cols-[auto_1fr] lg:items-center lg:gap-12">
      <div>
        <p className="text-sm text-white/85">Your points</p>
        <p className="mt-1 font-display text-6xl font-black leading-none text-gold">
          <AnimatedNumber value={overview.availablePoints} format={formatPoints} className="tabular" />
        </p>
        {overview.reservedPoints > 0 && (
          <p className="mt-1 text-sm text-white/75">plus {formatPoints(overview.reservedPoints)} in bids</p>
        )}
        <Link href="/arena" className="mt-3 inline-flex items-center gap-1.5 text-sm font-semibold text-white hover:text-gold">
          Bid them in the Arena <ArrowRightIcon className="size-4" />
        </Link>
      </div>
      <div>
        <p className="flex flex-wrap items-baseline justify-between gap-2">
          <span className="font-semibold">
            Earned today: <span className="tabular text-gold">{formatPoints(earned)}</span>
          </span>
          <span className="tabular text-sm text-white/80">of {formatPoints(viewsMax + tasksMax)} available today</span>
        </p>
        <div
          className="mt-3 flex h-3.5 overflow-hidden rounded-full bg-ink/35"
          role="img"
          aria-label={`Today: ${earned} of ${viewsMax + tasksMax} possible points`}
        >
          <span className="h-full bg-gold transition-[width] duration-700" style={{ width: `${(overview.earnedTodayFromViews / max) * 100}%` }} />
          <span className="h-full bg-white transition-[width] duration-700" style={{ width: `${(tasks.earnedToday / max) * 100}%` }} />
        </div>
        <ul className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-sm text-white/85">
          <li className="flex items-center gap-2">
            <span className="size-2.5 bg-gold" />
            Arena websites: <span className="tabular font-semibold text-white">{overview.earnedTodayFromViews} / {viewsMax}</span>
          </li>
          <li className="flex items-center gap-2">
            <span className="size-2.5 bg-white" />
            Bonus links: <span className="tabular font-semibold text-white">{tasks.earnedToday} / {tasksMax}</span>
          </li>
        </ul>
      </div>
    </section>
  );
}

/** Las dos formas de ganar, como pestañas. */
function Tabs({ pathname, overview, tasks }: { pathname: string; overview: EarnOverview; tasks: TasksOverview }) {
  const others = overview.projects.filter((p) => !p.own);
  const projectsLeft = others.filter((p) => p.pointsEarnedToday < p.dailyCap).length;
  const tasksLeft = Math.max(
    0,
    Math.min(tasks.tasksPerDay - tasks.tasksDoneToday, tasks.tasks.filter((t) => t.state !== "DONE").length),
  );
  const items = [
    {
      href: "/earn",
      title: "Websites",
      detail: `Arena projects, up to ${overview.rules.dailyCapPerProject} pts`,
      count: projectsLeft,
    },
    {
      href: "/earn/links",
      title: "Bonus links",
      detail: `+${overview.rules.taskRewardPoints} pts each`,
      count: tasksLeft,
    },
  ];
  return (
    <nav className="mt-6 flex border-b-2 border-ink" aria-label="Ways to earn points">
      {items.map((item) => {
        const active = pathname === item.href;
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "-mb-0.5 flex min-w-0 flex-1 items-center gap-2 rounded-t-md border-2 px-3 py-3 transition sm:flex-none sm:gap-3 sm:px-6",
              active ? "border-ink border-b-canvas bg-canvas" : "border-transparent text-ink-soft hover:text-ink",
            )}
          >
            <span className="min-w-0">
              <span className="block truncate font-display text-xl font-black uppercase leading-none sm:text-2xl">{item.title}</span>
              <span className="mt-1 block truncate text-xs text-muted">{item.detail}</span>
            </span>
            <span
              className={cn(
                "tabular grid min-w-7 shrink-0 place-items-center rounded-sm px-1.5 py-0.5 text-sm font-bold",
                item.count > 0 ? "bg-brand text-white" : "bg-ink/10 text-ink-soft",
              )}
              aria-label={`${item.count} left today`}
            >
              {item.count}
            </span>
          </Link>
        );
      })}
    </nav>
  );
}

function AnonymousInvite({ pathname }: { pathname: string }) {
  return (
    <div className="track mt-8 rounded-2xl p-6 text-white shadow-lift ring-2 ring-ink sm:p-10">
      <p className="font-display text-5xl font-black uppercase leading-none">Start with 200 free points</p>
      <p className="mt-4 max-w-xl text-white/85">
        Sign up and we give you 200 Arena Points. Then every 10 seconds you watch a project’s website earns you 10 more
        (up to 100 per website a day).
      </p>
      <div className="mt-8 flex flex-wrap gap-3">
        <ButtonLink href={`/signup?next=${pathname}`} size="lg" variant="gold">
          Sign up free
        </ButtonLink>
        <ButtonLink href={`/login?next=${pathname}`} size="lg" variant="glass">
          Log in
        </ButtonLink>
      </div>
    </div>
  );
}
