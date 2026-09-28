/**
 * Valida el parámetro ?next= para volver a la página de la que venía el usuario tras entrar.
 * Solo se aceptan rutas internas: así nadie puede usar un enlace de AdArena para redirigir a
 * una web externa (phishing).
 */
export function safeNextPath(next: string | null, fallback = "/account"): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
    return fallback;
  }
  return next;
}
