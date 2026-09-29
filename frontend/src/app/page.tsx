import type { Metadata } from "next";
import { HomeClient } from "@/components/home/HomeClient";
import { HomeStatic } from "@/components/home/HomeStatic";
import { SITE_DESCRIPTION, SITE_NAME, SITE_TAGLINE } from "@/lib/site";

// La portada es la dirección canónica "/" (el título y la descripción son los del layout)
export const metadata: Metadata = {
  alternates: { canonical: "/" },
  openGraph: {
    title: `${SITE_NAME} · ${SITE_TAGLINE}`,
    description: SITE_DESCRIPTION,
    url: "/",
    siteName: SITE_NAME,
    type: "website",
    locale: "en_US",
  },
};

/**
 * Portada: el anuncio ganador de hoy y la Race (HomeClient, con datos en directo) y, debajo, lo que no
 * cambia (HomeStatic: ya escrito en el HTML para que Google lo lea).
 */
export default function HomePage() {
  return (
    <>
      <HomeClient />
      <HomeStatic />
    </>
  );
}
