"use client";

import { useCountdown } from "@/lib/hooks";
import { countdownParts } from "@/lib/format";
import { cn } from "@/lib/cn";

/**
 * Contador HH:MM:SS de marcador de estadio (fichas con la raya de las pantallas de paletas). Cada
 * cifra que cambia "cae" suavemente (animación tick). En los dos últimos minutos se pone en rojo.
 * variant="hero": grande sobre fondo oscuro. variant="chip": pequeño, en línea.
 */
export function Countdown({
  endsAt,
  clockOffset,
  variant = "hero",
  className,
}: {
  endsAt: string;
  clockOffset: number;
  variant?: "hero" | "chip";
  className?: string;
}) {
  const remaining = useCountdown(endsAt, clockOffset);
  const parts = countdownParts(remaining);
  const units = [
    { value: parts.hours, label: "hours" },
    { value: parts.minutes, label: "min" },
    { value: parts.seconds, label: "sec" },
  ];
  const urgent = remaining > 0 && remaining <= 2 * 60 * 1000;

  if (variant === "chip") {
    return (
      <span className={cn("tabular inline-flex items-center gap-0.5 font-bold", urgent && "text-live", className)}>
        {parts.hours}:{parts.minutes}:
        <span key={parts.seconds} className="inline-block animate-tick">
          {parts.seconds}
        </span>
      </span>
    );
  }

  return (
    <div className={cn("flex items-start gap-1.5 sm:gap-2.5", className)} role="timer" aria-label="Time left">
      {units.map((unit, index) => (
        <div key={unit.label} className="flex items-start gap-1.5 sm:gap-2.5">
          {index > 0 && (
            <span className="pt-2 font-display text-4xl font-black text-white/50 sm:pt-3 sm:text-6xl">:</span>
          )}
          <div className="flex flex-col items-center">
            <span
              className={cn(
                "flap tabular relative grid min-w-[4.25rem] place-items-center overflow-hidden rounded-md px-2 py-1 font-display text-5xl font-black leading-none sm:min-w-28 sm:text-7xl",
                urgent ? "bg-live text-white ring-2 ring-ink" : "bg-ink text-gold ring-2 ring-ink",
              )}
            >
              <span key={unit.value} className="inline-block animate-tick py-1">
                {unit.value}
              </span>
            </span>
            <span className="mt-1.5 text-xs font-semibold text-white/80">{unit.label}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
