"use client";

import { useEffect, useState } from "react";

/** Milisegundos que faltan hasta {@code endsAt}, actualizado cada segundo. */
export function useCountdown(endsAt: string | null | undefined, clockOffset = 0): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, []);

  if (!endsAt) return 0;
  return Math.max(0, new Date(endsAt).getTime() - (now + clockOffset));
}
