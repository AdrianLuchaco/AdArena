"use client";

import Script from "next/script";
import { useEffect, useRef } from "react";
import { ADSENSE_CLIENT, ADSENSE_ENABLED, ADSENSE_SLOTS, type AdSlotName } from "@/lib/adsense";
import { cn } from "@/lib/cn";

declare global {
  interface Window {
    adsbygoogle?: unknown[];
  }
}

/**
 * El script de AdSense. Se añade UNA vez, solo en las páginas con anuncios y solo si AdSense está
 * configurado. Para el aviso de cookies que exige Google en Europa, activa en AdSense
 * "Privacidad y mensajes" (su propia CMP): la muestra este mismo script.
 */
export function AdSenseScript() {
  if (!ADSENSE_ENABLED) return null;
  return (
    <Script
      id="adsense"
      async
      strategy="afterInteractive"
      crossOrigin="anonymous"
      src={`https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE_CLIENT}`}
    />
  );
}

/**
 * Un bloque de anuncios de AdSense. REGLAS (normas de AdSense):
 *  - Solo en páginas donde NO se ganan puntos (nunca en "Gana puntos" ni en la página de un proyecto).
 *  - Los puntos nunca premian ver ni pulsar anuncios, y no se invita a pulsarlos.
 * Sin AdSense configurado no ocupa espacio.
 */
export function AdSenseUnit({ slot, className }: { slot: AdSlotName; className?: string }) {
  const slotId = ADSENSE_SLOTS[slot];
  const pushed = useRef(false);

  useEffect(() => {
    if (!ADSENSE_ENABLED || !slotId || pushed.current) return;
    pushed.current = true;
    try {
      (window.adsbygoogle = window.adsbygoogle || []).push({});
    } catch {
      // Bloqueador de anuncios o AdSense aún cargando: el hueco se queda vacío
    }
  }, [slotId]);

  // Sin AdSense configurado no ocupa espacio
  if (!ADSENSE_ENABLED || !slotId) return null;

  return (
    <div className={cn("overflow-hidden", className)} aria-label="Advertisement">
      <p className="mb-1 text-center text-[11px] text-muted">Advertisement</p>
      <ins
        className="adsbygoogle"
        style={{ display: "block" }}
        data-ad-client={ADSENSE_CLIENT}
        data-ad-slot={slotId}
        data-ad-format={slot === "sidebar" ? "vertical" : "auto"}
        data-full-width-responsive="true"
      />
    </div>
  );
}
