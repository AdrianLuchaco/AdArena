"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, getMyShowcase, refreshMyShowcase } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { MyShowcase } from "@/lib/types";
import { AdView, type AdContent } from "../ad/AdView";
import { WinnerShowcase } from "../ad/WinnerShowcase";
import { Button } from "../ui/Button";
import { RefreshIcon } from "../icons";

const POLL_MS = 3000;
const MAX_POLLS = 12;

/**
 * "Así se verá si ganas": la presentación animada montada con tu web (o tu anuncio clásico si tu
 * web no se ha podido leer). Tras guardar el anuncio, LaunchCrown lee tu web en segundo plano: aquí se
 * espera a que termine.
 *
 * @param enabled  solo si ya tienes anuncio guardado
 * @param savedKey cambia cada vez que guardas (para volver a mirar)
 */
export function ShowcasePreview({ ad, enabled, savedKey }: { ad: AdContent; enabled: boolean; savedKey: number }) {
  const [data, setData] = useState<MyShowcase | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const polls = useRef(0);

  // Para saber cuándo se puede volver a pedir (la espera mínima es de 2 minutos)
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 5000);
    return () => clearInterval(timer);
  }, []);

  const load = useCallback(async () => {
    try {
      const result = await getMyShowcase();
      setData(result);
      setError(null);
      return result;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t load your presentation.");
      return null;
    }
  }, []);

  // Al abrir y tras cada guardado: si la está leyendo, se vuelve a preguntar cada 3 s (máx. ~36 s)
  useEffect(() => {
    if (!enabled) return;
    polls.current = 0;
    let timer: ReturnType<typeof setTimeout>;
    const tick = async () => {
      const result = await load();
      polls.current += 1;
      if (result?.status === "PENDING" && polls.current < MAX_POLLS) {
        timer = setTimeout(() => void tick(), POLL_MS);
      }
    };
    timer = setTimeout(() => void tick(), savedKey > 0 ? 1200 : 0);
    return () => clearTimeout(timer);
  }, [enabled, savedKey, load]);

  async function refresh() {
    setRefreshing(true);
    try {
      setData(await refreshMyShowcase());
      setError(null);
      // Esperar a que termine la lectura
      polls.current = 0;
      const wait = async () => {
        const result = await load();
        polls.current += 1;
        if (result?.status === "PENDING" && polls.current < MAX_POLLS) setTimeout(() => void wait(), POLL_MS);
        else setRefreshing(false);
      };
      setTimeout(() => void wait(), POLL_MS);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t read your website again.");
      setRefreshing(false);
    }
  }

  const classic = <AdView variant="preview" ad={ad} />;

  if (!enabled || !data || data.status === "NONE") {
    return (
      <div className="space-y-3">
        {classic}
        <p className="text-sm text-muted">
          This is how your ad looks if you win. When you save it, we read your website to build your animated presentation.
        </p>
      </div>
    );
  }

  const canRefresh = !data.canRefreshAt || new Date(data.canRefreshAt).getTime() <= now;

  return (
    <div className="space-y-3">
      {data.status === "READY" && data.showcase ? (
        <WinnerShowcase ad={ad} showcase={data.showcase} variant="preview" />
      ) : data.status === "PENDING" ? (
        <div className="skeleton grid aspect-[16/10] place-items-center rounded-lg">
          <p className="rounded-sm bg-surface/90 px-4 py-2 text-sm font-semibold text-ink-soft shadow-card">
            Reading your website to build your presentation…
          </p>
        </div>
      ) : (
        classic
      )}

      <div className="rounded-md bg-surface p-4 text-sm ring-1 ring-line">
        {data.status === "READY" && (
          <p className="text-ink-soft">
            <strong className="text-ink">Your animated presentation.</strong> We built it from your website: your logo,
            colour, headline, section titles and photos. This is how you’ll appear on the homepage if you win.
          </p>
        )}
        {data.status === "PENDING" && <p className="text-ink-soft">We’re reading your website. It takes a few seconds.</p>}
        {data.status === "FAILED" && (
          <p className="text-ink-soft">
            <strong className="text-ink">We couldn’t read your website</strong>
            {data.error ? ` (${data.error})` : ""}. If you win, your classic ad will show, as in the preview above. Check
            that your website is online and try again.
          </p>
        )}
        {error && <p className="mt-2 text-danger">{error}</p>}
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <Button size="sm" variant="secondary" onClick={() => void refresh()} loading={refreshing} disabled={!canRefresh || data.status === "PENDING"}>
            <RefreshIcon className="size-4" /> Read my website again
          </Button>
          {data.fetchedAt && <span className="text-xs text-muted">Read on {formatDateTime(data.fetchedAt)}</span>}
        </div>
        {!canRefresh && <p className="mt-2 text-xs text-muted">You can ask again in a couple of minutes.</p>}
      </div>
    </div>
  );
}
