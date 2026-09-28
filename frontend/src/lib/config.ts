/**
 * Dirección del backend (la API).
 *  - En tu ordenador: http://localhost:8081.
 *  - En la nube: NEXT_PUBLIC_API_URL=/ → la API está en la misma dirección que la web (la web
 *    reenvía /api/* al backend: ver src/proxy.ts). Queda "" y las rutas son relativas ("/api/…").
 */
export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081").replace(/\/$/, "");

/**
 * El tiempo real (WebSocket) va directo al backend: la web no puede reenviar WebSockets.
 * En la nube: NEXT_PUBLIC_WS_URL=wss://tu-backend.onrender.com/ws
 */
export const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? `${API_URL.replace(/^http/, "ws")}/ws`;

/** Convierte una ruta del backend ("/api/public/images/…") en una URL completa. */
export function apiUrl(path: string): string {
  return path.startsWith("http") ? path : `${API_URL}${path}`;
}
