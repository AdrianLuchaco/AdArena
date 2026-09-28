import { ADSENSE_CLIENT, ADSENSE_ENABLED } from "@/lib/adsense";

/**
 * /ads.txt: Google lo exige para servir anuncios. Dice que tu cuenta de AdSense está autorizada a
 * vender los espacios de esta web. Se genera solo a partir de NEXT_PUBLIC_ADSENSE_CLIENT.
 */
export function GET() {
  if (!ADSENSE_ENABLED) {
    return new Response("Not found", { status: 404 });
  }
  const publisher = ADSENSE_CLIENT.replace(/^ca-/, "");
  return new Response(`google.com, ${publisher}, DIRECT, f08c47fec0942fa0\n`, {
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=86400" },
  });
}
