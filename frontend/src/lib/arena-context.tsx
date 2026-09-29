"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useToast } from "@/components/ui/Toaster";
import { ApiError, getHome, getNotifications, getWallet } from "./api";
import { useAuth } from "./auth-context";
import { connectArena } from "./realtime";
import type { ArenaNotification, HomeData, NotificationType } from "./types";

/** Si el WebSocket fallara, al menos se refresca cada minuto. */
const FALLBACK_REFRESH_MS = 60_000;

type NotificationListener = (notification: ArenaNotification) => void;

interface ArenaContextValue {
  data: HomeData | null;
  error: string | null;
  /** Desfase entre el reloj del servidor y el del navegador (ms) */
  clockOffset: number;
  /** Conectados por WebSocket (datos al instante) */
  live: boolean;
  reload: () => Promise<void>;
  /** Para que una página reaccione a los avisos privados (p. ej. refrescar "tu puja") */
  onNotification: (listener: NotificationListener) => () => void;
  /** Avisos sin leer (la campana de la cabecera) */
  unreadCount: number;
  refreshUnread: () => void;
  /** Tus Crown Points libres (null sin sesión). Se muestran en la cabecera. */
  points: number | null;
  /** Actualiza los puntos de la cabecera con un valor que ya conocemos (p. ej. tras pujar). */
  setPoints: (points: number) => void;
  refreshPoints: () => void;
}

/** Cómo se ve cada aviso emergente: color y texto del botón. */
const TOAST_STYLE: Record<NotificationType, { tone: "success" | "danger" | "info"; action: string }> = {
  OUTBID: { tone: "danger", action: "Bid again" },
  AUCTION_WON: { tone: "success", action: "See my ad" },
  CANDIDATE_PROMOTED: { tone: "success", action: "See my ad" },
  AD_APPROVED: { tone: "success", action: "See it live" },
  AUCTION_LOST: { tone: "info", action: "Open the Race" },
  WINNER_REFUNDED: { tone: "info", action: "See my points" },
  AD_REJECTED: { tone: "danger", action: "See my points" },
  TASK_HIDDEN: { tone: "danger", action: "See my promotions" },
};

const ArenaContext = createContext<ArenaContextValue | null>(null);

/**
 * Estado de la Arena compartido por toda la web: se carga una vez y después llega al instante
 * por WebSocket. También muestra los avisos privados ("te han superado", "has ganado"…) como
 * aviso emergente y lleva la cuenta de avisos sin leer.
 */
export function ArenaProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const toast = useToast();
  const [data, setData] = useState<HomeData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [clockOffset, setClockOffset] = useState(0);
  const [live, setLive] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [points, setPointsState] = useState<number | null>(null);
  const listeners = useRef(new Set<NotificationListener>());

  const apply = useCallback((home: HomeData) => {
    setClockOffset(new Date(home.serverTime).getTime() - Date.now());
    setData(home);
    setError(null);
  }, []);

  const reload = useCallback(async () => {
    try {
      apply(await getHome());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn't load the Race.");
    }
  }, [apply]);

  // Carga inicial + refresco de seguridad cada minuto
  useEffect(() => {
    const first = setTimeout(() => void reload(), 0);
    const interval = setInterval(() => void reload(), FALLBACK_REFRESH_MS);
    return () => {
      clearTimeout(first);
      clearInterval(interval);
    };
  }, [reload]);

  // Avisos sin leer: al entrar y cada vez que llega uno nuevo
  const userId = user?.id ?? null;
  const refreshUnread = useCallback(() => {
    if (!userId) return;
    getNotifications()
      .then((result) => setUnreadCount(result.unreadCount))
      .catch(() => {
        // No es crítico: se reintenta con el siguiente aviso
      });
  }, [userId]);
  const refreshPoints = useCallback(() => {
    if (!userId) return;
    getWallet()
      .then((balance) => setPointsState(balance.availablePoints))
      .catch(() => {
        // No es crítico: se reintenta con el siguiente cambio
      });
  }, [userId]);
  const setPoints = useCallback((value: number) => setPointsState(value), []);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (userId) {
        refreshUnread();
        refreshPoints();
      } else {
        setUnreadCount(0);
        setPointsState(null);
      }
    }, 0);
    return () => clearTimeout(timer);
  }, [userId, refreshUnread, refreshPoints]);

  // Tiempo real. Se vuelve a conectar al entrar o salir para que el canal privado siga a la sesión.
  useEffect(() => {
    const client = connectArena({
      authenticated: userId !== null,
      onArena: apply,
      onStatusChange: setLive,
      onNotification: (notification) => {
        const style = TOAST_STYLE[notification.type] ?? { tone: "info", action: "Ver" };
        toast({
          tone: style.tone,
          title: notification.title,
          message: notification.message,
          href: notification.link,
          actionLabel: style.action,
        });
        refreshUnread();
        refreshPoints();
        listeners.current.forEach((listener) => listener(notification));
      },
    });
    return () => {
      void client.deactivate();
    };
  }, [userId, apply, toast, refreshUnread, refreshPoints]);

  const onNotification = useCallback((listener: NotificationListener) => {
    listeners.current.add(listener);
    return () => {
      listeners.current.delete(listener);
    };
  }, []);

  const value = useMemo(
    () => ({ data, error, clockOffset, live, reload, onNotification, unreadCount, refreshUnread, points, setPoints, refreshPoints }),
    [data, error, clockOffset, live, reload, onNotification, unreadCount, refreshUnread, points, setPoints, refreshPoints],
  );
  return <ArenaContext.Provider value={value}>{children}</ArenaContext.Provider>;
}

export function useArena(): ArenaContextValue {
  const context = useContext(ArenaContext);
  if (!context) throw new Error("useArena must be used inside <ArenaProvider>");
  return context;
}
