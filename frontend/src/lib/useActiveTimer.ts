"use client";

import { useEffect, useRef, useState } from "react";

/**
 * Cómo se está mirando la web del proyecto:
 *  - "frame": dentro de LaunchCrown (iframe). Cuenta mientras esta pestaña se ve, tiene el foco y hay
 *    alguien usándola.
 *  - "window": en su propia ventana (webs que no se dejan mostrar dentro de otras). Cuenta desde que
 *    la abres y mientras estás FUERA de LaunchCrown (en cuanto vuelves a LaunchCrown, se para).
 */
export type TimerMode = "frame" | "window";

/** Por qué no está contando. */
export type PauseReason =
  | "hidden" // (frame) cambiaste de pestaña o minimizaste
  | "blur" // (frame) estás en otra aplicación o ventana
  | "idle" // (frame) 45 s sin tocar nada
  | "closed" // (window) todavía no has abierto la web (o pulsaste "I'm done")
  | "here" // (window) estás en LaunchCrown, no en la web
  | null;

interface ActiveTimerOptions {
  /** Cuenta solo si es true (p. ej. el usuario puede ganar puntos con esta web). */
  enabled: boolean;
  mode: TimerMode;
  /** Cada cuánto se completa un tramo (10 s). */
  tickMs: number;
  /** Se completaron {@code count} tramos (normalmente 1; varios al volver de otra ventana). */
  onTicks: (count: number) => void;
  /** (frame) Sin actividad durante este tiempo → en pausa (45 s). */
  idleMs?: number;
  /** (frame) ¿Está el usuario dentro de la web? (el ratón encima o el foco en el iframe) */
  isFrameEngaged?: () => boolean;
  /** (window) ¿Has abierto la web (y no has terminado)? */
  isWindowOpen?: () => boolean;
}

const ACTIVITY_EVENTS = ["pointermove", "pointerdown", "keydown", "wheel", "scroll", "touchstart"] as const;
const SAMPLE_MS = 200;

/**
 * Cuenta el tiempo que alguien pasa MIRANDO la web de otro proyecto. Se pausa AL INSTANTE si deja
 * de mirarla (Page Visibility API, eventos blur/focus, inactividad o la ventana de la web cerrada).
 * El tiempo en pausa no se recupera.
 *
 * IMPORTANTE: esto es solo la primera barrera (el navegador se puede manipular). Quien decide los
 * puntos es el servidor, que nunca paga más tiempo del que ha pasado de verdad.
 */
export function useActiveTimer({
  enabled,
  mode,
  tickMs,
  onTicks,
  idleMs = 45_000,
  isFrameEngaged,
  isWindowOpen,
}: ActiveTimerOptions) {
  const [progressMs, setProgressMs] = useState(0);
  const [pauseReason, setPauseReason] = useState<PauseReason>(null);
  const callbacks = useRef({ onTicks, isFrameEngaged, isWindowOpen });
  useEffect(() => {
    callbacks.current = { onTicks, isFrameEngaged, isWindowOpen };
  }, [onTicks, isFrameEngaged, isWindowOpen]);

  useEffect(() => {
    if (!enabled) return;

    let accumulated = 0;
    let lastSample = performance.now();
    let lastActivity = Date.now();

    const computeReason = (): PauseReason => {
      if (mode === "window") {
        if (!callbacks.current.isWindowOpen?.()) return "closed";
        const away = document.visibilityState !== "visible" || !document.hasFocus();
        return away ? null : "here";
      }
      if (document.visibilityState !== "visible") return "hidden";
      if (!document.hasFocus()) return "blur";
      const engaged = callbacks.current.isFrameEngaged?.() ?? false;
      if (!engaged && Date.now() - lastActivity > idleMs) return "idle";
      return null;
    };

    // Empieza "sin motivo de pausa"; la primera comprobación (enseguida) pone el estado real
    let reason: PauseReason = null;

    const update = () => {
      const now = performance.now();
      const nextReason = computeReason();
      // Dentro de LaunchCrown: solo suma si estaba activo al principio Y al final del intervalo.
      // En ventana aparte: basta con el principio (al volver, el navegador avisa en el acto; y en el
      // móvil la pestaña de LaunchCrown se "congela" mientras estás en la otra web).
      const counts = mode === "frame" ? reason === null && nextReason === null : reason === null;
      if (counts) {
        accumulated += now - lastSample;
      }
      if (accumulated >= tickMs) {
        const count = Math.floor(accumulated / tickMs);
        accumulated -= count * tickMs;
        callbacks.current.onTicks(count);
      }
      lastSample = now;
      if (nextReason !== reason) {
        reason = nextReason;
        setPauseReason(nextReason);
      }
      setProgressMs(accumulated);
    };

    const onActivity = () => {
      const wasIdle = reason === "idle";
      lastActivity = Date.now();
      if (wasIdle) update();
    };

    document.addEventListener("visibilitychange", update);
    window.addEventListener("blur", update);
    window.addEventListener("focus", update);
    window.addEventListener("pagehide", update);
    ACTIVITY_EVENTS.forEach((type) => window.addEventListener(type, onActivity, { passive: true }));
    const interval = setInterval(update, SAMPLE_MS);
    const first = setTimeout(update, 0);

    return () => {
      clearInterval(interval);
      clearTimeout(first);
      document.removeEventListener("visibilitychange", update);
      window.removeEventListener("blur", update);
      window.removeEventListener("focus", update);
      window.removeEventListener("pagehide", update);
      ACTIVITY_EVENTS.forEach((type) => window.removeEventListener(type, onActivity));
    };
  }, [enabled, mode, tickMs, idleMs]);

  return { progressMs: enabled ? progressMs : 0, pauseReason: enabled ? pauseReason : null };
}
