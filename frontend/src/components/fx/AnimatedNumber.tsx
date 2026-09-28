"use client";

import { useEffect, useRef, useState } from "react";

const DURATION_MS = 700;

/**
 * Un número que "sube" hasta su nuevo valor en lugar de cambiar de golpe (por ejemplo, el total
 * de un proyecto cuando alguien puja). Escribe directamente en el DOM: no provoca re-renderizados.
 */
export function AnimatedNumber({
  value,
  format,
  className,
}: {
  value: number;
  format: (value: number) => string;
  className?: string;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  const shown = useRef(value);
  // React pinta el valor inicial una vez; a partir de ahí el efecto actualiza el texto directamente
  const [initial] = useState(value);

  useEffect(() => {
    const element = ref.current;
    const from = shown.current;
    if (!element || from === value) return;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduceMotion) {
      shown.current = value;
      element.textContent = format(value);
      return;
    }
    const start = performance.now();
    let frame = 0;
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / DURATION_MS);
      const eased = 1 - Math.pow(1 - progress, 3);
      const current = Math.round(from + (value - from) * eased);
      shown.current = current;
      element.textContent = format(current);
      if (progress < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [value, format]);

  return (
    <span ref={ref} className={className}>
      {format(initial)}
    </span>
  );
}
