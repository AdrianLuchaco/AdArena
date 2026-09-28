import Link from "next/link";
import { cn } from "@/lib/cn";
import { Spinner } from "./Spinner";

type Variant = "primary" | "gold" | "secondary" | "ghost" | "dark" | "glass";
type Size = "sm" | "md" | "lg";

/*
 * Botones "de marcador": esquinas casi rectas. El principal lleva sombra dura que se "hunde" al
 * pulsarlo, como un botón físico.
 */
const base =
  "inline-flex items-center justify-center gap-2 rounded-md font-semibold transition-all duration-150 " +
  "disabled:pointer-events-none disabled:opacity-50 whitespace-nowrap";

const variants: Record<Variant, string> = {
  primary:
    "bg-brand text-white ring-2 ring-ink shadow-hard hover:bg-brand-dark active:translate-x-[3px] active:translate-y-[3px] active:shadow-none",
  /** Amarillo "meta": el botón principal sobre la pista azul */
  gold: "bg-gold text-ink ring-2 ring-ink shadow-hard hover:bg-white active:translate-x-[3px] active:translate-y-[3px] active:shadow-none",
  secondary: "bg-surface text-ink ring-2 ring-ink hover:bg-canvas",
  ghost: "text-ink-soft hover:bg-ink/5 hover:text-ink",
  dark: "bg-ink text-white hover:bg-ink-soft",
  /** Translúcido, para fondos oscuros o fotos */
  glass: "bg-white/10 text-white ring-1 ring-white/30 backdrop-blur hover:bg-white/20",
};

const sizes: Record<Size, string> = {
  sm: "h-9 px-4 text-sm",
  md: "h-11 px-5 text-[15px]",
  lg: "h-13 px-7 text-base",
};

export function buttonClasses(variant: Variant = "primary", size: Size = "md", className?: string) {
  return cn(base, variants[variant], sizes[size], className);
}

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
}

export function Button({ variant, size, loading, className, children, disabled, ...props }: ButtonProps) {
  return (
    <button className={buttonClasses(variant, size, className)} disabled={disabled || loading} {...props}>
      {loading && <Spinner className="size-4" />}
      {children}
    </button>
  );
}

interface ButtonLinkProps extends React.ComponentProps<typeof Link> {
  variant?: Variant;
  size?: Size;
}

export function ButtonLink({ variant, size, className, ...props }: ButtonLinkProps) {
  return <Link className={buttonClasses(variant, size, className)} {...props} />;
}
