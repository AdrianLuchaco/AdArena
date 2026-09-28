"use client";

import { EzoicAd } from "@ezoic/react-sdk";
import { AD_PLACEMENTS, ADS_ENABLED, type AdPlacement } from "@/lib/ads";
import { cn } from "@/lib/cn";

/**
 * Un hueco de anuncio de Ezoic. REGLAS (las de Ezoic y las de Google, que vende a través de Ezoic):
 *  - Solo en páginas donde NO se ganan puntos (nunca en Earn points, Bonus links ni en el visor).
 *  - Los puntos nunca premian ver ni pulsar anuncios, y no se invita a pulsarlos.
 * Sin Ezoic activado no ocupa espacio.
 */
export function AdUnit({ placement, className }: { placement: AdPlacement; className?: string }) {
  if (!ADS_ENABLED) return null;
  return (
    <div className={cn("overflow-hidden", className)} aria-label="Advertisement">
      <p className="mb-1 text-center text-[11px] text-muted">Advertisement</p>
      <EzoicAd id={AD_PLACEMENTS[placement]} />
    </div>
  );
}
