import type { Metadata } from "next";
import { HomeClient } from "@/components/home/HomeClient";
import { HomeStatic } from "@/components/home/HomeStatic";
import { JsonLd } from "@/components/seo/JsonLd";
import { ORGANIZATION_ID, SITE_DESCRIPTION, SITE_NAME, SITE_URL, pageJsonLd } from "@/lib/site";

const TITLE = `${SITE_NAME} · Free startup promotion: win the homepage daily`;

// La portada es la dirección canónica "/" (la descripción es la del layout)
export const metadata: Metadata = {
  title: { absolute: TITLE },
  alternates: { canonical: "/" },
  openGraph: {
    title: TITLE,
    description: SITE_DESCRIPTION,
    url: "/",
    siteName: SITE_NAME,
    type: "website",
    locale: "en_US",
  },
  twitter: { card: "summary_large_image", title: TITLE, description: SITE_DESCRIPTION },
};

/** Qué es LaunchCrown como aplicación (gratis, en el navegador). Solo en la portada. */
const APP_JSON_LD = {
  "@type": "WebApplication",
  "@id": `${SITE_URL}/#app`,
  name: SITE_NAME,
  url: SITE_URL,
  image: `${SITE_URL}/opengraph-image`,
  applicationCategory: "BusinessApplication",
  operatingSystem: "Any (web browser)",
  description: SITE_DESCRIPTION,
  offers: { "@type": "Offer", price: "0", priceCurrency: "EUR" },
  provider: { "@id": ORGANIZATION_ID },
};

/**
 * Portada: el anuncio ganador de hoy y la Race (HomeClient, con datos en directo) y, debajo, lo que no
 * cambia (HomeStatic: ya escrito en el HTML para que Google lo lea).
 *
 * El título principal (h1) va aquí, ya escrito en el HTML: el titular grande de la Race depende de los
 * datos del día (y con ganador cambia), así que Google no siempre lo veía. Está oculto a la vista
 * (sr-only) porque repite lo que ya dice la página; los lectores de pantalla sí lo leen.
 */
export default function HomePage() {
  return (
    <>
      <JsonLd data={pageJsonLd({ path: "/", name: TITLE, description: SITE_DESCRIPTION, extra: [APP_JSON_LD] })} />
      <h1 className="sr-only">LaunchCrown: free startup promotion. Win tomorrow’s homepage for 24 hours</h1>
      <HomeClient />
      <HomeStatic />
    </>
  );
}
