import { NextResponse, type NextRequest } from "next/server";

/**
 * En la nube, la web (Vercel) reenvía las llamadas a /api/* al backend (Render). Así, para el
 * navegador la API está en la MISMA dirección que la web:
 *  - la cookie de sesión es "propia" y los navegadores no la bloquean (entre *.vercel.app y
 *    *.onrender.com sí la bloquearían: tendrías que volver a entrar cada vez que recargas);
 *  - no hace falta comprar un dominio para que funcione.
 *
 * Además le pasa al backend la IP real del visitante junto con una clave compartida (PROXY_SECRET),
 * para que los límites contra abusos se apliquen a cada persona y no a Vercel.
 *
 * En tu ordenador BACKEND_URL no existe: la web llama directamente a http://localhost:8081.
 */
export function proxy(request: NextRequest) {
  const backend = process.env.BACKEND_URL?.replace(/\/$/, "");
  if (!backend) return NextResponse.next();

  const target = new URL(request.nextUrl.pathname + request.nextUrl.search, backend);
  const headers = new Headers(request.headers);
  // Nunca fiarse de lo que mande el navegador en estas cabeceras: se ponen aquí
  headers.delete("x-adarena-client-ip");
  headers.delete("x-adarena-proxy-secret");
  const ip = request.headers.get("x-real-ip") ?? request.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
  const secret = process.env.PROXY_SECRET;
  if (ip && secret) {
    headers.set("x-adarena-client-ip", ip);
    headers.set("x-adarena-proxy-secret", secret);
  }
  return NextResponse.rewrite(target, { request: { headers } });
}

export const config = {
  matcher: "/api/:path*",
};
