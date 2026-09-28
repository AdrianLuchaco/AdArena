import Image from "next/image";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import { formatPoints, formatLongDate, prettyUrl } from "@/lib/format";
import { ArrowUpRightIcon, CrownIcon, ImageIcon, TrophyIcon } from "../icons";

export interface AdContent {
  companyName: string;
  description: string;
  websiteUrl: string;
  imageUrl: string | null;
}

/** Datos del triunfo: se muestran en la placa de ganador. */
export interface WinnerInfo {
  wonWithPoints: number;
  /** Día en que compitió (AAAA-MM-DD) */
  roundDate: string;
}

/**
 * La "página de anuncio" (regla 10): nunca metemos la web del anunciante en un iframe.
 * Mostramos su imagen, nombre y descripción, y un botón que abre SU web en una pestaña nueva.
 *
 * - variant="full": portada a pantalla completa. Con {@code winner}, la imagen lleva el marco
 *   dorado de trofeo y una placa con el día que ganó y con cuánto.
 * - variant="preview": vista previa compacta en el panel ("así se verá tu anuncio").
 */
export function AdView({
  ad,
  variant = "full",
  winner,
  previewLabel = "How it looks if you win",
}: {
  ad: AdContent;
  variant?: "full" | "preview";
  winner?: WinnerInfo;
  /** Texto de la etiqueta en la vista previa */
  previewLabel?: string;
}) {
  const full = variant === "full";
  const Heading = full ? "h1" : "h2";
  const image = ad.imageUrl ? apiUrl(ad.imageUrl) : null;

  return (
    <section
      className={cn(
        "relative isolate overflow-hidden bg-ink text-white",
        full ? "min-h-[calc(100svh-4rem)]" : "rounded-lg",
      )}
      aria-label={`Ad for ${ad.companyName || "your project"}`}
    >
      {/* Fondo: la misma imagen, muy desenfocada, para envolver el anuncio en sus colores */}
      {image && (
        <Image
          src={image}
          alt=""
          fill
          sizes="100vw"
          className="-z-10 scale-125 object-cover opacity-45 blur-3xl"
          priority={full}
        />
      )}
      <div className="absolute inset-0 -z-10 bg-linear-to-b from-ink/30 via-ink/40 to-ink/80" />

      <div
        className={cn(
          "mx-auto grid items-center",
          full
            ? "min-h-[calc(100svh-4rem)] max-w-6xl gap-8 px-4 pb-24 pt-20 sm:px-6 md:grid-cols-[1.15fr_1fr] md:gap-12 md:pb-16"
            : "gap-5 p-5 sm:grid-cols-[1.1fr_1fr] sm:p-6",
        )}
      >
        <div
          className={cn(
            "relative w-full overflow-hidden",
            winner ? "trophy-frame" : "bg-white/10 ring-1 ring-white/15",
            full ? "aspect-[4/3] rounded-lg shadow-pop" : "aspect-[4/3] rounded-md",
          )}
          style={winner ? ({ "--trophy-fill": "#0c0f1f" } as React.CSSProperties) : undefined}
        >
          {image ? (
            <Image
              src={image}
              alt={`Image of ${ad.companyName}`}
              fill
              sizes={full ? "(min-width: 768px) 55vw, 100vw" : "(min-width: 640px) 30vw, 100vw"}
              className="object-cover"
              priority={full}
            />
          ) : (
            <div className="flex size-full flex-col items-center justify-center gap-2 text-white/60">
              <ImageIcon className="size-10" />
              <span className="text-sm">Your image goes here</span>
            </div>
          )}
          {winner && (
            <>
              <span className="absolute left-4 top-4 inline-flex items-center gap-1.5 rounded-sm bg-gold px-3 py-1.5 text-sm font-bold text-ink shadow-card">
                <CrownIcon className="size-4" /> Winner
              </span>
              <span className="trophy-sheen" aria-hidden="true" />
            </>
          )}
        </div>

        <div className={cn("flex flex-col", full ? "gap-6" : "gap-3")}>
          {winner ? (
            <p className="flex w-fit flex-wrap items-center gap-x-2 gap-y-1 rounded-md bg-gold/15 px-4 py-2.5 text-sm text-white ring-1 ring-gold/50">
              <TrophyIcon className="size-5 text-gold" />
              <span>
                Won the Arena on <strong className="font-semibold">{formatLongDate(winner.roundDate)}</strong> with{" "}
                <strong className="tabular font-semibold text-gold">{formatPoints(winner.wonWithPoints)}</strong>
              </span>
            </p>
          ) : (
            <span className="inline-flex w-fit items-center gap-2 rounded-sm bg-white/12 px-3 py-1 text-xs font-semibold text-white/85 ring-1 ring-white/15">
              <span className="size-1.5 rounded-full bg-brand" />
              {full ? "Today’s ad" : previewLabel}
            </span>
          )}
          <Heading
            className={cn(
              "font-display font-black uppercase leading-[0.95] text-balance break-words",
              full ? "text-5xl sm:text-6xl lg:text-7xl" : "text-3xl",
            )}
          >
            {ad.companyName || "Your project’s name"}
          </Heading>
          <p
            className={cn(
              "whitespace-pre-line text-white/85 text-pretty",
              full ? "text-lg leading-relaxed sm:text-xl" : "text-sm leading-relaxed",
            )}
          >
            {ad.description || "Your ad’s description will appear here."}
          </p>
          <div className={cn("flex flex-col gap-2", full ? "pt-2" : "pt-1")}>
            {ad.websiteUrl ? (
              <a
                href={ad.websiteUrl}
                target="_blank"
                rel="noopener noreferrer sponsored"
                className={cn(
                  "inline-flex w-fit items-center gap-2 rounded-md bg-white font-semibold text-ink transition hover:bg-gold",
                  full ? "h-14 px-8 text-lg" : "h-10 px-5 text-sm",
                )}
              >
                Visit website
                <ArrowUpRightIcon className={full ? "size-5" : "size-4"} />
              </a>
            ) : (
              <span className="inline-flex h-10 w-fit items-center rounded-md bg-white/20 px-5 text-sm font-semibold">
                Visit website
              </span>
            )}
            {ad.websiteUrl && <span className="text-sm text-white/60">{prettyUrl(ad.websiteUrl)}</span>}
          </div>
        </div>
      </div>
    </section>
  );
}
