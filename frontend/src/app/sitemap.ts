import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";
import { getHistoryOnServer } from "@/lib/server-data";

/**
 * Cuándo cambió por última vez el contenido de cada página fija. Google solo se fía de esta fecha si es
 * verdad, así que hay que actualizarla A MANO cuando se cambie el texto de la página (no en cada despliegue).
 */
const UPDATED = {
  howItWorks: "2026-10-01",
  promote: "2026-09-29",
  earn: "2026-09-29",
  about: "2026-10-01",
  launchGuide: "2026-10-01",
  productHuntAlternative: "2026-10-01",
  terms: "2026-09-29",
  privacy: "2026-09-29",
};

// Se regenera como mucho cada hora (la fecha de la Race y de los ganadores cambia una vez al día)
export const revalidate = 3600;

/**
 * /sitemap.xml: la lista de páginas públicas para que Google las encuentre todas. Solo lleva la fecha de
 * último cambio (Google ignora "changefreq" y "priority"). La portada, la Race y los ganadores cambian con
 * cada Race que se cierra; si no se puede leer, se usa la de hoy (esas páginas sí cambian a diario).
 * /signup no va: es un formulario, no tiene contenido que buscar.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const history = await getHistoryOnServer();
  const lastRace = history?.items[0]?.closedAt ?? new Date().toISOString();

  const page = (path: string, lastModified: string): MetadataRoute.Sitemap[number] => ({
    // La portada, sin barra final: igual que su dirección canónica
    url: path === "/" ? SITE_URL : `${SITE_URL}${path}`,
    lastModified,
  });

  return [
    page("/", lastRace),
    page("/race", lastRace),
    page("/how-it-works", UPDATED.howItWorks),
    page("/promote", UPDATED.promote),
    page("/earn", UPDATED.earn),
    page("/winners", lastRace),
    page("/about", UPDATED.about),
    page("/guides/where-to-launch-your-startup", UPDATED.launchGuide),
    page("/alternatives/product-hunt", UPDATED.productHuntAlternative),
    page("/legal/terms", UPDATED.terms),
    page("/legal/privacy", UPDATED.privacy),
  ];
}
