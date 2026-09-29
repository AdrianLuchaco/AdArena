"use client";

import Link from "next/link";
import { useArena } from "@/lib/arena-context";
import { useAuth } from "@/lib/auth-context";
import { formatClock } from "@/lib/format";
import { useCountdown } from "@/lib/hooks";
import { AdView } from "../ad/AdView";
import { WinnerShowcase } from "../ad/WinnerShowcase";
import { AdUnit } from "../ads/AdUnit";
import { ArenaScoreboard } from "../arena/ArenaScoreboard";
import { Leaderboard, LeaderboardSkeleton } from "../arena/Leaderboard";
import { Alert } from "../ui/Alert";
import { Button, ButtonLink } from "../ui/Button";
import { ArrowRightIcon } from "../icons";

const NOTICES = {
  NO_BIDS: {
    title: "The homepage is free today",
    text: "Nobody bid yesterday. Today’s Race is already open, so tomorrow could be yours.",
  },
  PENDING_REVIEW: {
    title: "The winning ad is being reviewed",
    text: "We check every ad before it goes live. It will appear here as soon as it’s approved.",
  },
} as const;

/**
 * La portada. Lo principal es ganar la puja, así que todo lleva a la Arena:
 *  - Con ganador aprobado: su presentación a pantalla completa (lo que se gana) con una franja
 *    amarilla para pujar por mañana; debajo, el marcador de la Arena.
 *  - Sin ganador: el marcador de la Arena es la cabecera.
 *  - Siempre: los primeros de la clasificación, los 3 pasos para ganar y un anuncio pequeño.
 */
export function HomeClient() {
  const { data, error, clockOffset, live, reload } = useArena();
  const { user } = useAuth();

  if (!data) {
    if (error) {
      return (
        <div className="mx-auto flex w-full max-w-lg flex-1 flex-col justify-center gap-4 px-4 py-24">
          <Alert tone="danger" title="We can’t load the homepage">
            {error}
          </Alert>
          <Button variant="secondary" onClick={() => void reload()} className="self-start">
            Try again
          </Button>
        </div>
      );
    }
    return <HomeSkeleton />;
  }

  const ad = data.state === "AD" ? data.currentAd : null;
  const baseNotice = data.state === "NO_BIDS" || data.state === "PENDING_REVIEW" ? NOTICES[data.state] : null;
  // A un administrador se le da el atajo para revisarlo (y aprobarlo) ya
  const notice =
    baseNotice && data.state === "PENDING_REVIEW" && user?.role === "ADMIN"
      ? { ...baseNotice, href: "/admin/moderation", action: "Review it now" }
      : baseNotice;

  return (
    <>
      {ad &&
        (ad.showcase ? (
          <WinnerShowcase
            ad={ad}
            showcase={ad.showcase}
            winner={{ wonWithPoints: ad.wonWithPoints, roundDate: ad.roundDate }}
            footer={<BidStrip />}
          />
        ) : (
          <div className="relative">
            <AdView ad={ad} winner={{ wonWithPoints: ad.wonWithPoints, roundDate: ad.roundDate }} />
            <BidStrip />
          </div>
        ))}

      <ArenaScoreboard
        round={data.round}
        clockOffset={clockOffset}
        live={live}
        heading={ad ? "h2" : "h1"}
        title={ad ? "This spot is up for grabs" : "Win tomorrow’s homepage"}
        notice={notice}
        intro={
          <p>
            Projects bid with Crown Points, which you earn for free. The highest bid at midnight takes over this
            homepage for 24 hours.
          </p>
        }
        actions={
          <>
            <ButtonLink href="/race" size="lg" variant="gold">
              Bid now
            </ButtonLink>
            <ButtonLink href="/earn" size="lg" variant="glass">
              Earn points first
            </ButtonLink>
          </>
        }
      />

      <section className="mx-auto w-full max-w-5xl px-4 py-12 sm:px-6 sm:py-16">
        <div className="mb-5 flex items-end justify-between gap-4">
          <h2 className="text-4xl sm:text-5xl">Today’s standings</h2>
          <Link
            href="/race"
            className="hidden shrink-0 items-center gap-1.5 text-sm font-semibold hover:underline sm:flex"
          >
            Open the Race <ArrowRightIcon className="size-4" />
          </Link>
        </div>
        <Leaderboard round={data.round} limit={5} />
      </section>

      <HowToWin />

      {/* Un anuncio pequeño, al final y lejos de los botones */}
      <div className="mx-auto w-full max-w-5xl px-4 pb-14 sm:px-6">
        <AdUnit placement="homeBanner" />
      </div>
    </>
  );
}

/** Franja amarilla al pie del ganador de hoy: el mismo sitio, mañana, puede ser tuyo. */
function BidStrip() {
  const { data, clockOffset } = useArena();
  const remaining = useCountdown(data?.round?.endsAt, clockOffset);
  return (
    <Link
      href="/race"
      className="group absolute inset-x-0 bottom-0 z-20 flex h-14 items-center gap-3 border-t-2 border-ink bg-gold px-4 text-ink sm:px-6"
    >
      <span className="min-w-0 flex-1 truncate font-display text-xl font-black uppercase sm:text-2xl">
        <span className="sm:hidden">Want this spot?</span>
        <span className="hidden sm:inline">Want this spot tomorrow? Bid for it</span>
      </span>
      {data?.round && (
        <span className="tabular hidden font-display text-2xl font-black sm:inline">{formatClock(remaining)}</span>
      )}
      <span className="inline-flex h-9 shrink-0 items-center gap-1.5 rounded-md bg-ink px-4 text-sm font-semibold text-white transition group-hover:bg-brand">
        Bid now <ArrowRightIcon className="size-4" />
      </span>
    </Link>
  );
}

const STEPS = [
  {
    title: "Earn points",
    text: "Get 200 when you sign up. Then earn more for free by watching other projects’ websites.",
    href: "/earn",
    link: "Start earning",
  },
  {
    title: "Bid",
    text: "Set up your ad and bid your points. Bids add up, and we tell you the moment someone outbids you.",
    href: "/race",
    link: "Go to the Race",
  },
  {
    title: "Win the homepage",
    text: "Highest bid at midnight wins. Your website becomes the full-screen homepage for 24 hours, plus 500 points.",
    href: "/winners",
    link: "See past winners",
  },
];

/** Los 3 pasos para ganar (es una secuencia de verdad, por eso van numerados). */
function HowToWin() {
  return (
    <section className="border-y-2 border-ink bg-surface">
      <div className="mx-auto max-w-5xl px-4 py-12 sm:px-6 sm:py-16">
        <h2 className="text-4xl sm:text-5xl">How to win</h2>
        <ol className="mt-8 grid gap-px overflow-hidden rounded-lg bg-ink ring-2 ring-ink md:grid-cols-3">
          {STEPS.map((step, index) => (
            <li key={step.title} className="flex flex-col bg-surface p-6">
              <span className="font-display text-7xl font-black leading-none text-brand" aria-hidden="true">
                {index + 1}
              </span>
              <h3 className="mt-3 text-3xl">{step.title}</h3>
              <p className="mt-2 flex-1 leading-relaxed text-ink-soft">{step.text}</p>
              <Link
                href={step.href}
                className="mt-4 inline-flex items-center gap-1.5 font-semibold text-brand hover:underline"
              >
                {step.link} <ArrowRightIcon className="size-4" />
              </Link>
            </li>
          ))}
        </ol>
        <p className="mt-6 text-ink-soft">
          If you don’t win, you keep 50% of your bid for tomorrow.{" "}
          <Link
            href="/how-it-works"
            className="font-semibold text-ink underline decoration-brand decoration-2 underline-offset-4"
          >
            Read the full guide
          </Link>
        </p>
      </div>
    </section>
  );
}

function HomeSkeleton() {
  return (
    <>
      <div className="skeleton h-[28rem] rounded-none" />
      <div className="mx-auto w-full max-w-5xl px-4 py-14 sm:px-6">
        <LeaderboardSkeleton />
      </div>
    </>
  );
}
