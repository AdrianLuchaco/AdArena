import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

/**
 * /robots.txt: qué pueden visitar los buscadores. Todo lo público, sí; las zonas privadas (cuenta,
 * admin), los formularios de contraseña, el visor de puntos y la API privada, no. Y dónde está el sitemap.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      // /api/public/ sí: las páginas piden ahí sus datos (Race, ganadores…) y Google tiene que poder cargarlos
      // para ver la página como un visitante. Gana la regla más específica, así que el resto de /api/ sigue cerrado.
      allow: ["/", "/api/public/"],
      disallow: ["/account", "/admin", "/watch/", "/forgot-password", "/reset-password", "/api/"],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
