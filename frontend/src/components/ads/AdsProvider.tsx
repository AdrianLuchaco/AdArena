"use client";

import { EzoicProvider, config, setEzoicAnchorAd, setInterstitialAllowed, setOutstreamAllowed } from "@ezoic/react-sdk";
import { useEffect } from "react";
import { ADS_ENABLED } from "@/lib/ads";

/**
 * Carga Ezoic (su aviso de cookies, que exige la ley en Europa, y su script) una sola vez para toda la
 * web. Sin NEXT_PUBLIC_EZOIC_ENABLED=true no hace nada.
 */
export function AdsProvider({ children }: { children: React.ReactNode }) {
  if (!ADS_ENABLED) return <>{children}</>;
  return (
    <EzoicProvider analyticsUrl="https://ezoicanalytics.com/analytics.js">
      <OnlyOurPlaceholders />
      {children}
    </EzoicProvider>
  );
}

/**
 * Solo anuncios en los huecos que ponemos nosotros. Se desactivan los formatos que Ezoic coloca por
 * su cuenta en cualquier página (anuncio fijo abajo, a pantalla completa, vídeo flotante, laterales):
 * aparecerían también donde se ganan puntos, y eso no se permite. Va antes que el resto de la web
 * para que se aplique antes de pedir el primer anuncio. Desactívalos también en el panel de Ezoic.
 */
function OnlyOurPlaceholders() {
  useEffect(() => {
    config({
      disableVideo: true,
      disableInterstitial: true,
      disableLeftSideRail: true,
      disableRightSideRail: true,
      disableSidebarFloating: true,
      reservePlaceholderSpace: true,
    });
    setEzoicAnchorAd(false);
    setInterstitialAllowed(false);
    void setOutstreamAllowed(false);
  }, []);
  return null;
}
