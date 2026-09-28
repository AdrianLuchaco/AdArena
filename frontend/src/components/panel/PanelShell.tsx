"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useArena } from "@/lib/arena-context";
import { useAuth } from "@/lib/auth-context";
import { cn } from "@/lib/cn";
import { Button } from "../ui/Button";

const SECTIONS = [
  { href: "/account", label: "My ad" },
  { href: "/account/points", label: "My points" },
  { href: "/account/bids", label: "My bids" },
  { href: "/account/notifications", label: "Notifications" },
];

/** Estructura de "Account": saludo, salir, pestañas de secciones y contenido. */
export function PanelShell({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const { unreadCount } = useArena();
  const pathname = usePathname();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 sm:py-10">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted">{user?.email}</p>
          <h1 className="mt-1 truncate text-5xl sm:text-6xl">Hi, {user?.displayName?.split(" ")[0] ?? "there"}</h1>
        </div>
        <Button variant="secondary" size="sm" onClick={() => void handleLogout()}>
          Log out
        </Button>
      </div>

      <nav className="-mx-4 mb-8 flex overflow-x-auto border-b-2 border-ink px-4 sm:mx-0 sm:px-0" aria-label="Account sections">
        {SECTIONS.map((section) => {
          const active = pathname === section.href;
          const showBadge = section.href === "/account/notifications" && unreadCount > 0;
          return (
            <Link
              key={section.href}
              href={section.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "-mb-0.5 flex shrink-0 items-center gap-2 border-b-4 px-4 py-3 text-sm font-semibold transition",
                active ? "border-brand text-ink" : "border-transparent text-ink-soft hover:text-ink",
              )}
            >
              {section.label}
              {showBadge && (
                <span className="tabular rounded-sm bg-live px-1.5 text-[11px] font-bold leading-5 text-white">
                  {unreadCount > 9 ? "9+" : unreadCount}
                </span>
              )}
            </Link>
          );
        })}
      </nav>

      {children}
    </div>
  );
}
