"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError, getAdminTasks, hideTask, restoreTask } from "@/lib/api";
import { cn } from "@/lib/cn";
import { formatDateTime, prettyUrl } from "@/lib/format";
import type { AdminTask, PromotionStatus } from "@/lib/types";
import { PlatformBadge } from "../earn/PlatformBadge";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { TextField } from "../ui/Field";
import { PageSpinner } from "../ui/Spinner";
import { useToast } from "../ui/Toaster";

const STATUS: Record<PromotionStatus, { text: string; className: string }> = {
  ACTIVE: { text: "Live", className: "bg-success-soft text-success" },
  PAUSED: { text: "Paused by its owner", className: "bg-canvas text-muted ring-1 ring-line" },
  HIDDEN: { text: "Hidden", className: "bg-danger-soft text-danger" },
};

const PLATFORM_LABEL: Record<string, string> = {
  YOUTUBE: "YouTube", X: "X (Twitter)", INSTAGRAM: "Instagram", TIKTOK: "TikTok", TWITCH: "Twitch",
  LINKEDIN: "LinkedIn", FACEBOOK: "Facebook", GITHUB: "GitHub", WEB: "Website",
};

/**
 * Promociones de los usuarios (Bonus links). Se publican al momento; aquí revisas las
 * denunciadas y ocultas las que no cumplan las normas (su dueño recibe el motivo).
 * Con 3 denuncias de usuarios distintos, una promoción se oculta sola hasta que la revises.
 */
export function AdminTasksView() {
  const [tasks, setTasks] = useState<AdminTask[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setTasks(await getAdminTasks());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t load the promotions.");
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!tasks) return <PageSpinner label="Loading promotions…" />;

  return (
    <div className="space-y-5">
      <Alert tone="info" title="How it works">
        Promotions go live straight away. Focus on the reported ones: open the link and, if it breaks the rules
        (misleading, illegal or adult content, malware…), hide it with a reason. After 3 reports they hide themselves
        until you review them.
      </Alert>
      {tasks.length === 0 ? (
        <p className="rounded-md bg-surface px-5 py-6 text-ink-soft ring-1 ring-line">No promotions yet.</p>
      ) : (
        <ul className="space-y-3">
          {tasks.map((task) => (
            <TaskRow key={task.id} task={task} onChanged={load} />
          ))}
        </ul>
      )}
    </div>
  );
}

function TaskRow({ task, onChanged }: { task: AdminTask; onChanged: () => Promise<void> }) {
  const toast = useToast();
  const [hiding, setHiding] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  async function run(action: () => Promise<void>, message: string) {
    setBusy(true);
    try {
      await action();
      toast({ tone: "success", title: message });
      await onChanged();
    } catch (e) {
      toast({ tone: "danger", title: "That didn’t work", message: e instanceof ApiError ? e.message : undefined });
    } finally {
      setBusy(false);
    }
  }

  const status = STATUS[task.status];
  return (
    <li className={cn("rounded-lg bg-surface p-5 ring-2", task.reports > 0 ? "ring-danger" : "ring-ink/15")}>
      <div className="flex flex-wrap items-center gap-2">
        <PlatformBadge platform={task.platform} label={PLATFORM_LABEL[task.platform] ?? task.platform} />
        <span className={cn("rounded-sm px-2 py-0.5 text-xs font-semibold", status.className)}>{status.text}</span>
        {task.reports > 0 && (
          <span className="rounded-sm bg-danger-soft px-2 py-0.5 text-xs font-bold text-danger">
            {task.reports} {task.reports === 1 ? "report" : "reports"}
          </span>
        )}
        <span className="ml-auto text-sm text-muted">{formatDateTime(task.createdAt)}</span>
      </div>
      <p className="mt-2 font-bold">{task.title}</p>
      {task.description && <p className="text-sm text-ink-soft">{task.description}</p>}
      <a href={task.url} target="_blank" rel="noopener noreferrer" className="mt-1 block truncate text-sm text-brand hover:underline">
        {prettyUrl(task.url)}
      </a>
      <p className="mt-1 text-sm text-muted">
        By {task.ownerName} ({task.ownerEmail}), {task.completions} visits
      </p>
      {task.hiddenReason && <p className="mt-2 text-sm text-danger">Hidden: {task.hiddenReason}</p>}
      {task.reportReasons.length > 0 && (
        <ul className="mt-2 list-disc space-y-0.5 pl-5 text-sm text-ink-soft">
          {task.reportReasons.map((text, index) => (
            <li key={index}>{text}</li>
          ))}
        </ul>
      )}

      <div className="mt-4">
        {task.status === "HIDDEN" ? (
          <Button size="sm" variant="secondary" loading={busy} onClick={() => void run(() => restoreTask(task.id), "Promotion live again")}>
            Restore
          </Button>
        ) : hiding ? (
          <div className="grid gap-2 sm:grid-cols-[1fr_auto] sm:items-end">
            <TextField label="Reason (we send it to the owner)" value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} />
            <div className="flex gap-2">
              <Button size="sm" variant="dark" loading={busy} disabled={reason.trim().length < 3}
                onClick={() => void run(() => hideTask(task.id, reason), "Promotion hidden")}>
                Hide
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setHiding(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <Button size="sm" variant="secondary" onClick={() => setHiding(true)}>
            Hide
          </Button>
        )}
      </div>
    </li>
  );
}
