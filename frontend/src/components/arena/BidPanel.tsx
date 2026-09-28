"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, getMyArenaStatus, placeBid } from "@/lib/api";
import { useArena } from "@/lib/arena-context";
import { useAuth } from "@/lib/auth-context";
import { pointsToInput, formatPoints, ordinal, parsePoints } from "@/lib/format";
import type { MyArenaStatus, RoundSummary } from "@/lib/types";
import { AnimatedNumber } from "../fx/AnimatedNumber";
import { Alert } from "../ui/Alert";
import { Button, ButtonLink } from "../ui/Button";
import { CrownIcon, InfoIcon, WalletIcon } from "../icons";

const RULES_ACK_KEY = "adarena_rules_ack_v1";
const QUICK_AMOUNTS = [100, 500, 1_000, 2_500];

function readRulesAck(): boolean {
  try {
    return typeof window !== "undefined" && window.localStorage.getItem(RULES_ACK_KEY) === "1";
  } catch {
    return false;
  }
}

/** Tarjeta "Your bid": tu situación, tus puntos y el formulario para pujar o incrementar. */
export function BidPanel({ round }: { round: RoundSummary | null }) {
  const { status: authStatus } = useAuth();
  const { onNotification } = useArena();
  const [me, setMe] = useState<MyArenaStatus | null>(null);

  const refresh = useCallback(async () => {
    try {
      setMe(await getMyArenaStatus());
    } catch {
      // Se reintenta con la siguiente actualización de la Arena
    }
  }, []);

  // Al entrar, cuando alguien puja (cambia el ranking) y cuando nos superan: actualizar "tu puja"
  useEffect(() => {
    if (authStatus !== "authenticated") return;
    const timer = setTimeout(() => void refresh(), 0);
    return () => clearTimeout(timer);
  }, [authStatus, refresh, round]);
  useEffect(() => onNotification(() => void refresh()), [onNotification, refresh]);

  if (authStatus === "loading" || (authStatus === "authenticated" && !me)) {
    return <PanelFrame><div className="skeleton h-48 rounded-md" /></PanelFrame>;
  }

  if (authStatus === "anonymous") {
    return (
      <PanelFrame>
        <p className="text-ink-soft">To bid you need a free account and your ad ready. It takes a minute, and you get 200 points to start.</p>
        <div className="flex flex-col gap-2 sm:flex-row">
          <ButtonLink href="/signup?next=/account" className="flex-1">
            Sign up free
          </ButtonLink>
          <ButtonLink href="/login?next=/arena" variant="secondary" className="flex-1">
            Log in
          </ButtonLink>
        </div>
        <RulesReminder />
      </PanelFrame>
    );
  }

  if (!me) return null;

  if (!me.hasAdProfile) {
    return (
      <PanelFrame>
        <Alert tone="info" title="First, set up your ad">
          It’s what everyone will see if you win: an image, your project’s name, one line and your website.
        </Alert>
        <ButtonLink href="/account" className="w-full">
          Set up my ad
        </ButtonLink>
      </PanelFrame>
    );
  }

  if (!round || !me.roundOpen) {
    return (
      <PanelFrame>
        <Alert tone="warning" title={round ? "Today’s Arena has closed" : "The Arena hasn’t opened yet"}>
          {round ? "We’re setting up tomorrow’s. Come back in a moment." : "It opens in a few moments."}
        </Alert>
      </PanelFrame>
    );
  }

  return <BidForm me={me} round={round} onBidPlaced={refresh} />;
}

function BidForm({ me, round, onBidPlaced }: { me: MyArenaStatus; round: RoundSummary; onBidPlaced: () => Promise<void> }) {
  const { setPoints } = useArena();
  const [amount, setAmount] = useState(pointsToInput(me.minNextBidPoints));
  const [rulesAccepted, setRulesAccepted] = useState(readRulesAck);
  // La casilla de la regla del 50 % solo se pregunta hasta aceptarla una vez
  const [askRules] = useState(() => !readRulesAck());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  // Clave de idempotencia de ESTE intento: si la red falla y se reintenta, no se cuenta dos veces
  const attemptKey = useRef<string | null>(null);

  const participating = me.totalPoints > 0;
  const leader = round.ranking[0];
  const isLeading = me.position === 1;
  // Para adelantar al primero hay que superarlo (a igualdad gana quien llegó antes)
  const toLead = leader && !isLeading ? Math.max(leader.totalPoints - me.totalPoints + 1, me.minNextBidPoints) : null;

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSuccess(null);
    const points = parsePoints(amount);
    if (points === null || points <= 0) {
      setError("Enter a number of points, for example 250.");
      return;
    }
    if (points < me.minNextBidPoints) {
      setError(`The minimum is ${formatPoints(me.minNextBidPoints)}.`);
      return;
    }
    if (points > me.availablePoints) {
      setError(`Not enough points: you have ${formatPoints(me.availablePoints)}.`);
      return;
    }
    if (!rulesAccepted) {
      setError("Confirm you know the 50% rule before bidding.");
      return;
    }

    attemptKey.current ??= crypto.randomUUID();
    setSubmitting(true);
    try {
      const result = await placeBid(points, attemptKey.current);
      attemptKey.current = null;
      setPoints(result.availablePoints);
      setSuccess(
        result.position === 1
          ? `Done! You’re 1st with ${formatPoints(result.totalPoints)}.`
          : `Done! You’ve bid ${formatPoints(result.totalPoints)} and you’re ${ordinal(result.position)}.`,
      );
      if (result.extended) {
        setSuccess((current) => `${current} Your bid added 2 minutes to the clock.`);
      }
      setAmount(pointsToInput(me.minNextBidPoints));
      await onBidPlaced();
    } catch (e) {
      if (!(e instanceof ApiError) || e.code !== "NETWORK_ERROR") {
        attemptKey.current = null; // error definitivo: el siguiente intento es una puja nueva
      }
      setError(e instanceof ApiError ? (e.fieldErrors.amountPoints ?? e.message) : "We couldn’t place your bid.");
    } finally {
      setSubmitting(false);
    }
  }

  function acceptRules(checked: boolean) {
    setRulesAccepted(checked);
    try {
      if (checked) window.localStorage.setItem(RULES_ACK_KEY, "1");
    } catch {
      // Navegación privada: se volverá a preguntar
    }
  }

  return (
    <PanelFrame>
      {/* Tu situación */}
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-md bg-canvas p-4">
          <p className="text-sm font-medium text-muted">Your total today</p>
          <p className="mt-1 font-display text-4xl font-black leading-none">
            <AnimatedNumber value={me.totalPoints} format={formatPoints} className="tabular" />
          </p>
          {me.carriedInPoints > 0 && (
            <p className="mt-1 text-xs text-muted">Includes {formatPoints(me.carriedInPoints)} from yesterday</p>
          )}
        </div>
        <div className={isLeading ? "rounded-md bg-gold p-4 ring-2 ring-ink" : "rounded-md bg-canvas p-4"}>
          <p className={isLeading ? "text-sm font-medium" : "text-sm font-medium text-muted"}>Position</p>
          <p className="mt-1 flex items-center gap-1.5 font-display text-4xl font-black leading-none">
            {isLeading && <CrownIcon className="size-7" />}
            {me.position ? ordinal(me.position) : "—"}
          </p>
          {!participating && <p className="mt-1 text-xs text-muted">No bid yet</p>}
        </div>
      </div>

      <div className="flex items-center justify-between gap-3 rounded-md px-4 py-3 text-sm ring-1 ring-line">
        <span className="flex items-center gap-2 text-ink-soft">
          <WalletIcon className="size-4" /> Your points
        </span>
        <span className="flex items-center gap-3">
          <strong className="tabular">{formatPoints(me.availablePoints)}</strong>
          <Link href="/earn" className="font-semibold text-brand hover:underline">
            Earn more
          </Link>
        </span>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {error && <Alert tone="danger">{error}</Alert>}
        {success && <Alert tone="success">{success}</Alert>}

        <div className="space-y-2">
          <label htmlFor="bid-amount" className="text-sm font-semibold">
            {participating ? "How many points to add?" : "How many points to bid?"}
          </label>
          <div className="relative">
            <input
              id="bid-amount"
              inputMode="numeric"
              autoComplete="off"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="tabular h-14 w-full rounded-md bg-surface pl-4 pr-12 font-display text-3xl font-black ring-2 ring-ink outline-none transition focus:ring-brand"
            />
            <span className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-base font-bold text-muted">pts</span>
          </div>
          <div className="flex flex-wrap gap-2">
            {QUICK_AMOUNTS.filter((value) => value >= me.minNextBidPoints).map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => setAmount(pointsToInput(value))}
                className="tabular rounded-sm bg-canvas px-3 py-1.5 text-sm font-semibold ring-1 ring-line transition hover:ring-ink"
              >
                {formatPoints(value)}
              </button>
            ))}
            {toLead !== null && (
              <button
                type="button"
                onClick={() => setAmount(pointsToInput(toLead))}
                className="tabular rounded-sm bg-gold px-3 py-1.5 text-sm font-bold ring-1 ring-ink transition hover:bg-gold-soft"
              >
                Take the lead: {formatPoints(toLead)}
              </button>
            )}
          </div>
          <p className="text-xs text-muted">Minimum {formatPoints(me.minNextBidPoints)}. It adds to what you’ve already bid.</p>
        </div>

        {askRules && (
          <label className="flex cursor-pointer items-start gap-3 rounded-md bg-warning-soft p-3 text-sm leading-relaxed text-ink-soft">
            <input
              type="checkbox"
              checked={rulesAccepted}
              onChange={(e) => acceptRules(e.target.checked)}
              className="mt-0.5 size-5 shrink-0 cursor-pointer accent-brand"
            />
            <span>
              I understand that if I don’t win, I keep <strong>50%</strong> of the points I bid for tomorrow and the
              other <strong>50% is lost</strong>.
            </span>
          </label>
        )}

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          {participating ? "Raise my bid" : "Place bid"}
        </Button>
      </form>
      <RulesReminder />
    </PanelFrame>
  );
}

function PanelFrame({ children }: { children: React.ReactNode }) {
  return (
    <section className="space-y-5 rounded-lg bg-surface p-5 shadow-lift ring-2 ring-ink sm:p-7">
      <h2 className="text-3xl font-black">Your bid</h2>
      {children}
    </section>
  );
}

function RulesReminder() {
  return (
    <div className="flex gap-3 rounded-md bg-canvas p-4 text-sm leading-relaxed text-ink-soft">
      <InfoIcon className="mt-0.5 size-5 shrink-0 text-brand" />
      <p>
        Your bids for the day <strong>add up</strong>. If you don’t win, you keep <strong>50%</strong> of your points
        for tomorrow and the other 50% is lost. A bid in the last 2 minutes adds 2 more minutes to the clock.
      </p>
    </div>
  );
}
