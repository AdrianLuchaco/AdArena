"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError, claimTask, getTasks, reportTask, startTask } from "@/lib/api";
import { useArena } from "@/lib/arena-context";
import { useAuth } from "@/lib/auth-context";
import { pointsText } from "@/lib/format";
import type { TaskItem, TasksOverview } from "@/lib/types";
import { Alert } from "../ui/Alert";
import { Button, ButtonLink } from "../ui/Button";
import { PageSpinner } from "../ui/Spinner";
import { useToast } from "../ui/Toaster";
import { FlagIcon } from "../icons";
import { SiteViewer, type ViewerEarning } from "../viewer/SiteViewer";
import { platformColor, PlatformBadge } from "./PlatformBadge";

/**
 * Créditos extra: ver el enlace que promociona otro usuario (su canal, su perfil, su web) y ganar
 * sus puntos tras 10 s mirándolo. Cada tarea, una vez al día.
 */
export function TaskViewer({ id }: { id: string }) {
  const { status: authStatus } = useAuth();
  const { setPoints } = useArena();
  const toast = useToast();
  const [overview, setOverview] = useState<TasksOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [started, setStarted] = useState(false);
  const [claimed, setClaimed] = useState<number | null>(null);
  const [floating, setFloating] = useState<number | null>(null);
  const [claimError, setClaimError] = useState<string | null>(null);
  const claiming = useRef(false);

  useEffect(() => {
    if (authStatus !== "authenticated") return;
    let active = true;
    getTasks()
      .then((result) => active && setOverview(result))
      .catch((e) => active && setError(e instanceof ApiError ? e.message : "We couldn't load this link."));
    return () => {
      active = false;
    };
  }, [authStatus]);

  const task: TaskItem | undefined = overview?.tasks.find((t) => t.id === id);
  const limitReached = overview ? overview.tasksDoneToday >= overview.tasksPerDay : false;
  const alreadyDone = task?.state === "DONE";

  const taskId = task?.id ?? null;

  // El servidor apunta cuándo abriste el enlace
  useEffect(() => {
    if (!taskId || alreadyDone || limitReached) return;
    let active = true;
    startTask(taskId)
      .then(() => active && setStarted(true))
      .catch((e) => active && setClaimError(e instanceof ApiError ? e.message : "We couldn't start this link."));
    return () => {
      active = false;
    };
  }, [taskId, alreadyDone, limitReached]);

  const onTicks = useCallback(() => {
    if (!task || claiming.current || claimed !== null) return;
    claiming.current = true;
    claimTask(task.id)
      .then((result) => {
        setClaimed(result.pointsAwarded);
        setPoints(result.availablePoints);
        setFloating(result.pointsAwarded);
        setTimeout(() => setFloating(null), 1400);
        toast({ tone: "success", title: `+${pointsText(result.pointsAwarded)}`, message: `For watching "${task.title}".` });
      })
      .catch((e) => {
        if (e instanceof ApiError && e.code === "TASK_TOO_SOON") return; // aún no: seguirá contando
        setClaimError(e instanceof ApiError ? e.message : "We couldn't add the points.");
      })
      .finally(() => {
        claiming.current = false;
      });
  }, [task, claimed, setPoints, toast]);

  const next = useMemo(() => {
    const candidate = overview?.tasks.find((t) => t.id !== id && t.state !== "DONE");
    if (candidate && !limitReached) return { href: `/watch/link/${candidate.id}`, label: "Next link" };
    return { href: "/earn", label: "Arena websites" };
  }, [overview, id, limitReached]);

  if (authStatus === "anonymous") {
    return (
      <div className="mx-auto w-full max-w-lg flex-1 px-4 py-24 text-center">
        <h1 className="text-4xl font-black">Log in to earn bonus points</h1>
        <p className="mt-2 text-ink-soft">Create a free account and start with 200 Arena Points.</p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <ButtonLink href={`/signup?next=/watch/link/${id}`}>Create free account</ButtonLink>
          <ButtonLink href={`/login?next=/watch/link/${id}`} variant="secondary">
            I have an account
          </ButtonLink>
        </div>
      </div>
    );
  }
  if (error) {
    return (
      <div className="mx-auto w-full max-w-lg flex-1 px-4 py-24">
        <Alert tone="danger">{error}</Alert>
      </div>
    );
  }
  if (!overview) return <PageSpinner label="Opening the link…" />;
  if (!task) {
    return (
      <div className="mx-auto w-full max-w-lg flex-1 px-4 py-24 text-center">
        <h1 className="text-4xl font-black">This link is no longer available</h1>
        <p className="mt-2 text-ink-soft">Its owner paused it or we removed it. Try another one.</p>
        <ButtonLink href="/earn/links" className="mt-6">
          See bonus links
        </ButtonLink>
      </div>
    );
  }

  const rules = overview.rules;
  const base = {
    earned: claimed ?? (alreadyDone ? task.rewardPoints : 0),
    goal: task.rewardPoints,
    tickMs: rules.taskMinSeconds * 1000,
    onTicks,
    floating,
    earningText: `+${task.rewardPoints} for watching it ${rules.taskMinSeconds} seconds`,
  };
  let earning: ViewerEarning;
  if (claimed !== null || alreadyDone) {
    earning = {
      ...base,
      state: "done",
      message: {
        title: claimed !== null ? `+${claimed} points!` : "Already watched today",
        text: "You can earn them again tomorrow. Liked it? Follow them if you want: it's optional.",
      },
    };
  } else if (limitReached) {
    earning = {
      ...base,
      state: "info",
      message: { title: `You did all ${overview.tasksPerDay} links for today`, text: "More tomorrow. You can keep watching." },
    };
  } else if (claimError) {
    earning = { ...base, state: "info", message: { title: "We can't count right now", text: claimError } };
  } else if (!started) {
    earning = { ...base, state: "loading" };
  } else {
    earning = { ...base, state: "earning" };
  }

  return (
    <SiteViewer
      site={{ ...task.site, themeColor: task.site.themeColor ?? platformColor(task.platform) }}
      name={task.title}
      badge={<PlatformBadge platform={task.platform} label={task.platformLabel} />}
      exitHref="/earn/links"
      next={next}
      earning={earning}
      details={<TaskDetails task={task} />}
    />
  );
}

/** La ficha de la tarea y el botón para denunciarla. */
function TaskDetails({ task }: { task: TaskItem }) {
  const [reason, setReason] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function send(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSending(true);
    try {
      await reportTask(task.id, reason);
      setSent(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn't send the report.");
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <PlatformBadge platform={task.platform} label={task.platformLabel} />
        <h2 className="mt-3 text-2xl font-extrabold">{task.title}</h2>
        {task.description && <p className="mt-2 leading-relaxed text-ink-soft">{task.description}</p>}
        <p className="mt-2 break-all text-sm text-muted">{task.url}</p>
      </div>
      <p className="rounded-md bg-canvas p-4 text-sm leading-relaxed text-ink-soft">
        You earn the points for <strong className="text-ink">watching</strong> the link. Following or liking is up to
        you: it doesn&apos;t earn points and it&apos;s optional.
      </p>
      <section className="rounded-md p-4 ring-1 ring-line">
        <p className="flex items-center gap-2 font-semibold">
          <FlagIcon className="size-4 text-danger" /> Something wrong with this link?
        </p>
        {sent ? (
          <p className="mt-2 text-sm text-success">Thanks, we&apos;ll review it. With 3 reports it&apos;s hidden automatically.</p>
        ) : (
          <form onSubmit={send} className="mt-3 space-y-2">
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={300}
              placeholder="Broken, misleading, dangerous…"
              className="h-11 w-full rounded-md bg-surface px-3 ring-1 ring-line outline-none focus:ring-2 focus:ring-brand"
              aria-label="Reason for the report"
            />
            {error && <p className="text-sm text-danger">{error}</p>}
            <Button type="submit" size="sm" variant="dark" loading={sending} disabled={reason.trim().length < 3}>
              Report
            </Button>
          </form>
        )}
      </section>
    </div>
  );
}
