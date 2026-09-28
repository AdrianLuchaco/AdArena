"use client";

import Image from "next/image";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError, getEarnOverview, getPublicProject, startProjectView, tickProjectView } from "@/lib/api";
import { useArena } from "@/lib/arena-context";
import { useAuth } from "@/lib/auth-context";
import { apiUrl } from "@/lib/config";
import { formatPoints, ordinal, pointsText } from "@/lib/format";
import type { EarnOverview, PublicProject, ViewStatus } from "@/lib/types";
import { Alert } from "../ui/Alert";
import { ButtonLink } from "../ui/Button";
import { PageSpinner } from "../ui/Spinner";
import { useToast } from "../ui/Toaster";
import { ArrowUpRightIcon, CrownIcon } from "../icons";
import { SiteViewer, type ViewerEarning } from "../viewer/SiteViewer";

/**
 * Ver la web de un proyecto de la Arena y ganar puntos: 10 cada 10 s mirándola, 40 de bonus a los
 * 60 s y como mucho 100 al día por proyecto. Sin sesión, se puede ver la web pero no se gana nada.
 */
export function ProjectViewer({ id }: { id: string }) {
  const { status: authStatus } = useAuth();
  const { setPoints } = useArena();
  const toast = useToast();
  const [project, setProject] = useState<PublicProject | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<ViewStatus | null>(null);
  const [overview, setOverview] = useState<EarnOverview | null>(null);
  const [earned, setEarned] = useState(0);
  const [capReached, setCapReached] = useState(false);
  const [floating, setFloating] = useState<number | null>(null);
  const [startError, setStartError] = useState<string | null>(null);
  const sending = useRef(false);
  const pending = useRef(0);

  useEffect(() => {
    let active = true;
    getPublicProject(id)
      .then((result) => active && setProject(result))
      .catch((e) => active && setError(e instanceof ApiError ? e.message : "We couldn't open this project."));
    return () => {
      active = false;
    };
  }, [id]);

  const loggedIn = authStatus === "authenticated";

  // El servidor apunta cuándo has abierto la web (y te dice lo que llevas hoy con ella)
  useEffect(() => {
    if (!loggedIn || !project?.inArena) return;
    let active = true;
    startProjectView(id)
      .then((result) => {
        if (!active) return;
        setView(result);
        setEarned(result.pointsEarnedToday);
        setCapReached(result.reason === "CAP_REACHED");
      })
      .catch((e) => active && setStartError(e instanceof ApiError ? e.message : "We couldn't start counting."));
    getEarnOverview()
      .then((result) => active && setOverview(result))
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [id, loggedIn, project?.inArena]);

  /** Tramos de 10 s completados: se envían de uno en uno (si llegan más mientras, van en el siguiente envío). */
  const onTicks = useCallback(
    (count: number) => {
      pending.current += count;
      const run = () => {
        if (sending.current || pending.current <= 0) return;
        const ticks = pending.current;
        pending.current = 0;
        sending.current = true;
        tickProjectView(id, ticks)
          .then((result) => {
            setEarned(result.pointsEarnedToday);
            setPoints(result.availablePoints);
            if (result.pointsAwarded > 0) {
              setFloating(result.pointsAwarded);
              setTimeout(() => setFloating(null), 1400);
            }
            if (result.bonusAwarded) {
              toast({
                tone: "success",
                title: "60-second bonus!",
                message: `+${pointsText(result.pointsAwarded)} with ${project?.companyName ?? "this project"}.`,
              });
            }
            if (result.capReached) setCapReached(true);
          })
          .catch((e) => {
            // TICK_TOO_SOON: el servidor aún no cuenta ese tramo (otra pestaña cobró hace nada). No es un error.
            if (e instanceof ApiError && e.code !== "TICK_TOO_SOON") setStartError(e.message);
          })
          .finally(() => {
            sending.current = false;
            run();
          });
      };
      run();
    },
    [id, project?.companyName, setPoints, toast],
  );

  const next = useMemo(() => {
    const candidate = overview?.projects.find(
      (p) => !p.own && p.id !== id && p.pointsEarnedToday < p.dailyCap,
    );
    if (candidate) return { href: `/watch/${candidate.id}`, label: "Next website" };
    return loggedIn ? { href: "/earn/links", label: "Bonus links" } : null;
  }, [overview, id, loggedIn]);

  if (error) {
    return (
      <div className="mx-auto w-full max-w-lg flex-1 px-4 py-24">
        <Alert tone="danger" title="We couldn't open this project">
          {error}
        </Alert>
        <ButtonLink href="/earn" variant="secondary" className="mt-4">
          See other projects
        </ButtonLink>
      </div>
    );
  }
  if (!project || authStatus === "loading") return <PageSpinner label="Opening the website…" />;

  const rules = view?.rules;
  const cap = view?.dailyCap ?? 100;
  let earning: ViewerEarning;
  const base = {
    earned,
    goal: cap,
    tickMs: (rules?.tickSeconds ?? 10) * 1000,
    onTicks,
    floating,
    earningText: rules
      ? `+${rules.tickPoints} every ${rules.tickSeconds} s · +${rules.bonusPoints} bonus at ${rules.bonusAfterSeconds} s`
      : "+10 every 10 s",
  };
  if (!loggedIn) {
    earning = {
      ...base,
      state: "info",
      goal: 0,
      message: {
        title: "Earn points watching this website",
        text: "Create a free account: you get 200 Arena Points to start, and up to 100 more with every project, every day.",
        action: (
          <>
            <ButtonLink href={`/signup?next=/watch/${id}`} size="sm">
              Create free account
            </ButtonLink>
            <ButtonLink href={`/login?next=/watch/${id}`} size="sm" variant="secondary">
              I have an account
            </ButtonLink>
          </>
        ),
      },
    };
  } else if (!project.inArena) {
    earning = {
      ...base,
      state: "info",
      goal: 0,
      message: { title: "Not in today's Arena", text: "Only projects competing today earn points." },
    };
  } else if (startError) {
    earning = { ...base, state: "info", message: { title: "We can't count right now", text: startError } };
  } else if (!view) {
    earning = { ...base, state: "loading" };
  } else if (view.reason === "OWN_PROJECT") {
    earning = {
      ...base,
      state: "info",
      goal: 0,
      message: { title: "This is your project", text: "This is how others see your website. Your own projects don't earn points." },
    };
  } else if (capReached) {
    earning = {
      ...base,
      state: "done",
      message: {
        title: `${cap} points earned!`,
        text: "That's all for today with this website. You can earn them again tomorrow.",
      },
    };
  } else {
    earning = { ...base, state: "earning" };
  }

  return (
    <SiteViewer
      site={project.site}
      name={project.companyName}
      badge={
        project.position ? (
          <span className="hidden shrink-0 items-center gap-1 rounded-sm bg-gold px-2 py-0.5 text-xs font-bold text-ink sm:inline-flex">
            {project.position === 1 && <CrownIcon className="size-3.5" />}
            {ordinal(project.position)} today
          </span>
        ) : undefined
      }
      exitHref={loggedIn ? "/earn" : "/arena"}
      next={next}
      earning={earning}
      details={<ProjectDetails project={project} />}
    />
  );
}

/** La ficha del proyecto: su anuncio y cómo va en la Arena. */
function ProjectDetails({ project }: { project: PublicProject }) {
  return (
    <div className="space-y-5">
      <div className="relative aspect-[16/10] overflow-hidden rounded-md bg-canvas">
        <Image src={apiUrl(project.imageUrl)} alt={`${project.companyName} image`} fill sizes="28rem" className="object-cover" />
      </div>
      <div>
        <h2 className="text-2xl font-extrabold">{project.companyName}</h2>
        <p className="mt-2 whitespace-pre-line leading-relaxed text-ink-soft">{project.description}</p>
      </div>
      <dl className="grid grid-cols-2 gap-3">
        <div className="rounded-md bg-canvas p-4">
          <dt className="text-sm text-muted">{project.inArena ? "Bid so far" : "Bid"}</dt>
          <dd className="tabular mt-1 font-display text-xl font-extrabold">{formatPoints(project.totalPoints)}</dd>
        </div>
        <div className="rounded-md bg-canvas p-4">
          <dt className="text-sm text-muted">Position</dt>
          <dd className="mt-1 font-display text-xl font-extrabold">{project.position ? ordinal(project.position) : "—"}</dd>
        </div>
      </dl>
      <a
        href={project.site.openUrl}
        target="_blank"
        rel="noopener noreferrer sponsored"
        className="flex h-12 w-full items-center justify-center gap-2 rounded-md bg-ink font-semibold text-white transition hover:bg-brand"
      >
        Open {project.site.domain} in a new tab
        <ArrowUpRightIcon className="size-5" />
      </a>
      <p className="text-sm text-muted">
        In a normal new tab the counter can’t tell whether you’re watching, so it doesn’t count. To earn points, watch
        it here (or with “Open in a window”, which counts).
      </p>
    </div>
  );
}
