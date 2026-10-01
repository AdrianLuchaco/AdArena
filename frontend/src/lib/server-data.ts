import "server-only";
import { API_URL } from "./config";
import type { HistoryPage } from "./types";

/**
 * Datos públicos leídos DESDE EL SERVIDOR de la web (no desde el navegador), para que la página llegue
 * ya rellena en el HTML: Google la lee entera sin ejecutar JavaScript ni esperar a la API.
 *
 * Nunca rompe la página: si el backend tarda (Render dormido) o falla, devuelve null y la página se
 * comporta como siempre (el navegador pide los datos al cargar).
 *
 * En la nube la API está en BACKEND_URL (la web reenvía /api/* ahí: ver src/proxy.ts); en tu ordenador,
 * en http://localhost:8081.
 */
const BACKEND = (process.env.BACKEND_URL || (API_URL.startsWith("http") ? API_URL : "")).replace(/\/$/, "");

/** Cuánto esperar al backend antes de rendirse (Render gratis puede tardar ~30 s en despertar). */
const TIMEOUT_MS = 8_000;

async function getPublic<T>(path: string, revalidateSeconds: number): Promise<T | null> {
  if (!BACKEND) return null;
  try {
    const response = await fetch(`${BACKEND}${path}`, {
      next: { revalidate: revalidateSeconds },
      signal: AbortSignal.timeout(TIMEOUT_MS),
      headers: { Accept: "application/json" },
    });
    return response.ok ? ((await response.json()) as T) : null;
  } catch {
    return null;
  }
}

/** La primera página del historial de ganadores (la misma que pide /winners al cargar). */
export function getHistoryOnServer(): Promise<HistoryPage | null> {
  return getPublic<HistoryPage>("/api/public/history?page=0&size=10", 300);
}
