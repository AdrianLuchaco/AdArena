import { LogoMark } from "../Logo";

/** Tarjeta centrada para las pantallas de entrar y registrarse. */
export function AuthCard({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
}) {
  return (
    <div className="relative flex flex-1 items-center justify-center overflow-hidden px-4 py-12 sm:py-20">
      <div className="relative w-full max-w-md">
        <div className="rounded-lg bg-surface p-6 shadow-lift ring-2 ring-ink sm:p-9">
          <LogoMark className="size-10" />
          <h1 className="mt-5 text-5xl">{title}</h1>
          <p className="mt-2 text-ink-soft">{subtitle}</p>
          <div className="mt-7">{children}</div>
        </div>
        <p className="mt-6 text-center text-sm text-ink-soft">{footer}</p>
      </div>
    </div>
  );
}
