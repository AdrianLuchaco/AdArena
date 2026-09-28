// Formatos de la web (en inglés). Las horas siempre en la de Madrid, que es la de la Arena.
const points = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 0 });

/** 1250 → "1,250 pts" (versión corta, para cifras, tablas y botones) */
export function formatPoints(value: number): string {
  return `${points.format(value)} pts`;
}

/** 1250 → "1,250 points"; 1 → "1 point" (para frases) */
export function pointsText(value: number): string {
  return `${points.format(value)} ${value === 1 ? "point" : "points"}`;
}

/** 1250 → "1,250" (solo el número) */
export function formatNumber(value: number): string {
  return points.format(value);
}

/**
 * Tiempo restante en formato humano:
 *  - más de 1 hora  → "13h 20m"
 *  - menos de 1 hora → "12:04" (minutos:segundos, para que se vea correr)
 */
export function formatRemaining(ms: number): string {
  if (ms <= 0) return "0:00";
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours >= 1) {
    return `${hours}h ${minutes.toString().padStart(2, "0")}m`;
  }
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/** Partes del contador grande: { hours: "13", minutes: "20", seconds: "05" } */
export function countdownParts(ms: number) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const pad = (n: number) => n.toString().padStart(2, "0");
  return {
    hours: pad(Math.floor(totalSeconds / 3600)),
    minutes: pad(Math.floor((totalSeconds % 3600) / 60)),
    seconds: pad(totalSeconds % 60),
  };
}

/** "02:14:33" (para marcadores) */
export function formatClock(ms: number): string {
  const { hours, minutes, seconds } = countdownParts(ms);
  return `${hours}:${minutes}:${seconds}`;
}

const madridTime = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "Europe/Madrid",
});

/** "00:00" en hora de Madrid */
export function formatMadridTime(iso: string): string {
  return madridTime.format(new Date(iso));
}

/** Quita "https://" y la barra final para mostrar la web de forma limpia. */
export function prettyUrl(url: string): string {
  return url.replace(/^https?:\/\//, "").replace(/\/$/, "");
}

/**
 * Lo que escribe el usuario ("250", "1,250", "1.250", "1250 pts") → puntos (1250).
 * Devuelve null si no es un número entero de puntos.
 */
export function parsePoints(input: string): number | null {
  const cleaned = input.replace(/pts?|points?|\s|[.,]/gi, "");
  if (!/^\d{1,9}$/.test(cleaned)) return null;
  return Number(cleaned);
}

/** Puntos → texto editable ("1250") */
export function pointsToInput(value: number): string {
  return String(value);
}

const longDate = new Intl.DateTimeFormat("en-GB", {
  weekday: "long",
  day: "numeric",
  month: "long",
  timeZone: "UTC",
});

/** "2026-09-25" → "Friday 25 September" */
export function formatLongDate(isoDate: string): string {
  return longDate.format(new Date(`${isoDate}T00:00:00Z`));
}

const shortDateTime = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "Europe/Madrid",
});

/** "26 Sept, 14:05" en hora de Madrid */
export function formatDateTime(iso: string): string {
  return shortDateTime.format(new Date(iso));
}

/** 1 → "1st", 2 → "2nd", 3 → "3rd", 11 → "11th" */
export function ordinal(position: number): string {
  const tens = position % 100;
  if (tens >= 11 && tens <= 13) return `${position}th`;
  switch (position % 10) {
    case 1:
      return `${position}st`;
    case 2:
      return `${position}nd`;
    case 3:
      return `${position}rd`;
    default:
      return `${position}th`;
  }
}
