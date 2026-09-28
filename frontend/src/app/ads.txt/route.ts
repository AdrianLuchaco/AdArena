import { ADS_ENABLED } from "@/lib/ads";

/**
 * /ads.txt: los anunciantes lo exigen para comprar los espacios de una web. Ezoic lo gestiona por ti
 * (su "Ads.txt Manager"): aquí solo se redirige a su versión para tu dominio, que Ezoic mantiene al
 * día. Solo con Ezoic activado; sin él, no existe (404).
 */
export function GET(request: Request) {
  if (!ADS_ENABLED) {
    return new Response("Not found", { status: 404 });
  }
  // Tu dominio, sin "www." (adarena.com)
  const domain = new URL(request.url).hostname.replace(/^www\./, "");
  return Response.redirect(`https://srv.adstxtmanager.com/19390/${domain}`, 301);
}
