import { cn } from "@/lib/cn";
import type { SocialPlatform } from "@/lib/types";

/** Colores de cada red (sin logotipos: solo su nombre con un color reconocible). */
const STYLES: Record<SocialPlatform, string> = {
  YOUTUBE: "bg-[#ff0033] text-white",
  X: "bg-ink text-white",
  INSTAGRAM: "bg-linear-to-r from-[#f58529] via-[#dd2a7b] to-[#8134af] text-white",
  TIKTOK: "bg-ink text-[#25f4ee]",
  TWITCH: "bg-[#9146ff] text-white",
  LINKEDIN: "bg-[#0a66c2] text-white",
  FACEBOOK: "bg-[#1877f2] text-white",
  GITHUB: "bg-[#24292f] text-white",
  WEB: "bg-canvas text-ink-soft ring-1 ring-line",
};

/** El color de cada red (para su inicial cuando no tenemos su logo). Las webs usan el suyo. */
const COLORS: Record<SocialPlatform, string | null> = {
  YOUTUBE: "#ff0033",
  X: "#16161d",
  INSTAGRAM: "#dd2a7b",
  TIKTOK: "#16161d",
  TWITCH: "#9146ff",
  LINKEDIN: "#0a66c2",
  FACEBOOK: "#1877f2",
  GITHUB: "#24292f",
  WEB: null,
};

export function platformColor(platform: SocialPlatform): string | null {
  return COLORS[platform];
}

/** El fondo de color de cada red (para las fichas de Bonus links). */
export function platformSurface(platform: SocialPlatform): string {
  return STYLES[platform];
}

export function PlatformBadge({ platform, label }: { platform: SocialPlatform; label: string }) {
  return (
    <span className={cn("inline-flex items-center rounded-sm px-2 py-0.5 text-xs font-bold", STYLES[platform])}>
      {label}
    </span>
  );
}
