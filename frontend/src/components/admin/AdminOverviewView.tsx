"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ApiError, getAdminOverview } from "@/lib/api";
import { cn } from "@/lib/cn";
import { formatDateTime, formatPoints } from "@/lib/format";
import type { AdminOverview } from "@/lib/types";
import { Alert } from "../ui/Alert";
import { PageSpinner } from "../ui/Spinner";

/** Resumen del negocio y lo que tienes pendiente. */
export function AdminOverviewView() {
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setOverview(await getAdminOverview());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t load the overview.");
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  if (!overview) return error ? <Alert tone="danger">{error}</Alert> : <PageSpinner label="Loading the overview…" />;

  const balanced = overview.ledgerMismatches === 0;

  return (
    <div className="space-y-6">
      {(overview.pendingAdSlots > 0 || overview.reportedTasks > 0) && (
        <div className="grid gap-3 sm:grid-cols-2">
          {overview.pendingAdSlots > 0 && (
            <PendingLink href="/admin/moderation" count={overview.pendingAdSlots}
              text={overview.pendingAdSlots === 1 ? "winning ad to moderate" : "winning ads to moderate"} />
          )}
          {overview.reportedTasks > 0 && (
            <PendingLink href="/admin/promotions" count={overview.reportedTasks}
              text={overview.reportedTasks === 1 ? "reported promotion to review" : "reported promotions to review"} />
          )}
        </div>
      )}

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Points issued today" value={formatPoints(overview.issuedTodayPoints)} strong />
        <Stat label="Points issued in total" value={formatPoints(overview.issuedPoints)} />
        <Stat label="Points spent in the Arena" value={formatPoints(overview.spentPoints)} />
        <Stat label="Users’ points" value={formatPoints(overview.usersAvailablePoints + overview.usersReservedPoints)} />
      </section>

      <p className="text-sm text-muted">
        {overview.users} accounts, {overview.activeTasks} live promotions, {overview.hiddenTasks} hidden,{" "}
        {formatPoints(overview.usersReservedPoints)} in bids or on hold right now.
      </p>

      <section className="grid gap-3 lg:grid-cols-3">
        <div className={cn("rounded-lg p-5 ring-2", balanced ? "bg-success-soft ring-success" : "bg-danger-soft ring-danger")}>
          <p className={cn("font-bold", balanced ? "text-success" : "text-danger")}>
            {balanced ? "The points balance" : `${overview.ledgerMismatches} accounts don’t balance!`}
          </p>
          <p className="mt-1 text-sm text-ink-soft">
            {balanced
              ? "Every point issued is in someone’s account or has been spent in the Arena."
              : "Something is wrong in the ledger. Check the audit log and investigate."}
          </p>
        </div>
        <div className="rounded-lg bg-surface p-5 ring-2 ring-ink/15">
          <p className="font-bold">{overview.emailsDelivered ? "Emails are being sent" : "Emails are NOT being sent"}</p>
          <p className="mt-1 text-sm text-ink-soft">
            {overview.emailsDelivered
              ? overview.failedEmails > 0
                ? `${overview.failedEmails} emails have failed 5 times. Check your email provider.`
                : "No delivery errors."
              : "No mail server configured (SMTP_HOST): emails are only written to the log."}
          </p>
        </div>
        <div className="rounded-lg bg-surface p-5 ring-2 ring-ink/15">
          <p className="font-bold">Today’s Arena</p>
          {overview.openRound ? (
            <p className="mt-1 text-sm text-ink-soft">
              {overview.openRound.participants} projects, {formatPoints(overview.openRound.totalPoints)} in play, closes{" "}
              {formatDateTime(overview.openRound.endsAt)}
            </p>
          ) : (
            <p className="mt-1 text-sm text-ink-soft">None open right now (one opens automatically in a few seconds).</p>
          )}
        </div>
      </section>
    </div>
  );
}

function PendingLink({ href, count, text }: { href: string; count: number; text: string }) {
  return (
    <Link href={href} className="flex items-center gap-4 rounded-lg bg-gold p-5 ring-2 ring-ink transition hover:shadow-hard">
      <span className="tabular font-display text-5xl font-black leading-none">{count}</span>
      <span className="font-semibold">{text}</span>
    </Link>
  );
}

function Stat({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={cn("rounded-lg p-5", strong ? "bg-night text-white ring-2 ring-ink" : "bg-surface ring-2 ring-ink/15")}>
      <p className={cn("text-sm", strong ? "text-white/70" : "text-muted")}>{label}</p>
      <p className="tabular mt-2 font-display text-4xl font-black leading-none">{value}</p>
    </div>
  );
}
