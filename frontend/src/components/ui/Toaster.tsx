"use client";

import Link from "next/link";
import { createContext, useCallback, useContext, useMemo, useRef, useState } from "react";
import { cn } from "@/lib/cn";
import { AlertIcon, CheckIcon, ClockIcon, CloseIcon } from "../icons";

type ToastTone = "success" | "danger" | "info";

interface ToastInput {
  tone?: ToastTone;
  title: string;
  message?: string;
  href?: string;
  actionLabel?: string;
}

interface Toast extends ToastInput {
  id: number;
}

const ToastContext = createContext<((toast: ToastInput) => void) | null>(null);

const TONES: Record<ToastTone, { box: string; icon: React.ReactNode }> = {
  success: { box: "bg-ink text-white", icon: <CheckIcon className="size-5 text-emerald-400" /> },
  danger: { box: "bg-danger text-white", icon: <AlertIcon className="size-5" /> },
  info: { box: "bg-ink text-white", icon: <ClockIcon className="size-5 text-gold" /> },
};

/** Avisos emergentes (abajo a la derecha; abajo en el móvil) que desaparecen solos a los 6 s. */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const show = useCallback(
    (toast: ToastInput) => {
      const id = nextId.current++;
      setToasts((current) => [...current.slice(-2), { ...toast, id }]);
      setTimeout(() => dismiss(id), 6000);
    },
    [dismiss],
  );

  const value = useMemo(() => show, [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed inset-x-0 bottom-4 z-[80] flex flex-col items-center gap-2 px-4 sm:inset-x-auto sm:right-6 sm:items-end"
        aria-live="polite"
      >
        {toasts.map((toast) => {
          const tone = TONES[toast.tone ?? "info"];
          return (
            <div
              key={toast.id}
              role="status"
              className={cn(
                "animate-toast-in pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-md px-4 py-3.5 shadow-pop ring-2 ring-ink",
                tone.box,
              )}
            >
              <span className="mt-0.5 shrink-0">{tone.icon}</span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold">{toast.title}</p>
                {toast.message && <p className="mt-0.5 text-sm opacity-85">{toast.message}</p>}
                {toast.href && (
                  <Link
                    href={toast.href}
                    onClick={() => dismiss(toast.id)}
                    className="mt-2 inline-block rounded-sm bg-gold px-3 py-1 text-sm font-semibold text-ink hover:bg-white"
                  >
                    {toast.actionLabel ?? "View"}
                  </Link>
                )}
              </div>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                className="shrink-0 rounded-sm p-1 opacity-70 hover:bg-white/10 hover:opacity-100"
                aria-label="Dismiss"
              >
                <CloseIcon className="size-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error("useToast must be used inside <ToastProvider>");
  return context;
}
