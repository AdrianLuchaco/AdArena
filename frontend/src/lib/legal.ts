/**
 * Datos de contacto que aparecen en las páginas legales (Términos y Privacidad).
 * Por defecto es el mismo email con el que la web envía los avisos (MAIL_FROM en Render). Si más
 * adelante tienes un email con tu dominio (p. ej. hola@tudominio.com), ponlo en Vercel como
 * NEXT_PUBLIC_CONTACT_EMAIL y vuelve a desplegar.
 */
export const CONTACT_EMAIL = process.env.NEXT_PUBLIC_CONTACT_EMAIL || "agencyluchaco@gmail.com";

/** Fecha de la versión vigente de los textos legales (la de los Términos va también en el backend). */
export const LEGAL_UPDATED = "29 September 2026";
