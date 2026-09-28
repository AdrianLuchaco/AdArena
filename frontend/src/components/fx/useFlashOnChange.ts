"use client";

import { useEffect, useRef } from "react";

/**
 * Hace "destellar" un elemento (anillo naranja que se expande) cada vez que cambia {@code value}.
 * No destella en la primera carga, solo cuando algo cambia en directo (alguien ha pujado).
 * Versión en hook de {@link FlashOnChange} para elementos que no pueden ir dentro de un <div>,
 * como las filas de una tabla.
 */
export function useFlashOnChange<T extends HTMLElement>(value: unknown) {
  const ref = useRef<T>(null);
  const previous = useRef(value);

  useEffect(() => {
    const element = ref.current;
    if (!element || Object.is(previous.current, value)) return;
    previous.current = value;
    element.classList.remove("animate-flash");
    void element.offsetWidth; // reinicia la animación aunque ya estuviera puesta
    element.classList.add("animate-flash");
  }, [value]);

  return ref;
}
