"use client";

import { useEffect, useState } from "react";
import { ApiError, getMyBids } from "@/lib/api";
import { cn } from "@/lib/cn";
import { formatDateTime, formatPoints } from "@/lib/format";
import type { BidType, MyBid } from "@/lib/types";
import { Alert } from "../ui/Alert";
import { ButtonLink } from "../ui/Button";
import { PageSpinner } from "../ui/Spinner";

const TYPE_LABEL: Record<BidType, { text: string; className: string }> = {
  BID: { text: "Bid", className: "bg-brand-soft text-brand-dark" },
  CARRY_OVER: { text: "Carried over", className: "bg-success-soft text-success" },
  CARRY_REVERSAL: { text: "Carry-over withdrawn", className: "bg-canvas text-muted" },
};

/** Historial de tus pujas, de la más reciente a la más antigua. */
export function MyBidsView() {
  const [bids, setBids] = useState<MyBid[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getMyBids(100)
      .then((result) => active && setBids(result))
      .catch((e) => active && setError(e instanceof ApiError ? e.message : "We couldn’t load your bids."));
    return () => {
      active = false;
    };
  }, []);

  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!bids) return <PageSpinner label="Loading your bids…" />;

  if (bids.length === 0) {
    return (
      <div className="rounded-lg border-2 border-dashed border-ink/30 px-6 py-14 text-center">
        <p className="font-display text-3xl font-black uppercase">No bids yet</p>
        <p className="mx-auto mt-2 max-w-md text-ink-soft">Once you bid, every move shows up here.</p>
        <ButtonLink href="/arena" className="mt-6">
          Go to the Arena
        </ButtonLink>
      </div>
    );
  }

  return (
    <section className="overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
      <ul className="divide-y divide-line">
        {bids.map((bid, index) => {
          const type = TYPE_LABEL[bid.type];
          return (
            <li key={`${bid.createdAt}-${index}`} className="flex flex-wrap items-center gap-3 px-5 py-4 sm:px-7">
              <span className={cn("rounded-sm px-2.5 py-1 text-xs font-semibold", type.className)}>{type.text}</span>
              <span className="text-sm text-muted">{formatDateTime(bid.createdAt)}</span>
              <span className="ml-auto text-right">
                <span className="tabular block font-bold">
                  {bid.amountPoints > 0 ? "+" : ""}
                  {formatPoints(bid.amountPoints)}
                </span>
                <span className="tabular block text-xs text-muted">Total: {formatPoints(bid.totalAfterPoints)}</span>
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
