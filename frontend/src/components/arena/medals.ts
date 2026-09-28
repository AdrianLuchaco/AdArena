/** Colores de la posición: amarillo para el 1.º, plata y bronce para el 2.º y el 3.º; neutro para el resto. */
export function medalClasses(position: number): { badge: string; soft: string; text: string } {
  switch (position) {
    case 1:
      return { badge: "bg-gold text-ink", soft: "bg-gold-soft", text: "text-gold-dark" };
    case 2:
      return { badge: "bg-silver text-white", soft: "bg-silver-soft", text: "text-silver" };
    case 3:
      return { badge: "bg-bronze text-white", soft: "bg-bronze-soft", text: "text-bronze" };
    default:
      return { badge: "bg-canvas text-ink-soft ring-1 ring-line", soft: "bg-canvas", text: "text-muted" };
  }
}

export { ordinal as positionLabel } from "@/lib/format";
