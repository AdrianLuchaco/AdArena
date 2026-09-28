import { cn } from "@/lib/cn";
import { AlertIcon, CheckIcon, InfoIcon } from "../icons";

type Tone = "info" | "success" | "warning" | "danger";

const tones: Record<Tone, string> = {
  info: "bg-brand-soft text-ink ring-brand/30",
  success: "bg-success-soft text-success ring-success/20",
  warning: "bg-warning-soft text-warning ring-warning/20",
  danger: "bg-danger-soft text-danger ring-danger/20",
};

export function Alert({
  tone = "info",
  title,
  children,
  className,
}: {
  tone?: Tone;
  title?: string;
  children?: React.ReactNode;
  className?: string;
}) {
  const IconComponent = tone === "success" ? CheckIcon : tone === "info" ? InfoIcon : AlertIcon;
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={cn("flex gap-3 rounded-md border-l-4 border-current px-4 py-3 text-sm ring-1 animate-fade-in", tones[tone], className)}
    >
      <IconComponent className="mt-0.5 size-5 shrink-0" />
      <div className="space-y-0.5">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className="leading-relaxed opacity-90">{children}</div>}
      </div>
    </div>
  );
}
