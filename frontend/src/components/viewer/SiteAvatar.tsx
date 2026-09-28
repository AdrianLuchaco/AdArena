import Image from "next/image";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";

/** El logo de una web (leído de ella) o, si no tiene, su inicial sobre su color de marca. */
export function SiteAvatar({
  name,
  iconUrl,
  color,
  className,
  eager = false,
}: {
  name: string;
  iconUrl: string | null;
  color: string | null;
  className?: string;
  /** Cárgalo ya (es lo primero que se ve de la página) */
  eager?: boolean;
}) {
  if (iconUrl) {
    return (
      <Image
        src={apiUrl(iconUrl)}
        alt=""
        width={96}
        height={96}
        loading={eager ? "eager" : "lazy"}
        className={cn("size-9 shrink-0 rounded-md bg-white object-cover ring-1 ring-black/5", className)}
      />
    );
  }
  return (
    <span
      aria-hidden="true"
      className={cn("grid size-9 shrink-0 place-items-center rounded-md font-display text-base font-extrabold text-white", className)}
      style={{ background: color ?? "var(--color-brand)" }}
    >
      {(name.trim()[0] ?? "?").toUpperCase()}
    </span>
  );
}
