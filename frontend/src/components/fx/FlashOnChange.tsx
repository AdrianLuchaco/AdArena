"use client";

import { useEffect, useRef } from "react";
import { cn } from "@/lib/cn";

/**
 * Envuelve un elemento y lo hace "destellar" (anillo naranja que se expande) cada vez que
 * cambia {@code value}. No destella en la primera carga, solo cuando algo cambia en directo.
 */
export function FlashOnChange({
  value,
  children,
  className,
}: {
  value: unknown;
  children: React.ReactNode;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const previous = useRef(value);

  useEffect(() => {
    const element = ref.current;
    if (!element || Object.is(previous.current, value)) return;
    previous.current = value;
    element.classList.remove("animate-flash");
    void element.offsetWidth; // reinicia la animación aunque ya estuviera puesta
    element.classList.add("animate-flash");
  }, [value]);

  return (
    <div ref={ref} className={cn(className)}>
      {children}
    </div>
  );
}
