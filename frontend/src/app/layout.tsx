import type { Metadata, Viewport } from "next";
import { Archivo, Big_Shoulders } from "next/font/google";
import { AdsProvider } from "@/components/ads/AdsProvider";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { ToastProvider } from "@/components/ui/Toaster";
import { ArenaProvider } from "@/lib/arena-context";
import { AuthProvider } from "@/lib/auth-context";
import { SITE_DESCRIPTION, SITE_NAME, SITE_TAGLINE, SITE_URL, jsonLd } from "@/lib/site";
import "./globals.css";

// Tipografías: Archivo para el texto y Big Shoulders (rotulación de estadio) para titulares y cifras
const body = Archivo({ variable: "--font-body", subsets: ["latin"] });
const heading = Big_Shoulders({ variable: "--font-heading", subsets: ["latin"], axes: ["opsz"] });

export const metadata: Metadata = {
  // Base de todas las direcciones absolutas (canónicas, Open Graph, sitemap…)
  metadataBase: new URL(SITE_URL),
  title: {
    default: `${SITE_NAME} · ${SITE_TAGLINE}`,
    template: `%s · ${SITE_NAME}`,
  },
  description: SITE_DESCRIPTION,
  applicationName: SITE_NAME,
  keywords: [
    "promote your startup",
    "free startup promotion",
    "launch your startup",
    "startup directory",
    "indie hackers",
    "side project",
    "product launch",
    "free advertising",
    "homepage takeover",
    "get traffic to your website",
  ],
  // La dirección canónica NO va aquí: la heredarían todas las páginas (cada una pone la suya)
  openGraph: {
    title: `${SITE_NAME} · ${SITE_TAGLINE}`,
    description: SITE_DESCRIPTION,
    siteName: SITE_NAME,
    type: "website",
    locale: "en_US",
  },
  twitter: { card: "summary_large_image", title: `${SITE_NAME} · ${SITE_TAGLINE}`, description: SITE_DESCRIPTION },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, "max-image-preview": "large", "max-snippet": -1, "max-video-preview": -1 },
  },
  category: "business",
};

/** Quién somos y qué es la web, para Google (datos estructurados de schema.org). */
const SITE_JSON_LD = {
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "Organization",
      "@id": `${SITE_URL}/#organization`,
      name: SITE_NAME,
      url: SITE_URL,
      logo: `${SITE_URL}/icon.svg`,
    },
    {
      "@type": "WebSite",
      "@id": `${SITE_URL}/#website`,
      name: SITE_NAME,
      url: SITE_URL,
      description: SITE_DESCRIPTION,
      inLanguage: "en",
      publisher: { "@id": `${SITE_URL}/#organization` },
    },
    {
      "@type": "WebApplication",
      name: SITE_NAME,
      url: SITE_URL,
      applicationCategory: "BusinessApplication",
      operatingSystem: "Any (web browser)",
      description: SITE_DESCRIPTION,
      offers: { "@type": "Offer", price: "0", priceCurrency: "EUR" },
    },
  ],
};

export const viewport: Viewport = {
  themeColor: "#eef0f5",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" data-scroll-behavior="smooth" className={`${body.variable} ${heading.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col">
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLd(SITE_JSON_LD) }} />
        <AdsProvider>
          <ToastProvider>
            <AuthProvider>
              <ArenaProvider>
                <SiteHeader />
                <main className="flex flex-1 flex-col">{children}</main>
                <SiteFooter />
              </ArenaProvider>
            </AuthProvider>
          </ToastProvider>
        </AdsProvider>
      </body>
    </html>
  );
}
