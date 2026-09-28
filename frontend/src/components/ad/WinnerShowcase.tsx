"use client";

import Image from "next/image";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import { formatLongDate, formatPoints } from "@/lib/format";
import type { Showcase } from "@/lib/types";
import { ArrowUpRightIcon, ChevronLeftIcon, ChevronRightIcon, CrownIcon, PauseIcon, PlayIcon } from "../icons";
import { SiteAvatar } from "../viewer/SiteAvatar";
import type { AdContent, WinnerInfo } from "./AdView";

type SceneKind = "intro" | "headline" | "points" | "gallery" | "visit";

const SCENE_MS: Record<SceneKind, number> = {
  intro: 5200,
  headline: 6500,
  points: 7000,
  gallery: 6500,
  visit: 7500,
};

const SCENE_NAMES: Record<SceneKind, string> = {
  intro: "Intro",
  headline: "Headline",
  points: "Highlights",
  gallery: "Photos",
  visit: "Visit",
};

/**
 * La presentación animada del ganador: una "película" corta montada SOLA con lo que AdArena leyó
 * de su web (logo, color de marca, titular, frases destacadas y fotos) y su propio anuncio.
 * Ocupa toda la portada y se toma el color de la marca que ha ganado: cada día, la portada es suya.
 *
 * Cinco escenas que pasan solas (se pueden pausar, saltar con las flechas, los puntos o deslizando):
 * presentación → titular → lo más importante → fotos → visítala. Las que no tienen datos se saltan.
 * Con "reducir movimiento" activado en el sistema, no pasan solas.
 *
 * variant="preview": la misma presentación en pequeño (Mi anuncio y moderación).
 */
export function WinnerShowcase({
  ad,
  showcase,
  winner,
  variant = "full",
  footer,
}: {
  ad: AdContent;
  showcase: Showcase;
  winner?: WinnerInfo;
  variant?: "full" | "preview";
  /** Algo más al pie (en la portada: la franja para pujar por mañana) */
  footer?: React.ReactNode;
}) {
  const full = variant === "full";
  const accent = showcase.themeColor ?? "#2437ff";
  const heroImage = showcase.heroImageUrl ?? ad.imageUrl;
  const title = showcase.title ?? ad.companyName;
  const name = showcase.siteName || ad.companyName;

  const photos = useMemo(() => {
    const unique: string[] = [];
    for (const url of [...showcase.galleryUrls, heroImage, ad.imageUrl]) {
      if (url && !unique.includes(url)) unique.push(url);
    }
    return unique.slice(0, 3);
  }, [showcase.galleryUrls, heroImage, ad.imageUrl]);

  const scenes = useMemo(() => {
    const list: SceneKind[] = ["intro", "headline"];
    if (showcase.highlights.length >= 2) list.push("points");
    if (showcase.galleryUrls.length >= 1 && photos.length >= 2) list.push("gallery");
    list.push("visit");
    return list;
  }, [showcase.highlights.length, showcase.galleryUrls.length, photos.length]);

  const reducedMotion = usePrefersReducedMotion();
  const [index, setIndex] = useState(0);
  const [userPaused, setUserPaused] = useState(false);
  const [cycle, setCycle] = useState(0);
  const pageHidden = usePageHidden();
  const paused = userPaused || reducedMotion || pageHidden;
  const scene = scenes[Math.min(index, scenes.length - 1)];

  const goTo = useCallback(
    (next: number) => {
      setIndex(((next % scenes.length) + scenes.length) % scenes.length);
      setCycle((value) => value + 1);
    },
    [scenes.length],
  );

  // Cada escena dura lo suyo; en pausa, el tiempo que queda se guarda y se retoma
  const remaining = useRef(SCENE_MS[scene]);
  const startedAt = useRef(0);
  useEffect(() => {
    remaining.current = SCENE_MS[scene];
  }, [scene, cycle]);
  useEffect(() => {
    if (paused) return;
    startedAt.current = performance.now();
    const timer = setTimeout(() => goTo(index + 1), remaining.current);
    return () => {
      clearTimeout(timer);
      remaining.current = Math.max(0, remaining.current - (performance.now() - startedAt.current));
    };
  }, [paused, index, cycle, goTo]);

  // Deslizar con el dedo (móvil) y flechas del teclado
  const touchStart = useRef<number | null>(null);
  function onPointerDown(event: React.PointerEvent) {
    if (event.pointerType !== "mouse") touchStart.current = event.clientX;
  }
  function onPointerUp(event: React.PointerEvent) {
    if (touchStart.current === null) return;
    const delta = event.clientX - touchStart.current;
    touchStart.current = null;
    if (Math.abs(delta) > 50) goTo(index + (delta < 0 ? 1 : -1));
  }
  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === "ArrowRight") goTo(index + 1);
    if (event.key === "ArrowLeft") goTo(index - 1);
  }

  return (
    <section
      className={cn(
        "@container relative isolate overflow-hidden text-white select-none",
        full ? "min-h-[calc(100svh-4rem)]" : "aspect-[16/10] rounded-lg",
        paused && "is-paused",
      )}
      style={
        {
          "--brand-x": accent,
          background: `radial-gradient(90% 70% at 80% 15%, color-mix(in oklab, ${accent} 60%, transparent), transparent 65%), linear-gradient(160deg, color-mix(in oklab, ${accent} 45%, #0e0d13) 0%, #0e0d13 72%)`,
        } as React.CSSProperties
      }
      aria-roledescription="slideshow"
      aria-label={`${name} presentation`}
      tabIndex={0}
      onKeyDown={onKeyDown}
      onPointerDown={onPointerDown}
      onPointerUp={onPointerUp}
    >
      {/* ---------- escenas ---------- */}
      <div key={`${scene}-${cycle}`} className="motion-scene animate-scene-in absolute inset-0">
        {scene === "intro" && <IntroScene name={name} showcase={showcase} winner={winner} full={full} />}
        {scene === "headline" && (
          <HeadlineScene image={heroImage} title={title} description={showcase.description ?? ad.description} domain={showcase.domain} full={full} />
        )}
        {scene === "points" && <PointsScene name={name} highlights={showcase.highlights} full={full} />}
        {scene === "gallery" && <GalleryScene photos={photos} caption={showcase.description ?? ad.description} full={full} />}
        {scene === "visit" && <VisitScene name={name} ad={ad} showcase={showcase} full={full} />}
      </div>

      {/* ---------- barra de escenas ---------- */}
      <div
        className={cn(
          "absolute inset-x-0 top-0 z-10 flex items-center gap-3",
          full ? "px-4 pt-4 sm:px-6 sm:pt-6" : "px-3 pt-3",
        )}
      >
        <div className="flex flex-1 gap-1.5" role="tablist" aria-label="Scenes">
          {scenes.map((kind, i) => (
            <button
              key={kind}
              type="button"
              role="tab"
              aria-selected={i === index}
              aria-label={SCENE_NAMES[kind]}
              onClick={() => goTo(i)}
              className="group relative h-6 flex-1"
            >
              <span className="absolute inset-x-0 top-1/2 block h-1 -translate-y-1/2 overflow-hidden rounded-sm bg-white/25 transition group-hover:bg-white/40">
                {i < index && <span className="absolute inset-0 bg-white" />}
                {i === index && (
                  <span
                    key={cycle}
                    className="segment-fill absolute inset-0 bg-white"
                    style={{ "--segment-duration": `${SCENE_MS[kind]}ms` } as React.CSSProperties}
                  />
                )}
              </span>
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={() => setUserPaused((value) => !value)}
          className="grid size-9 shrink-0 place-items-center rounded-md bg-black/25 text-white ring-1 ring-white/20 backdrop-blur transition hover:bg-black/40"
          aria-label={paused ? "Play the presentation" : "Pause the presentation"}
        >
          {paused ? <PlayIcon className="size-4" /> : <PauseIcon className="size-4" />}
        </button>
      </div>

      {/* ---------- flechas (escritorio) ---------- */}
      {full && (
        <>
          <button
            type="button"
            onClick={() => goTo(index - 1)}
            className="absolute left-3 top-1/2 z-10 hidden size-11 -translate-y-1/2 place-items-center rounded-full bg-black/20 text-white/80 ring-1 ring-white/15 backdrop-blur transition hover:bg-black/40 hover:text-white md:grid"
            aria-label="Previous scene"
          >
            <ChevronLeftIcon className="size-5" />
          </button>
          <button
            type="button"
            onClick={() => goTo(index + 1)}
            className="absolute right-3 top-1/2 z-10 hidden size-11 -translate-y-1/2 place-items-center rounded-full bg-black/20 text-white/80 ring-1 ring-white/15 backdrop-blur transition hover:bg-black/40 hover:text-white md:grid"
            aria-label="Next scene"
          >
            <ChevronRightIcon className="size-5" />
          </button>
        </>
      )}

      {/* ---------- siempre visible: quién es y su web ---------- */}
      <div
        className={cn(
          "absolute inset-x-0 z-10 flex items-center gap-3 bg-linear-to-t from-black/55 to-transparent",
          full ? "bottom-0 px-4 pb-16 pt-10 sm:px-6" : "bottom-0 px-3 pb-3 pt-8",
        )}
      >
        <SiteAvatar name={name} iconUrl={showcase.iconUrl} color={accent} className={full ? "size-11" : "size-8 rounded-lg text-sm"} />
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-2">
            <span className={cn("truncate font-bold", full ? "text-lg" : "text-sm")}>{ad.companyName}</span>
            {winner && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-sm bg-gold px-2 py-0.5 text-xs font-bold text-ink" title="Winner">
                <CrownIcon className="size-3.5" />
                <span className={full ? "hidden sm:inline" : "hidden"}>Winner</span>
              </span>
            )}
          </p>
          <p className={cn("truncate text-white/65", full ? "text-sm" : "text-xs")}>{showcase.domain}</p>
        </div>
        {ad.websiteUrl && (
          <a
            href={ad.websiteUrl}
            target="_blank"
            rel="noopener noreferrer sponsored"
            className={cn(
              "inline-flex shrink-0 items-center gap-2 rounded-md bg-white font-semibold text-ink transition hover:bg-gold",
              full ? "h-11 px-5 sm:h-12 sm:px-6" : "h-9 px-4 text-sm",
            )}
          >
            Visit website <ArrowUpRightIcon className="size-4" />
          </a>
        )}
      </div>

      {footer}
    </section>
  );
}

// ------------------------------------------------------------------ escenas

/** Logo y nombre de la marca, enormes, con la placa de ganador. */
function IntroScene({ name, showcase, winner, full }: { name: string; showcase: Showcase; winner?: WinnerInfo; full: boolean }) {
  return (
    <div className="flex size-full flex-col items-center justify-center px-6 text-center">
      <span
        className="animate-pop rounded-3xl p-1.5"
        style={{ boxShadow: "0 0 0 1px rgb(255 255 255 / 0.15), 0 30px 80px -20px var(--brand-x)" }}
      >
        <SiteAvatar
          name={name}
          iconUrl={showcase.iconUrl}
          color="var(--brand-x)"
          eager={full}
          className={cn("rounded-3xl", full ? "size-24 text-4xl sm:size-28" : "size-14 text-2xl")}
        />
      </span>
      <h2
        className="font-condensed animate-rise mt-6 max-w-[14ch] font-extrabold leading-[0.9] tracking-[-0.02em] text-balance [animation-delay:150ms]"
        style={{ fontSize: full ? "clamp(3.2rem, 13cqw, 11rem)" : "clamp(1.8rem, 11cqw, 4rem)" }}
      >
        {name}
      </h2>
      {winner && (
        <p
          className={cn(
            "animate-rise mt-6 inline-flex flex-wrap items-center justify-center gap-x-2 gap-y-1 rounded-md bg-black/25 text-white ring-1 ring-gold/60 backdrop-blur [animation-delay:450ms]",
            full ? "px-5 py-3 text-base" : "px-3 py-1.5 text-xs",
          )}
        >
          <CrownIcon className="size-5 text-gold" />
          Won the Arena on <strong className="font-semibold">{formatLongDate(winner.roundDate)}</strong> with{" "}
          <strong className="tabular font-semibold text-gold">{formatPoints(winner.wonWithPoints)}</strong>
        </p>
      )}
    </div>
  );
}

/** La foto principal de su web acercándose despacio, con su titular. */
function HeadlineScene({
  image,
  title,
  description,
  domain,
  full,
}: {
  image: string | null;
  title: string;
  description: string | null;
  domain: string;
  full: boolean;
}) {
  return (
    <div className="relative size-full">
      {image && (
        <div className="absolute inset-0 overflow-hidden">
          <Image src={apiUrl(image)} alt="" fill sizes="100vw" className="animate-kenburns object-cover" priority={full} />
        </div>
      )}
      <div className="absolute inset-0 bg-linear-to-r from-[#0e0d13]/90 via-[#0e0d13]/55 to-transparent" />
      <div className={cn("relative flex size-full flex-col justify-center", full ? "mx-auto max-w-6xl px-6 sm:px-10" : "px-5")}>
        <p className="animate-rise inline-flex w-fit items-center gap-2 rounded-sm bg-white/12 px-3 py-1 text-xs font-semibold text-white/85 ring-1 ring-white/20 backdrop-blur">
          <span className="size-1.5 rounded-full" style={{ background: "var(--brand-x)" }} />
          {domain}
        </p>
        <h2
          className="animate-rise mt-4 max-w-[18ch] font-display font-extrabold leading-[1.02] text-balance [animation-delay:150ms]"
          style={{ fontSize: full ? "clamp(2.4rem, 6.5cqw, 5.5rem)" : "clamp(1.3rem, 6cqw, 2.4rem)" }}
        >
          {title}
        </h2>
        {description && (
          <p
            className={cn(
              "animate-rise mt-4 max-w-xl text-white/80 text-pretty [animation-delay:350ms]",
              full ? "text-lg sm:text-xl" : "line-clamp-2 text-xs",
            )}
          >
            {description}
          </p>
        )}
      </div>
    </div>
  );
}

/** Sus frases destacadas, una a una. */
function PointsScene({ name, highlights, full }: { name: string; highlights: string[]; full: boolean }) {
  return (
    <div className={cn("flex size-full flex-col justify-center", full ? "mx-auto max-w-5xl px-6 sm:px-10" : "px-5")}>
      <p className={cn("animate-rise font-semibold text-white/70", full ? "text-lg" : "text-xs")}>At {name} you’ll find</p>
      <ul className={cn("mt-4", full ? "space-y-3 sm:space-y-4" : "space-y-1.5")}>
        {highlights.slice(0, 4).map((text, i) => (
          <li
            key={text}
            className="animate-rise flex items-baseline gap-4 font-display font-extrabold leading-tight"
            style={{ animationDelay: `${250 + i * 420}ms`, fontSize: full ? "clamp(1.6rem, 4.6cqw, 3.8rem)" : "clamp(0.95rem, 4.4cqw, 1.6rem)" }}
          >
            <span
              aria-hidden="true"
              className="inline-block size-[0.42em] shrink-0 translate-y-[-0.08em] rotate-45 rounded-[0.08em]"
              style={{ background: "color-mix(in oklab, var(--brand-x) 75%, white)" }}
            />
            <span className="text-balance">{text}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Sus fotos, flotando despacio. */
function GalleryScene({ photos, caption, full }: { photos: string[]; caption: string | null; full: boolean }) {
  const layouts = [
    "left-[6%] top-[14%] w-[52%] aspect-[4/3] [--tilt:-3deg] [--dx:14px] [--dy:-10px]",
    "right-[6%] top-[10%] w-[36%] aspect-[4/5] [--tilt:4deg] [--dx:-12px] [--dy:12px]",
    "right-[18%] bottom-[16%] w-[34%] aspect-[16/10] [--tilt:-2deg] [--dx:10px] [--dy:-14px]",
  ];
  return (
    <div className="relative size-full">
      {photos.map((photo, i) => (
        <div
          key={photo}
          className={cn(
            "animate-drift absolute overflow-hidden rounded-md shadow-pop ring-1 ring-white/20",
            full ? "" : "rounded-lg",
            layouts[i],
          )}
          style={{ animationDelay: `${i * -3}s` }}
        >
          <Image src={apiUrl(photo)} alt="" fill sizes="50vw" className="object-cover" />
        </div>
      ))}
      {caption && (
        <p
          className={cn(
            "animate-rise absolute bottom-[26%] left-[6%] max-w-md rounded-md bg-black/35 text-white backdrop-blur [animation-delay:400ms]",
            full ? "p-4 text-base sm:text-lg" : "line-clamp-2 p-2 text-[10px]",
          )}
        >
          {caption}
        </p>
      )}
    </div>
  );
}

/** El final: su nombre, su web y el botón para visitarla. */
function VisitScene({ name, ad, showcase, full }: { name: string; ad: AdContent; showcase: Showcase; full: boolean }) {
  return (
    <div className="flex size-full flex-col items-center justify-center px-6 text-center">
      <p className={cn("animate-rise font-semibold text-white/70", full ? "text-lg" : "text-xs")}>Discover</p>
      <h2
        className="font-condensed animate-rise mt-2 max-w-[16ch] font-extrabold leading-[0.92] text-balance [animation-delay:120ms]"
        style={{ fontSize: full ? "clamp(2.8rem, 10cqw, 8.5rem)" : "clamp(1.6rem, 9cqw, 3.4rem)" }}
      >
        {name}
      </h2>
      {ad.description && (
        <p className={cn("animate-rise mt-5 max-w-2xl text-white/80 text-pretty [animation-delay:320ms]", full ? "text-lg sm:text-xl" : "line-clamp-2 text-xs")}>
          {ad.description}
        </p>
      )}
      {ad.websiteUrl && full && (
        <a
          href={ad.websiteUrl}
          target="_blank"
          rel="noopener noreferrer sponsored"
          className="animate-rise mt-8 inline-flex h-14 items-center gap-2 rounded-md px-8 text-lg font-semibold text-ink [animation-delay:520ms]"
          style={{ background: "color-mix(in oklab, var(--brand-x) 25%, white)" }}
        >
          Go to {showcase.domain} <ArrowUpRightIcon className="size-5" />
        </a>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ utilidades

function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReduced(query.matches);
    const timer = setTimeout(update, 0);
    query.addEventListener("change", update);
    return () => {
      clearTimeout(timer);
      query.removeEventListener("change", update);
    };
  }, []);
  return reduced;
}

function usePageHidden(): boolean {
  const [hidden, setHidden] = useState(false);
  useEffect(() => {
    const update = () => setHidden(document.visibilityState === "hidden");
    document.addEventListener("visibilitychange", update);
    return () => document.removeEventListener("visibilitychange", update);
  }, []);
  return hidden;
}
