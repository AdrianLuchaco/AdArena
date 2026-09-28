/**
 * Anuncios de Ezoic (no Google AdSense).
 *
 * Se activan con NEXT_PUBLIC_EZOIC_ENABLED=true (variable de Vercel) cuando Ezoic haya aprobado tu
 * web. Sin ella no se carga NADA de Ezoic: ni scripts, ni cookies, ni huecos vacíos.
 *
 * Cada hueco tiene un número fijo (Ezoic los llama "placeholders"). Créalos en el panel de Ezoic con
 * ESTOS números (Ezoic → Ad Placements → New placeholder). Reglas de la web: nunca hay anuncios donde
 * se ganan puntos (Earn points, Bonus links, el visor): solo en la portada, la Arena y Promote.
 */
export const ADS_ENABLED = process.env.NEXT_PUBLIC_EZOIC_ENABLED === "true";

export const AD_PLACEMENTS = {
  /** Portada: bajo "How to win" */
  homeBanner: 101,
  /** La Arena: bajo la clasificación */
  arenaBanner: 102,
  /** Promote: bajo la cabecera */
  promoteTop: 103,
  /** Promote: columna derecha (escritorio) */
  promoteSidebar: 104,
  /** Promote: columna derecha, segundo hueco (escritorio) */
  promoteSidebarLower: 105,
  /** Promote sin sesión: entre los pasos y los botones (móvil) */
  promoteGuest: 106,
  /** Promote: bajo el formulario */
  promoteAfterForm: 107,
  /** Promote: entre tus promociones */
  promoteInList: 108,
  /** Promote: al final */
  promoteBottom: 109,
} as const;

export type AdPlacement = keyof typeof AD_PLACEMENTS;
