"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError, getNotifications, markAllNotificationsRead } from "@/lib/api";
import { useArena } from "@/lib/arena-context";
import { cn } from "@/lib/cn";
import { formatDateTime } from "@/lib/format";
import type { NotificationItem, NotificationType } from "@/lib/types";
import { Alert } from "../ui/Alert";
import { ButtonLink } from "../ui/Button";
import { PageSpinner } from "../ui/Spinner";
import { AlertIcon, BellIcon, CheckIcon, CrownIcon, GavelIcon, WalletIcon } from "../icons";

const ICONS: Record<NotificationType, { icon: typeof BellIcon; className: string }> = {
  OUTBID: { icon: GavelIcon, className: "bg-brand-soft text-brand" },
  AUCTION_WON: { icon: CrownIcon, className: "bg-gold-soft text-gold-dark" },
  CANDIDATE_PROMOTED: { icon: CrownIcon, className: "bg-gold-soft text-gold-dark" },
  AD_APPROVED: { icon: CheckIcon, className: "bg-success-soft text-success" },
  AUCTION_LOST: { icon: BellIcon, className: "bg-canvas text-ink-soft" },
  WINNER_REFUNDED: { icon: WalletIcon, className: "bg-canvas text-ink-soft" },
  AD_REJECTED: { icon: AlertIcon, className: "bg-danger-soft text-danger" },
  TASK_HIDDEN: { icon: AlertIcon, className: "bg-danger-soft text-danger" },
};

/** Tus avisos, del más reciente al más antiguo. Al abrir la página se marcan como leídos. */
export function NotificationsView() {
  const { refreshUnread } = useArena();
  const [items, setItems] = useState<NotificationItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getNotifications()
      .then(async (result) => {
        if (!active) return;
        setItems(result.items);
        if (result.unreadCount > 0) {
          await markAllNotificationsRead();
          refreshUnread();
        }
      })
      .catch((e) => active && setError(e instanceof ApiError ? e.message : "We couldn’t load your notifications."));
    return () => {
      active = false;
    };
  }, [refreshUnread]);

  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!items) return <PageSpinner label="Loading your notifications…" />;

  if (items.length === 0) {
    return (
      <div className="rounded-lg border-2 border-dashed border-ink/30 px-6 py-14 text-center">
        <p className="font-display text-3xl font-black uppercase">No notifications</p>
        <p className="mx-auto mt-2 max-w-md text-ink-soft">
          We’ll tell you here if someone outbids you, if you win and when your ad goes live.
        </p>
        <ButtonLink href="/race" className="mt-6">
          Go to the Race
        </ButtonLink>
      </div>
    );
  }

  return (
    <section className="overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
      <ul className="divide-y divide-line">
        {items.map((item) => {
          const style = ICONS[item.type] ?? ICONS.AUCTION_LOST;
          const content = (
            <div className="flex gap-4 px-5 py-4 sm:px-7">
              <span className={cn("grid size-10 shrink-0 place-items-center rounded-md", style.className)}>
                <style.icon className="size-5" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-baseline justify-between gap-x-3">
                  <p className="font-semibold">
                    {!item.read && <span className="mr-2 inline-block size-2 rounded-full bg-brand align-middle" />}
                    {item.title}
                  </p>
                  <span className="text-sm text-muted">{formatDateTime(item.createdAt)}</span>
                </div>
                <p className="mt-1 leading-relaxed text-ink-soft">{item.body}</p>
              </div>
            </div>
          );
          return (
            <li key={item.id}>
              {item.link ? (
                <Link href={item.link} className="block transition hover:bg-canvas">
                  {content}
                </Link>
              ) : (
                content
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
