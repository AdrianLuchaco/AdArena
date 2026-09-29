import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

/**
 * /robots.txt: qué pueden visitar los buscadores. Todo lo público, sí; las zonas privadas (cuenta,
 * admin), los formularios de contraseña, el visor de puntos y la API, no. Y dónde está el sitemap.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/account", "/admin", "/watch/", "/forgot-password", "/reset-password", "/api/"],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
