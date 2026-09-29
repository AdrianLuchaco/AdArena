"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError, getPoints } from "@/lib/api";
import { useArena } from "@/lib/arena-context";
import { cn } from "@/lib/cn";
import { formatDateTime, formatPoints } from "@/lib/format";
import type { MovementType, PointsOverview } from "@/lib/types";
import { AnimatedNumber } from "../fx/AnimatedNumber";
import { Alert } from "../ui/Alert";
import { ButtonLink } from "../ui/Button";
import { PageSpinner } from "../ui/Spinner";

const MOVEMENT_LABEL: Record<MovementType, string> = {
  SIGNUP_BONUS: "Welcome points",
  VIEW_REWARD: "Watching a project",
  TASK_REWARD: "Bonus link",
  BID_RESERVE: "Bid in the Race",
  WINNER_REFUND: "Bid refunded",
  BID_WIN_CHARGE: "Winning ad",
  BID_FORFEIT: "Lost half",
  ADMIN_ADJUSTMENT: "Adjustment by the LaunchCrown team",
  TEST_GRANT: "Test points",
  WINNER_BONUS: "Race winner bonus",
  TOP_UP: "Top-up (legacy)",
};

/** Mis puntos: cuántos tienes, cómo ganar más y tus últimos movimientos. */
export function PointsView() {
  const [overview, setOverview] = useState<PointsOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { onNotification, setPoints } = useArena();
  const load = useCallback(async () => {
    try {
      const result = await getPoints();
      setOverview(result);
      setPoints(result.availablePoints);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t load your points.");
    }
  }, [setPoints]);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  // Si llega un aviso (puntos devueltos, has ganado…), se actualiza solo
  useEffect(() => onNotification(() => void load()), [onNotification, load]);

  if (!overview) {
    return error ? <Alert tone="danger">{error}</Alert> : <PageSpinner label="Loading your points…" />;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_1.15fr]">
      <div className="space-y-6">
        <section className="space-y-5 rounded-lg bg-surface p-5 shadow-lift ring-2 ring-ink sm:p-7">
          <h2 className="text-4xl">Your Crown Points</h2>
          {error && <Alert tone="danger">{error}</Alert>}
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-md bg-night p-5 text-white">
              <p className="text-sm text-white/70">Available to bid</p>
              <p className="mt-2 font-display text-5xl font-black leading-none text-gold">
                <AnimatedNumber value={overview.availablePoints} format={formatPoints} className="tabular" />
              </p>
            </div>
            <div className="rounded-md bg-canvas p-5">
              <p className="text-sm text-muted">In bids or on hold</p>
              <p className="mt-2 font-display text-5xl font-black leading-none">
                <AnimatedNumber value={overview.reservedPoints} format={formatPoints} className="tabular" />
              </p>
            </div>
          </div>
          <p className="text-sm leading-relaxed text-ink-soft">
            When you bid, those points move to <strong>in bids</strong>. If you win, they’re spent when we publish your
            ad. If you don’t, half stays in play the next day and the other half is lost. Points aren’t money: they can’t
            be bought, sold or exchanged for anything outside LaunchCrown.
          </p>
          <div className="flex flex-wrap gap-2">
            <ButtonLink href="/earn">Earn more points</ButtonLink>
            <ButtonLink href="/race" variant="secondary">
              Go to the Race
            </ButtonLink>
          </div>
        </section>
      </div>

      <section className="overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
        <h2 className="px-5 pt-5 text-4xl sm:px-7 sm:pt-7">History</h2>
        {overview.movements.length === 0 ? (
          <p className="px-5 py-6 text-ink-soft sm:px-7">Nothing here yet.</p>
        ) : (
          <ul className="mt-3 divide-y divide-line">
            {overview.movements.map((movement, index) => (
              <li key={`${movement.createdAt}-${index}`} className="flex items-center gap-3 px-5 py-3.5 sm:px-7">
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{MOVEMENT_LABEL[movement.type] ?? movement.description}</p>
                  <p className="text-sm text-muted">{formatDateTime(movement.createdAt)}</p>
                </div>
                <div className="text-right">
                  <p className={cn("tabular font-bold", movement.amountPoints > 0 ? "text-success" : "text-ink")}>
                    {movement.amountPoints > 0 ? "+" : "−"}
                    {formatPoints(Math.abs(movement.amountPoints))}
                  </p>
                  <p className="tabular text-xs text-muted">Balance {formatPoints(movement.balanceAfterPoints)}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
