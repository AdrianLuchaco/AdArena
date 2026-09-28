"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError, approveAdSlot, getPendingAdSlots, getRecentAdSlots, refreshAdSlotShowcase, rejectAdSlot } from "@/lib/api";
import { useArena } from "@/lib/arena-context";
import { cn } from "@/lib/cn";
import { formatDateTime, formatPoints, formatLongDate, ordinal } from "@/lib/format";
import { useCountdown } from "@/lib/hooks";
import type { AdminAdSlot, AdSlotStatus } from "@/lib/types";
import { AdView } from "../ad/AdView";
import { WinnerShowcase } from "../ad/WinnerShowcase";
import { RefreshIcon } from "../icons";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { TextArea } from "../ui/Field";
import { PageSpinner } from "../ui/Spinner";
import { useToast } from "../ui/Toaster";

const STATUS: Record<AdSlotStatus, { text: string; className: string }> = {
  PENDING_REVIEW: { text: "Pending", className: "bg-warning-soft text-warning" },
  APPROVED: { text: "Approved", className: "bg-success-soft text-success" },
  REJECTED: { text: "Rejected", className: "bg-danger-soft text-danger" },
  EXPIRED: { text: "Expired (refunded)", className: "bg-canvas text-muted ring-1 ring-line" },
};

/**
 * Moderación del ganador. Aprobar: se gastan sus puntos, recibe su premio de 500 y su presentación
 * sale en portada TAL CUAL la ves aquí (se congela al aprobar). Rechazar: se le devuelve el 100 % y
 * pasa a revisión el siguiente clasificado. Si no decides antes de que acabe su día, se le
 * devuelven sus puntos automáticamente.
 */
export function ModerationView() {
  const [pending, setPending] = useState<AdminAdSlot[] | null>(null);
  const [recent, setRecent] = useState<AdminAdSlot[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [p, r] = await Promise.all([getPendingAdSlots(), getRecentAdSlots()]);
      setPending(p);
      setRecent(r);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t load moderation.");
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  if (!pending) return error ? <Alert tone="danger">{error}</Alert> : <PageSpinner label="Loading…" />;

  return (
    <div className="space-y-10">
      <section className="space-y-5">
        <h2 className="text-4xl">To moderate</h2>
        {pending.length === 0 ? (
          <p className="rounded-md bg-surface px-5 py-6 text-ink-soft ring-1 ring-line">
            No ads waiting. When today’s Arena closes, the winner shows up here.
          </p>
        ) : (
          pending.map((slot) => <PendingSlot key={slot.id} slot={slot} onDone={load} />)
        )}
      </section>

      {recent.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-4xl">Recent decisions</h2>
          <ul className="divide-y divide-line overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
            {recent.map((slot) => (
              <li key={slot.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-4">
                <span className={cn("rounded-sm px-2 py-0.5 text-xs font-semibold", STATUS[slot.status].className)}>
                  {STATUS[slot.status].text}
                </span>
                <span className="font-semibold">{slot.ad?.companyName ?? "—"}</span>
                <span className="tabular text-sm text-muted">{formatPoints(slot.amountPoints)}</span>
                <span className="ml-auto text-sm text-muted">
                  {slot.reviewedAt ? formatDateTime(slot.reviewedAt) : formatDateTime(slot.endsAt)}
                </span>
                {slot.rejectionReason && <p className="w-full text-sm text-ink-soft">Reason: {slot.rejectionReason}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function PendingSlot({ slot, onDone }: { slot: AdminAdSlot; onDone: () => Promise<void> }) {
  const toast = useToast();
  const { reload } = useArena();
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<"approve" | "reject" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [rereading, setRereading] = useState(false);

  async function reread() {
    setRereading(true);
    try {
      await refreshAdSlotShowcase(slot.id);
      toast({ tone: "info", title: "Reading their website", message: "The presentation will update in a few seconds." });
      setTimeout(() => {
        void onDone().finally(() => setRereading(false));
      }, 8000);
    } catch (e) {
      toast({ tone: "danger", title: "That didn’t work", message: e instanceof ApiError ? e.message : undefined });
      setRereading(false);
    }
  }

  async function approve() {
    setBusy("approve");
    setError(null);
    try {
      await approveAdSlot(slot.id);
      toast({ tone: "success", title: "Ad approved", message: `${slot.ad?.companyName} is now on the homepage.` });
      await Promise.all([onDone(), reload()]);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn’t approve it.");
    } finally {
      setBusy(null);
    }
  }

  async function reject() {
    setBusy("reject");
    setError(null);
    try {
      const result = await rejectAdSlot(slot.id, reason);
      toast({
        tone: "info",
        title: "Ad rejected",
        message: `${formatPoints(result.refundedPoints)} refunded.${result.promotedSlotId ? " The runner-up moves to review." : ""}`,
      });
      await Promise.all([onDone(), reload()]);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn’t reject it.");
    } finally {
      setBusy(null);
    }
  }

  const hoursLeft = Math.floor(useCountdown(slot.endsAt) / 3_600_000);

  return (
    <article className="grid gap-6 rounded-lg bg-surface p-5 shadow-lift ring-2 ring-ink sm:p-7 lg:grid-cols-[1.2fr_1fr]">
      <div className="space-y-3">
        {!slot.ad ? (
          <Alert tone="warning">The copy of this ad wasn’t saved.</Alert>
        ) : slot.showcase ? (
          <WinnerShowcase ad={slot.ad} showcase={slot.showcase} variant="preview" winner={{ wonWithPoints: slot.amountPoints, roundDate: slot.roundDate ?? slot.startsAt.slice(0, 10) }} />
        ) : (
          <AdView variant="preview" ad={slot.ad} previewLabel="How it will look on the homepage" />
        )}
        <p className="text-sm text-ink-soft">
          {slot.showcase
            ? "Their animated presentation, built from what we read on their website. Check every scene: approving publishes exactly this."
            : "We haven’t read their website yet: if you approve now, their classic ad (above) will be shown."}
        </p>
        <Button size="sm" variant="secondary" loading={rereading} onClick={() => void reread()}>
          <RefreshIcon className="size-4" /> Read their website again
        </Button>
      </div>
      <div className="flex flex-col gap-4">
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div className="col-span-2">
            <dt className="text-muted">Advertiser</dt>
            <dd className="font-semibold">
              {slot.userName} <span className="font-normal text-muted">({slot.userEmail})</span>
            </dd>
          </div>
          <div>
            <dt className="text-muted">Bid</dt>
            <dd className="tabular font-display text-3xl font-black">{formatPoints(slot.amountPoints)}</dd>
          </div>
          <div>
            <dt className="text-muted">Place</dt>
            <dd className="font-semibold">
              {slot.candidateRank === 1 ? "1st (won outright)" : `${ordinal(slot.candidateRank)} (those above were rejected)`}
            </dd>
          </div>
          <div className="col-span-2">
            <dt className="text-muted">Their day on the homepage</dt>
            <dd>
              From {formatDateTime(slot.startsAt)} to {formatDateTime(slot.endsAt)}
              {slot.roundDate && <span className="text-muted">. Arena of {formatLongDate(slot.roundDate)}</span>}
            </dd>
            <dd className={cn("mt-1 font-semibold", hoursLeft < 3 ? "text-danger" : "text-warning")}>
              {hoursLeft} h left: if you don’t decide, their points will be refunded.
            </dd>
          </div>
        </dl>

        <p className="text-sm text-ink-soft">
          Check that the image and text are appropriate and that their website works (open it with the ad’s button).
        </p>

        {error && <Alert tone="danger">{error}</Alert>}

        {rejecting ? (
          <div className="space-y-3">
            <TextArea
              label="Reason for rejection (we email it to them)"
              value={reason}
              maxChars={500}
              maxLength={500}
              placeholder="For example: the image contains content that isn’t allowed."
              onChange={(e) => setReason(e.target.value)}
            />
            <div className="flex flex-wrap gap-2">
              <Button variant="dark" loading={busy === "reject"} disabled={reason.trim().length < 3} onClick={() => void reject()}>
                Reject and refund their points
              </Button>
              <Button variant="ghost" onClick={() => setRejecting(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <div className="mt-auto flex flex-wrap gap-2">
            <Button size="lg" loading={busy === "approve"} onClick={() => void approve()}>
              Approve and publish
            </Button>
            <Button size="lg" variant="secondary" onClick={() => setRejecting(true)}>
              Reject
            </Button>
          </div>
        )}
      </div>
    </article>
  );
}
