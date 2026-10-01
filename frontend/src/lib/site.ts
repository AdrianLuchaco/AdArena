import type { Metadata } from "next";

/**
 * Datos de la web para Google y las redes sociales (SEO): la dirección oficial, el nombre y la
 * descripción. La dirección oficial es la que Google debe indexar (la "canónica"): las demás
 * (launchcrown.com sin www, adarena-lilac.vercel.app) apuntan a esta.
 * Si algún día cambia, ponla en Vercel como NEXT_PUBLIC_SITE_URL (sin barra final) y vuelve a desplegar.
 */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || "https://www.launchcrown.com").replace(/\/$/, "");

export const SITE_NAME = "LaunchCrown";

/** Lo que sale bajo el título en Google (unos 155 caracteres como mucho). */
export const SITE_DESCRIPTION =
  "Promote your startup for free. Every day, projects bid Crown Points to take over the LaunchCrown homepage for 24 hours. Earn points by discovering startups.";

export const SITE_TAGLINE = "Win the homepage for your startup, every day";

/**
 * Perfiles oficiales de LaunchCrown en otras webs (X, LinkedIn, YouTube, Product Hunt…). Google los usa
 * para saber que son la misma marca ("sameAs"). Añádelos aquí cuando existan, con la dirección completa.
 */
export const SOCIAL_PROFILES: string[] = [];

/** Quiénes somos, para Google: el mismo @id en todas las páginas para que sepa que es una sola organización. */
export const ORGANIZATION_ID = `${SITE_URL}/#organization`;
export const WEBSITE_ID = `${SITE_URL}/#website`;

export const ORGANIZATION_JSON_LD = {
  "@type": "Organization",
  "@id": ORGANIZATION_ID,
  name: SITE_NAME,
  url: SITE_URL,
  // Google pide un logo en imagen normal (PNG, cuadrado, de 112 px como mínimo), no SVG
  logo: { "@type": "ImageObject", url: `${SITE_URL}/logo.png`, width: 512, height: 512 },
  image: `${SITE_URL}/opengraph-image`,
  description: SITE_DESCRIPTION,
  slogan: SITE_TAGLINE,
  ...(SOCIAL_PROFILES.length > 0 ? { sameAs: SOCIAL_PROFILES } : {}),
};

/**
 * Datos estructurados de una página: qué tipo de página es y dónde está dentro de la web (las "migas de
 * pan": Inicio › Ganadores). Google lo usa para entender la web y para mostrar la ruta en los resultados.
 * `extra` añade más cosas a la misma página (p. ej. la lista de ganadores).
 */
export function pageJsonLd({
  path,
  name,
  description,
  type = "WebPage",
  breadcrumb,
  dateModified,
  extra = [],
}: {
  path: string;
  name: string;
  description: string;
  type?: "WebPage" | "AboutPage" | "CollectionPage" | "FAQPage";
  /** Los pasos desde la portada, sin incluirla: [["Winners", "/winners"]] */
  breadcrumb?: [string, string][];
  /** "2026-10-01": cuándo cambió el contenido de verdad por última vez */
  dateModified?: string;
  extra?: object[];
}): object {
  const url = path === "/" ? SITE_URL : `${SITE_URL}${path}`;
  const page = {
    "@type": type,
    "@id": `${url}#webpage`,
    url,
    name,
    description,
    inLanguage: "en",
    isPartOf: { "@id": WEBSITE_ID },
    publisher: { "@id": ORGANIZATION_ID },
    ...(dateModified ? { dateModified } : {}),
    ...(breadcrumb ? { breadcrumb: { "@id": `${url}#breadcrumb` } } : {}),
  };
  const crumbs = breadcrumb && {
    "@type": "BreadcrumbList",
    "@id": `${url}#breadcrumb`,
    itemListElement: [["Home", "/"] as [string, string], ...breadcrumb].map(([label, href], index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: label,
      item: href === "/" ? SITE_URL : `${SITE_URL}${href}`,
    })),
  };
  return { "@context": "https://schema.org", "@graph": [page, ...(crumbs ? [crumbs] : []), ...extra] };
}

/**
 * Datos estructurados (JSON-LD) listos para meter en un <script type="application/ld+json">.
 * Se escapa "<" para que ningún texto pueda cerrar la etiqueta (recomendación de Next.js).
 */
export function jsonLd(data: object): string {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}

/**
 * La imagen para compartir (la genera app/opengraph-image.tsx). Las páginas que definen su propio
 * openGraph dejan de heredarla, así que hay que ponerla a mano.
 */
const SHARE_IMAGE = {
  url: "/opengraph-image",
  width: 1200,
  height: 630,
  alt: `${SITE_NAME}: win the homepage for your startup, every day`,
};

/**
 * Metadatos completos de una página pública: título, descripción, dirección canónica y cómo se ve al
 * compartirla en X, WhatsApp, LinkedIn… (Open Graph). La imagen la pone app/opengraph-image.tsx.
 * Hace falta repetirlos por página porque, en Next.js, el openGraph de una página sustituye al del
 * layout entero (no se mezclan).
 */
export function pageMetadata({ title, description, path }: { title: string; description: string; path: string }): Metadata {
  return {
    title,
    description,
    alternates: { canonical: path },
    openGraph: {
      title: `${title} · ${SITE_NAME}`,
      description,
      url: path,
      siteName: SITE_NAME,
      type: "website",
      locale: "en_US",
      images: [SHARE_IMAGE],
    },
    twitter: { card: "summary_large_image", title: `${title} · ${SITE_NAME}`, description, images: [SHARE_IMAGE.url] },
  };
}

/** Páginas privadas o de trámite (cuenta, admin, login…): que Google no las muestre. */
export const NO_INDEX: Metadata = { robots: { index: false, follow: false } };
