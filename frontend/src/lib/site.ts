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
  "Promote your startup for free. Every day, projects bid Crown Points to take over the LaunchCrown homepage for 24 hours. Earn points by discovering other startups.";

export const SITE_TAGLINE = "Win the homepage for your startup, every day";

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
