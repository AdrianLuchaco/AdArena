"use client";

import Image from "next/image";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import type { SiteInfo, ViewMode } from "@/lib/types";
import { type PauseReason, useActiveTimer } from "@/lib/useActiveTimer";
import { ArrowLeftIcon, ArrowRightIcon, CheckIcon, CloseIcon, EyeIcon, InfoIcon, WindowIcon } from "../icons";
import { SiteAvatar } from "./SiteAvatar";

/** Lo que se gana en este visor y cómo va. */
export interface ViewerEarning {
  /**
   * loading: preparando · earning: cuenta el tiempo · done: ya conseguido todo lo de hoy ·
   * info: aquí no se ganan puntos (sin sesión, tu propio proyecto…), pero se puede ver la web
   */
  state: "loading" | "earning" | "done" | "info";
  earned: number;
  goal: number;
  /** Duración de cada tramo (10 s) */
  tickMs: number;
  onTicks: (count: number) => void;
  /** "+10" que sube al sumar */
  floating: number | null;
  /** Qué se gana: "+10 every 10 s · +40 bonus at 60 s" */
  earningText: string;
  /** Mensaje (y acción) cuando no se gana o ya se consiguió todo */
  message?: { title: string; text?: string; action?: React.ReactNode };
}

interface SiteViewerProps {
  site: SiteInfo;
  /** El nombre del proyecto o el título del enlace */
  name: string;
  /** Etiqueta junto al nombre ("2nd today", la red social…) */
  badge?: React.ReactNode;
  exitHref: string;
  next: { href: string; label: string } | null;
  earning: ViewerEarning;
  /** Ficha con más detalles, en un panel lateral */
  details?: React.ReactNode;
}

/**
 * idle: aún no se ha abierto · open: se abrió y cuenta mientras no estés en LaunchCrown ·
 * blocked: el navegador bloqueó la ventana (se ofrece un enlace normal)
 */
type WindowState = "idle" | "open" | "blocked";

/**
 * El visor de "Earn points": arriba, el marcador de puntos; debajo, LA WEB del otro proyecto.
 *  - Si la web se deja mostrar dentro de LaunchCrown, se ve aquí mismo (iframe aislado con sandbox: no
 *    puede tocar LaunchCrown ni cambiar de página la pestaña).
 *  - Si no (YouTube, Instagram, X, muchas tiendas…), se abre en su propia ventana y cuenta el tiempo
 *    que pasas FUERA de LaunchCrown desde que la abriste. En cuanto vuelves a LaunchCrown, se para.
 *
 * Por qué no miramos si esa ventana "sigue abierta": YouTube, X o Instagram envían la cabecera
 * Cross-Origin-Opener-Policy, que corta el vínculo con quien las abre, y el navegador dice que la
 * ventana está "cerrada" aunque la estés mirando. Por eso se medía mal (fase 11).
 */
export function SiteViewer({ site, name, badge, exitHref, next, earning, details }: SiteViewerProps) {
  const [mode, setMode] = useState<ViewMode>(site.mode);
  const [frameLoaded, setFrameLoaded] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [doneDismissed, setDoneDismissed] = useState(false);
  const frameRef = useRef<HTMLIFrameElement>(null);
  const pointerInFrame = useRef(false);

  // Ventana aparte
  const popup = useRef<Window | null>(null);
  const [windowState, setWindowStateValue] = useState<WindowState>("idle");
  const windowStateRef = useRef<WindowState>("idle");
  const setWindowState = useCallback((state: WindowState) => {
    windowStateRef.current = state;
    setWindowStateValue(state);
  }, []);

  // Mientras el visor está abierto, la página de detrás no se desplaza
  useEffect(() => {
    const previous = document.documentElement.style.overflow;
    document.documentElement.style.overflow = "hidden";
    return () => {
      document.documentElement.style.overflow = previous;
    };
  }, []);

  // Al salir del visor, se cierra la ventana de la web si todavía podemos
  useEffect(
    () => () => {
      try {
        popup.current?.close();
      } catch {
        // la web ya no nos deja tocar su ventana: no pasa nada
      }
    },
    [],
  );

  const isFrameEngaged = useCallback(
    () => pointerInFrame.current || (frameRef.current !== null && document.activeElement === frameRef.current),
    [],
  );
  const isWindowOpen = useCallback(() => windowStateRef.current === "open", []);

  const { progressMs, pauseReason } = useActiveTimer({
    enabled: earning.state === "earning",
    mode: mode === "FRAME" ? "frame" : "window",
    tickMs: earning.tickMs,
    onTicks: earning.onTicks,
    isFrameEngaged,
    isWindowOpen,
  });

  function openWindow() {
    // En el mismo clic (si no, el navegador la bloquea). Se abre en blanco, se corta el acceso de la
    // web a LaunchCrown (opener = null) y después se carga la web.
    const width = Math.min(1200, window.screen.availWidth - 80);
    const height = Math.min(900, window.screen.availHeight - 80);
    const opened = window.open("", "adarena-site", `popup,width=${width},height=${height},left=40,top=40`);
    if (!opened) {
      setWindowState("blocked");
      return;
    }
    try {
      opened.opener = null;
      opened.location.href = site.openUrl;
    } catch {
      opened.close();
      setWindowState("blocked");
      return;
    }
    popup.current = opened;
    setWindowState("open");
  }

  /** Si el navegador bloquea las ventanas: enlace normal (pestaña nueva) y contamos igual. */
  function openedWithLink() {
    popup.current = null;
    setWindowState("open");
  }

  /** Volver a la web: su ventana si aún la controlamos; si no, se abre otra vez. */
  function goToSite() {
    try {
      if (popup.current && !popup.current.closed) {
        popup.current.focus();
        return;
      }
    } catch {
      // sin acceso a la ventana: se abre de nuevo
    }
    openWindow();
  }

  function stopWatching() {
    try {
      popup.current?.close();
    } catch {
      // nada
    }
    popup.current = null;
    setWindowState("idle");
  }

  const progress = earning.goal > 0 ? Math.min(1, earning.earned / earning.goal) : 0;
  const paused = earning.state === "earning" && pauseReason !== null;
  const status = statusText(earning, pauseReason, site.domain, mode, windowState);

  return (
    <div className="fixed inset-0 z-[60] flex flex-col bg-night text-white" role="dialog" aria-modal="true" aria-label={`${name} website`}>
      {/* ---------------- marcador de puntos ---------------- */}
      <header className="relative shrink-0 border-b-2 border-gold bg-night">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2.5 sm:flex-nowrap sm:px-4">
          <Link
            href={exitHref}
            className="order-1 inline-flex h-10 shrink-0 items-center gap-1.5 rounded-md px-3 text-sm font-semibold text-white/80 ring-1 ring-white/20 transition hover:bg-white/10 hover:text-white"
          >
            <ArrowLeftIcon className="size-4" />
            <span className="hidden sm:inline">Exit</span>
          </Link>

          <div className="order-2 flex min-w-0 flex-1 items-center gap-2.5">
            <SiteAvatar name={site.siteName || name} iconUrl={site.iconUrl} color={site.themeColor} />
            <div className="min-w-0">
              <p className="flex min-w-0 items-center gap-2">
                <span className="truncate font-bold">{name}</span>
                {badge}
              </p>
              <p className="truncate text-xs text-white/55">{site.domain}</p>
            </div>
          </div>

          {/* Contador */}
          <div className="order-4 flex w-full items-center gap-3 sm:order-3 sm:w-auto sm:max-w-md">
            <ProgressRing
              progress={earning.state === "done" ? 1 : earning.state === "earning" ? progressMs / earning.tickMs : 0}
              done={earning.state === "done"}
              paused={paused}
              floating={earning.floating}
              seconds={Math.floor(progressMs / 1000)}
            />
            <p aria-live="polite" className={cn("min-w-0 flex-1 text-sm leading-snug", paused ? "text-gold" : "text-white/80")}>
              <strong className="block truncate font-semibold text-white">{status.title}</strong>
              <span className="line-clamp-1">{status.text}</span>
            </p>
            {earning.goal > 0 && earning.state !== "info" && (
              <p className="tabular shrink-0 text-right font-display text-3xl font-black leading-none text-gold">
                {earning.earned}
                <span className="text-base font-bold text-white/50">/{earning.goal}</span>
              </p>
            )}
          </div>

          <div className="order-3 flex shrink-0 items-center gap-2 sm:order-4">
            {details && (
              <button
                type="button"
                onClick={() => setShowDetails((value) => !value)}
                className="grid size-10 place-items-center rounded-md text-white/80 ring-1 ring-white/20 transition hover:bg-white/10 hover:text-white"
                aria-label={showDetails ? "Close details" : "Show details"}
                aria-expanded={showDetails}
              >
                <InfoIcon className="size-5" />
              </button>
            )}
            {mode === "FRAME" && (
              <button
                type="button"
                onClick={() => setMode("WINDOW")}
                className="hidden h-10 items-center gap-1.5 rounded-md px-3 text-sm font-semibold text-white/80 ring-1 ring-white/20 transition hover:bg-white/10 hover:text-white md:inline-flex"
                title="If the website doesn't look right here, open it in its own window"
              >
                <WindowIcon className="size-4" /> Open in a window
              </button>
            )}
            {next && (
              <Link
                href={next.href}
                className={cn(
                  "inline-flex h-10 items-center gap-1.5 rounded-md px-4 text-sm font-bold transition",
                  earning.state === "done" ? "bg-gold text-ink hover:bg-white" : "bg-white/10 text-white hover:bg-white/20",
                )}
              >
                <span className="sm:hidden">Next</span>
                <span className="hidden sm:inline">{next.label}</span>
                <ArrowRightIcon className="size-4" />
              </Link>
            )}
          </div>
        </div>
        {/* Lo conseguido hoy con esta web */}
        <div className="absolute inset-x-0 -bottom-0.5 h-0.5 bg-white/15" aria-hidden="true">
          <div
            className={cn("h-full transition-[width] duration-700", earning.state === "done" ? "bg-success" : "bg-brand")}
            style={{ width: `${progress * 100}%` }}
          />
        </div>
      </header>

      {/* ---------------- la web ---------------- */}
      <div className="relative min-h-0 flex-1">
        {mode === "FRAME" ? (
          <div
            className="absolute inset-0 bg-white"
            onPointerEnter={() => (pointerInFrame.current = true)}
            onPointerLeave={() => (pointerInFrame.current = false)}
          >
            {!frameLoaded && <FrameLoading site={site} name={name} />}
            <iframe
              ref={frameRef}
              src={site.frameUrl ?? site.openUrl}
              title={`${name} website`}
              className="size-full border-0"
              sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox allow-presentation"
              allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
              referrerPolicy="strict-origin-when-cross-origin"
              onLoad={() => setFrameLoaded(true)}
            />
            <button
              type="button"
              onClick={() => setMode("WINDOW")}
              className="absolute bottom-3 left-3 inline-flex items-center gap-1.5 rounded-md bg-night px-3 py-2 text-xs font-semibold text-white shadow-pop transition hover:bg-ink-soft md:hidden"
            >
              <WindowIcon className="size-4" /> Not loading? Open it in a window
            </button>
          </div>
        ) : (
          <WindowPortal
            site={site}
            name={name}
            state={windowState}
            earning={earning}
            pauseReason={pauseReason}
            onOpen={openWindow}
            onOpenedWithLink={openedWithLink}
            onGoToSite={goToSite}
            onStop={stopWatching}
          />
        )}

        {earning.state === "done" && !doneDismissed && (
          <div className="animate-pop pointer-events-auto absolute left-1/2 top-4 z-10 w-[min(92vw,26rem)] -translate-x-1/2 rounded-md bg-surface p-5 text-ink shadow-lift ring-2 ring-ink">
            <div className="flex items-start gap-3">
              <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-gold text-ink">
                <CheckIcon className="size-6" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-display text-2xl font-black uppercase leading-none">{earning.message?.title ?? "Done!"}</p>
                {earning.message?.text && <p className="mt-1 text-sm text-ink-soft">{earning.message.text}</p>}
              </div>
              <button
                type="button"
                onClick={() => setDoneDismissed(true)}
                className="grid size-8 place-items-center rounded-md text-muted hover:bg-ink/5 hover:text-ink"
                aria-label="Keep watching this website"
              >
                <CloseIcon className="size-4" />
              </button>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              {next && (
                <Link href={next.href} className="inline-flex h-10 items-center gap-1.5 rounded-md bg-brand px-4 text-sm font-bold text-white ring-2 ring-ink shadow-hard">
                  {next.label} <ArrowRightIcon className="size-4" />
                </Link>
              )}
              <button
                type="button"
                onClick={() => setDoneDismissed(true)}
                className="inline-flex h-10 items-center rounded-md px-4 text-sm font-semibold text-ink-soft ring-1 ring-line hover:ring-ink"
              >
                Keep watching
              </button>
            </div>
          </div>
        )}

        {earning.state === "info" && earning.message && (
          <div className="pointer-events-auto absolute bottom-4 left-1/2 z-10 w-[min(92vw,30rem)] -translate-x-1/2 rounded-md bg-surface p-5 text-ink shadow-lift ring-2 ring-ink">
            <p className="font-display text-2xl font-black uppercase leading-none">{earning.message.title}</p>
            {earning.message.text && <p className="mt-2 text-sm text-ink-soft">{earning.message.text}</p>}
            {earning.message.action && <div className="mt-4 flex flex-wrap gap-2">{earning.message.action}</div>}
          </div>
        )}

        {details && showDetails && (
          <aside className="animate-fade-in absolute inset-y-0 right-0 z-20 w-full max-w-md overflow-y-auto border-l-2 border-ink bg-surface text-ink shadow-pop">
            <div className="sticky top-0 flex items-center justify-between border-b border-line bg-surface px-5 py-3">
              <p className="font-bold">Details</p>
              <button
                type="button"
                onClick={() => setShowDetails(false)}
                className="grid size-9 place-items-center rounded-md text-ink-soft hover:bg-ink/5"
                aria-label="Close details"
              >
                <CloseIcon className="size-5" />
              </button>
            </div>
            <div className="p-5">{details}</div>
          </aside>
        )}
      </div>
    </div>
  );
}

/** El título y el texto del marcador según lo que está pasando. */
function statusText(
  earning: ViewerEarning,
  reason: PauseReason,
  domain: string,
  mode: ViewMode,
  windowState: WindowState,
): { title: string; text: string } {
  if (earning.state === "loading") return { title: "Getting ready…", text: "One moment" };
  if (earning.state === "done") {
    return { title: earning.message?.title ?? "Done!", text: earning.message?.text ?? "You have all of today's points for this website" };
  }
  if (earning.state === "info") return { title: earning.message?.title ?? "No points here", text: earning.message?.text ?? "" };
  switch (reason) {
    case "hidden":
      return { title: "Paused", text: "Come back to this tab to keep earning" };
    case "blur":
      return { title: "Paused", text: "Click on the website to keep earning" };
    case "idle":
      return { title: "Still there?", text: "Move your mouse or tap the website to keep earning" };
    case "closed":
      return {
        title: windowState === "blocked" ? "Your browser blocked the window" : "Open the website to start",
        text: "Points count while you're on the website",
      };
    case "here":
      return { title: "Paused while you're on LaunchCrown", text: `Go back to ${domain} to keep earning` };
    default:
      return {
        title: mode === "FRAME" ? "Earning points" : `Earning while you watch ${domain}`,
        text: earning.earningText,
      };
  }
}

/** Mientras carga la web: su foto, su logo y su nombre. */
function FrameLoading({ site, name }: { site: SiteInfo; name: string }) {
  return (
    <div className="absolute inset-0 grid place-items-center overflow-hidden bg-night text-white">
      {site.imageUrl && (
        <Image src={apiUrl(site.imageUrl)} alt="" fill sizes="100vw" className="object-cover opacity-30 blur-2xl" />
      )}
      <div className="relative flex flex-col items-center gap-3 text-center">
        <SiteAvatar name={site.siteName || name} iconUrl={site.iconUrl} color={site.themeColor} className="size-16 rounded-md text-2xl" />
        <p className="font-display text-4xl font-black uppercase">{site.siteName || name}</p>
        <p className="text-sm text-white/60">Loading {site.domain}…</p>
      </div>
    </div>
  );
}

/** Webs que se abren aparte: la "puerta" a su ventana y lo que está pasando. */
function WindowPortal({
  site,
  name,
  state,
  earning,
  pauseReason,
  onOpen,
  onOpenedWithLink,
  onGoToSite,
  onStop,
}: {
  site: SiteInfo;
  name: string;
  state: WindowState;
  earning: ViewerEarning;
  pauseReason: PauseReason;
  onOpen: () => void;
  onOpenedWithLink: () => void;
  onGoToSite: () => void;
  onStop: () => void;
}) {
  const counting = earning.state === "earning" && pauseReason === null;
  return (
    <div className="absolute inset-0 overflow-y-auto">
      {site.imageUrl && <Image src={apiUrl(site.imageUrl)} alt="" fill sizes="100vw" className="object-cover opacity-20 blur-3xl" />}
      <div
        className="absolute inset-0"
        style={{ background: `radial-gradient(80% 60% at 50% 0%, ${site.themeColor ?? "#2437ff"}44, transparent 70%)` }}
        aria-hidden="true"
      />
      <div className="relative mx-auto flex min-h-full max-w-xl flex-col items-center justify-center px-5 py-10 text-center">
        <SiteAvatar name={site.siteName || name} iconUrl={site.iconUrl} color={site.themeColor} className="size-20 rounded-md text-3xl" />
        <p className="mt-5 font-display text-5xl font-black uppercase leading-none text-balance">{site.title || site.siteName || name}</p>
        {site.description && <p className="mt-3 text-white/70 text-pretty">{site.description}</p>}
        <p className="mt-2 text-sm text-white/50">{site.domain}</p>

        {state === "open" ? (
          <div className="mt-8 w-full rounded-md bg-white/5 p-5 ring-1 ring-white/15">
            <p className="flex items-center justify-center gap-2 font-semibold">
              <EyeIcon className="size-5 text-gold" />
              {earning.state !== "earning"
                ? `${site.domain} is open in its own window`
                : counting
                  ? `Counting while you're on ${site.domain}`
                  : "Welcome back: the counter is paused"}
            </p>
            <p className="mt-1 text-sm text-white/65">
              {earning.state !== "earning"
                ? "There are no more points to earn here today. Keep browsing it if you like."
                : "Points count while you’re on the website, not while you’re here. Go back to keep earning, or stop when you are done."}
            </p>
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              <button type="button" onClick={onGoToSite} className="inline-flex h-11 items-center gap-2 rounded-md bg-gold px-5 font-bold text-ink hover:bg-white">
                <WindowIcon className="size-5" /> Back to {site.domain}
              </button>
              <button type="button" onClick={onStop} className="inline-flex h-11 items-center rounded-md px-5 font-semibold text-white/80 ring-1 ring-white/25 hover:bg-white/10">
                I&apos;m done
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-8 flex w-full flex-col items-center gap-3">
            <button
              type="button"
              onClick={onOpen}
              className="inline-flex h-14 items-center gap-2 rounded-md bg-gold px-8 text-lg font-bold text-ink ring-2 ring-white shadow-[4px_4px_0_#fff] transition hover:bg-white active:translate-x-1 active:translate-y-1 active:shadow-none"
            >
              <WindowIcon className="size-5" />
              Open {site.domain}
            </button>
            <p className="max-w-sm text-sm text-white/60">
              This website can&apos;t be shown inside LaunchCrown, so it opens in its own window. Points count while you&apos;re
              there. Come back here when you&apos;re done.
            </p>
            {state === "blocked" && (
              <p className="max-w-sm rounded-lg bg-gold/15 px-4 py-3 text-sm text-gold ring-1 ring-gold/40">
                Your browser blocked the window.{" "}
                <a href={site.openUrl} target="_blank" rel="noopener noreferrer" onClick={onOpenedWithLink} className="font-bold underline">
                  Open it with this link
                </a>{" "}
                or allow pop-ups for LaunchCrown.
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

/** Círculo que se llena durante los 10 s; al completarse, sale el "+10". */
function ProgressRing({
  progress,
  done,
  paused,
  floating,
  seconds,
}: {
  progress: number;
  done: boolean;
  paused: boolean;
  floating: number | null;
  seconds: number;
}) {
  const radius = 17;
  const circumference = 2 * Math.PI * radius;
  return (
    <div className="relative size-11 shrink-0">
      <svg viewBox="0 0 42 42" className="size-11 -rotate-90" aria-hidden="true">
        <circle cx="21" cy="21" r={radius} fill="none" stroke="currentColor" strokeWidth="4" className="text-white/12" />
        <circle
          cx="21"
          cy="21"
          r={radius}
          fill="none"
          stroke="currentColor"
          strokeWidth="4"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - Math.min(1, progress))}
          className={cn("transition-[stroke-dashoffset] duration-200", done ? "text-success" : paused ? "text-white/40" : "text-gold")}
        />
      </svg>
      <span className="absolute inset-0 grid place-items-center">
        {done ? <CheckIcon className="size-5 text-success" /> : <span className="tabular text-[11px] font-bold text-white/80">{seconds}s</span>}
      </span>
      {floating !== null && (
        <span className="animate-float-up tabular pointer-events-none absolute -top-3 left-1/2 whitespace-nowrap rounded-sm bg-gold px-2 py-0.5 text-xs font-black text-ink">
          +{floating}
        </span>
      )}
    </div>
  );
}
