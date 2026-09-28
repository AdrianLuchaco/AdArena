/**
 * Configuración de Google AdSense (variables de entorno de Vercel):
 *  - NEXT_PUBLIC_ADSENSE_CLIENT: tu identificador de editor, "ca-pub-1234567890123456".
 *  - NEXT_PUBLIC_ADSENSE_SLOT_SIDEBAR / _INFEED / _BANNER: los IDs de los bloques de anuncios que
 *    crees en AdSense (Anuncios → Por bloque de anuncios → Anuncio de display).
 * Sin NEXT_PUBLIC_ADSENSE_CLIENT no se carga NADA de Google (ni scripts ni cookies).
 */
export const ADSENSE_CLIENT = process.env.NEXT_PUBLIC_ADSENSE_CLIENT ?? "";

export const ADSENSE_SLOTS = {
  sidebar: process.env.NEXT_PUBLIC_ADSENSE_SLOT_SIDEBAR ?? "",
  infeed: process.env.NEXT_PUBLIC_ADSENSE_SLOT_INFEED ?? "",
  banner: process.env.NEXT_PUBLIC_ADSENSE_SLOT_BANNER ?? "",
} as const;

export type AdSlotName = keyof typeof ADSENSE_SLOTS;

export const ADSENSE_ENABLED = /^ca-pub-\d{10,20}$/.test(ADSENSE_CLIENT);
