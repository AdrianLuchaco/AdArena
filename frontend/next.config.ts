import type { NextConfig } from "next";

const isDev = process.env.NODE_ENV === "development";

/**
 * Dirección de la API y del tiempo real (las mismas que usa lib/config.ts). En la nube la API va por
 * la misma dirección que la web (apiUrl = "", cubierto por 'self') y el WebSocket directo al backend.
 */
const apiUrl = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081").replace(/\/$/, "");
const wsUrl = (process.env.NEXT_PUBLIC_WS_URL ?? `${apiUrl.replace(/^http/, "ws")}/ws`).replace(/\/ws$/, "");

/** Anuncios de Ezoic activados (la misma comprobación que lib/ads.ts). */
const adsEnabled = process.env.NEXT_PUBLIC_EZOIC_ENABLED === "true";

/**
 * Content Security Policy: lista blanca de lo que la web puede cargar. Si alguien consiguiera
 * inyectar código (XSS), el navegador no le dejaría cargar scripts de otras webs ni enviar datos
 * a ningún servidor que no sea nuestra API.
 *  - script-src 'unsafe-inline': Next.js inserta pequeños scripts en la página para arrancar React.
 *    La alternativa (nonces) obliga a renderizar cada página en el servidor en cada visita.
 *  - 'unsafe-eval' solo en local: React lo usa para mostrar errores más claros al programar.
 *
 * Con los anuncios de Ezoic activados, la política se abre a cualquier web HTTPS en scripts, imágenes,
 * iframes y conexiones: Ezoic (y los anunciantes que venden a través de él) sirven los anuncios desde
 * muchos dominios que cambian con el tiempo, así que no se puede hacer una lista. Se aplica a todas las
 * páginas porque la web navega sin recargar. Los huecos de anuncios solo están en la portada, la Arena
 * y Promote (nunca donde se ganan puntos).
 */
const external = adsEnabled ? " https:" : "";
const csp = [
  "default-src 'self'",
  // 'unsafe-eval': en local lo usa React para sus mensajes de error; con Ezoic, su script de estadísticas
  `script-src 'self' 'unsafe-inline'${isDev || adsEnabled ? " 'unsafe-eval'" : ""}${external}`,
  "style-src 'self' 'unsafe-inline'",
  `img-src 'self' data: blob: ${apiUrl}${external}`,
  "font-src 'self'",
  `connect-src 'self' ${apiUrl} ${wsUrl}${external}`,
  // Iframes: el visor de "Gana puntos" muestra las webs de los proyectos (cualquier web https) y los
  // anuncios de Google también van en iframes. Las webs se cargan aisladas (atributo sandbox): no
  // pueden leer LaunchCrown ni cambiar de página la pestaña.
  "frame-src 'self' https:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  ...(isDev ? [] : ["upgrade-insecure-requests"]),
].join("; ");

const nextConfig: NextConfig = {
  // No anunciar qué tecnología usa la web
  poweredByHeader: false,

  images: {
    // Las imágenes ya llegan optimizadas desde nuestro backend (reducidas y re-codificadas),
    // así que Next.js las muestra tal cual, sin volver a procesarlas.
    unoptimized: true,
  },

  // La web se tradujo al inglés (fase 11): las direcciones antiguas en español siguen funcionando
  // (pueden estar en emails ya enviados o en enlaces guardados)
  async redirects() {
    const moved: [string, string][] = [
      ["/ganar/extra", "/earn/links"],
      ["/ganar", "/earn"],
      ["/proyecto/:id", "/watch/:id"],
      ["/visitar/:id", "/watch/link/:id"],
      ["/promocionar", "/promote"],
      ["/ganadores", "/winners"],
      ["/como-funciona", "/how-it-works"],
      ["/entrar", "/login"],
      ["/registro", "/signup"],
      ["/recuperar", "/forgot-password"],
      ["/restablecer", "/reset-password"],
      ["/panel/avisos", "/account/notifications"],
      ["/panel/pujas", "/account/bids"],
      ["/panel/puntos", "/account/points"],
      ["/panel/saldo", "/account/points"],
      ["/panel", "/account"],
      ["/admin/ajustes", "/admin/settings"],
      ["/admin/moderacion", "/admin/moderation"],
      ["/admin/promociones", "/admin/promotions"],
      ["/admin/recargas", "/admin/promotions"],
      ["/admin/registro", "/admin/audit-log"],
      ["/legal/terminos", "/legal/terms"],
      ["/legal/privacidad", "/legal/privacy"],
      // Cambio de nombre a LaunchCrown: la Arena pasa a ser la Race
      ["/arena", "/race"],
    ];
    return moved.map(([source, destination]) => ({ source, destination, permanent: true }));
  },

  // Cabeceras de seguridad para todas las páginas
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
          // HTTPS obligatorio durante 2 años (el navegador lo recuerda). Solo tiene efecto en HTTPS.
          ...(isDev ? [] : [{ key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" }]),
        ],
      },
    ];
  },
};

export default nextConfig;
