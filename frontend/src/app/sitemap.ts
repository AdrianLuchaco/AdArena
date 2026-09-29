import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

/**
 * /sitemap.xml: la lista de páginas públicas para que Google las encuentre todas. La portada, la Race
 * y los ganadores cambian cada día; las guías y los textos legales, casi nunca.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  const page = (
    path: string,
    changeFrequency: MetadataRoute.Sitemap[number]["changeFrequency"],
    priority: number,
  ): MetadataRoute.Sitemap[number] => ({ url: `${SITE_URL}${path}`, lastModified: now, changeFrequency, priority });

  return [
    page("/", "daily", 1),
    page("/race", "daily", 0.9),
    page("/how-it-works", "monthly", 0.8),
    page("/earn", "daily", 0.7),
    page("/promote", "weekly", 0.7),
    page("/winners", "daily", 0.6),
    page("/signup", "yearly", 0.5),
    page("/legal/terms", "yearly", 0.2),
    page("/legal/privacy", "yearly", 0.2),
  ];
}
