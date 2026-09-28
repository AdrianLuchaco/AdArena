"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { useArena } from "@/lib/arena-context";
import { AdSenseScript, AdSenseUnit } from "../ads/AdSenseUnit";
import { BookIcon } from "../icons";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { PageSpinner } from "../ui/Spinner";
import { useToast } from "../ui/Toaster";
import { ArenaScoreboard } from "./ArenaScoreboard";
import { BidPanel } from "./BidPanel";
import { Leaderboard } from "./Leaderboard";

/** La Arena: el marcador, la clasificación en directo y la tarjeta para pujar. */
export function ArenaClient() {
  const { data, error, clockOffset, live, reload } = useArena();
  const toast = useToast();
  const previousEnd = useRef<string | null>(null);
  const round = data?.round ?? null;

  // Anti-sniping: si el fin se retrasa, avisamos a todos
  useEffect(() => {
    if (!round) return;
    const previous = previousEnd.current;
    previousEnd.current = round.endsAt;
    if (previous && new Date(round.endsAt) > new Date(previous)) {
      toast({
        tone: "info",
        title: "Last-minute bid!",
        message: "The clock has been extended by 2 minutes.",
      });
    }
  }, [round, toast]);

  if (!data) {
    if (error) {
      return (
        <div className="mx-auto flex w-full max-w-lg flex-1 flex-col justify-center gap-4 px-4 py-24">
          <Alert tone="danger" title="We can’t load the Arena">
            {error}
          </Alert>
          <Button variant="secondary" onClick={() => void reload()} className="self-start">
            Try again
          </Button>
        </div>
      );
    }
    return <PageSpinner label="Loading the Arena…" />;
  }

  return (
    <div className="flex-1">
      <ArenaScoreboard
        round={round}
        clockOffset={clockOffset}
        live={live}
        showLeader={false}
        title="Today’s Arena"
        intro={<p>The highest bid when the clock hits zero wins tomorrow’s homepage for 24 hours.</p>}
      />

      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 sm:px-6 lg:grid-cols-[1.25fr_1fr]">
        <section>
          <h2 className="mb-4 text-4xl">Live standings</h2>
          <Leaderboard round={round} showBidButton={false} />
          <Link
            href="/how-it-works#arena"
            className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-ink-soft hover:text-ink"
          >
            <BookIcon className="size-4" /> Arena rules: bids, ties and the 50% rule
          </Link>
          {/* Un anuncio pequeño, debajo de la clasificación y lejos del botón de pujar */}
          <AdSenseUnit slot="banner" className="mt-8" />
          <AdSenseScript />
        </section>

        {/* En el móvil, la tarjeta para pujar va primero; en escritorio, a la derecha */}
        <div className="order-first lg:sticky lg:top-24 lg:order-none lg:self-start">
          <BidPanel round={round} />
        </div>
      </div>
    </div>
  );
}
