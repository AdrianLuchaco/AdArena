"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useArena } from "@/lib/arena-context";
import { useAuth } from "@/lib/auth-context";
import { cn } from "@/lib/cn";
import { formatClock, formatPoints } from "@/lib/format";
import { useCountdown } from "@/lib/hooks";
import { Logo } from "../Logo";
import { BellIcon, CloseIcon, MenuIcon, SparklesIcon } from "../icons";
import { Button, ButtonLink } from "../ui/Button";

/** Lo principal es la Arena (va aparte, como marcador); después, las otras tres secciones. */
const NAV = [
  { href: "/earn", label: "Earn points" },
  { href: "/promote", label: "Promote" },
  { href: "/winners", label: "Winners" },
];

/**
 * Cabecera: el marcador de la Arena (cuenta atrás en directo) es el primer botón, porque ganar la
 * puja es lo principal. Con sesión: tus puntos, tus avisos y "Account" (tu anuncio, puntos, pujas).
 */
export function SiteHeader() {
  const { status, user, logout } = useAuth();
  const { unreadCount, points } = useArena();
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);

  async function handleLogout() {
    setOpen(false);
    await logout();
    router.push("/");
  }

  const close = () => setOpen(false);

  const account =
    status === "loading" ? (
      <div className="h-9 w-40 animate-pulse rounded-md bg-ink/10" aria-hidden="true" />
    ) : user ? (
      <>
        {points !== null && (
          <Link
            href="/account/points"
            onClick={close}
            className="tabular inline-flex h-9 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-md bg-surface px-3 text-sm font-bold text-ink ring-1 ring-line transition hover:ring-ink"
            aria-label={`Your points: ${formatPoints(points)}`}
          >
            <SparklesIcon className="size-4 text-brand" />
            {formatPoints(points)}
          </Link>
        )}
        <Link
          href="/account/notifications"
          onClick={close}
          className="relative grid size-9 shrink-0 place-items-center rounded-md text-ink-soft transition hover:bg-ink/5 hover:text-ink"
          aria-label={unreadCount > 0 ? `Notifications: ${unreadCount} unread` : "Notifications"}
        >
          <BellIcon className="size-5" />
          {unreadCount > 0 && (
            <span className="tabular absolute -right-0.5 -top-0.5 grid min-w-5 place-items-center rounded-sm bg-live px-1 text-[11px] font-bold leading-5 text-white">
              {unreadCount > 9 ? "9+" : unreadCount}
            </span>
          )}
        </Link>
        {user.role === "ADMIN" && (
          <ButtonLink href="/admin" variant="ghost" size="sm" onClick={close}>
            Admin
          </ButtonLink>
        )}
        <ButtonLink href="/account" variant="dark" size="sm" onClick={close}>
          Account
        </ButtonLink>
      </>
    ) : (
      <>
        <ButtonLink href="/login" variant="ghost" size="sm" onClick={close}>
          Log in
        </ButtonLink>
        <ButtonLink href="/signup" size="sm" onClick={close}>
          Sign up free
        </ButtonLink>
      </>
    );

  return (
    <header className="sticky top-0 z-40 border-b-2 border-ink bg-canvas/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4 sm:px-6">
        <Logo />

        <ArenaTicker active={pathname.startsWith("/race")} onClick={close} />

        <nav className="hidden h-full items-stretch lg:flex" aria-label="Main">
          {NAV.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex items-center whitespace-nowrap border-b-4 px-3 pt-1 text-sm font-semibold transition",
                  active ? "border-brand text-ink" : "border-transparent text-ink-soft hover:text-ink",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto hidden items-center gap-2 lg:flex">{account}</div>

        <button
          type="button"
          className="ml-auto grid size-10 place-items-center rounded-md text-ink hover:bg-ink/5 lg:hidden"
          aria-label={open ? "Close menu" : "Open menu"}
          aria-expanded={open}
          onClick={() => setOpen((value) => !value)}
        >
          {open ? <CloseIcon className="size-6" /> : <MenuIcon className="size-6" />}
        </button>
      </div>

      {open && (
        <div className="animate-fade-in border-t border-line bg-canvas px-4 pb-5 pt-2 lg:hidden">
          <nav className="flex flex-col" aria-label="Main (mobile)">
            {[{ href: "/race", label: "The Race: bid for the homepage" }, ...NAV, { href: "/how-it-works", label: "How it works" }].map(
              (item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={close}
                  className="border-b border-line py-3 font-display text-2xl font-bold uppercase text-ink"
                >
                  {item.label}
                </Link>
              ),
            )}
          </nav>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            {account}
            {user && (
              <Button variant="ghost" size="sm" onClick={handleLogout}>
                Log out
              </Button>
            )}
          </div>
        </div>
      )}
    </header>
  );
}

/**
 * El marcador de la Arena en la cabecera: en directo, con la cuenta atrás hasta el cierre. Es el
 * acceso principal de la web (pujar por la portada de mañana).
 */
function ArenaTicker({ active, onClick }: { active: boolean; onClick: () => void }) {
  const { data, clockOffset, live } = useArena();
  const remaining = useCountdown(data?.round?.endsAt, clockOffset);
  return (
    <Link
      href="/race"
      onClick={onClick}
      className={cn(
        "group flex h-10 shrink-0 items-center gap-2 rounded-md bg-night pl-2.5 pr-3 text-white transition",
        active ? "ring-2 ring-brand ring-offset-2 ring-offset-canvas" : "hover:bg-ink-soft",
      )}
      aria-label={data?.round ? `The Race closes in ${formatClock(remaining)}` : "The Race"}
    >
      <span className={cn("size-2 rounded-full", live ? "bg-live animate-pulse-dot" : "bg-white/40")} aria-hidden="true" />
      <span className="font-display text-lg font-black uppercase leading-none tracking-wide">Race</span>
      {data?.round && (
        <span className="tabular hidden font-display text-lg font-bold leading-none text-gold min-[370px]:inline">
          {formatClock(remaining)}
        </span>
      )}
    </Link>
  );
}
