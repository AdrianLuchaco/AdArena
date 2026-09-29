"use client";

import { useEffect, useState } from "react";
import { ApiError, getSettings, updateSettings } from "@/lib/api";
import { pointsToInput, parsePoints } from "@/lib/format";
import type { ArenaSettings } from "@/lib/types";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { TextField } from "../ui/Field";
import { PageSpinner } from "../ui/Spinner";

interface FormState {
  minBid: string;
  minIncrement: string;
  carryOverPercent: string;
  closeTime: string;
  timeZone: string;
  antiSnipingWindowSeconds: string;
  antiSnipingExtensionSeconds: string;
  antiSnipingMaxExtensions: string;
}

function toForm(settings: ArenaSettings): FormState {
  return {
    minBid: pointsToInput(settings.minBidPoints),
    minIncrement: pointsToInput(settings.minIncrementPoints),
    carryOverPercent: String(settings.carryOverPercent),
    closeTime: settings.closeTime,
    timeZone: settings.timeZone,
    antiSnipingWindowSeconds: String(settings.antiSnipingWindowSeconds),
    antiSnipingExtensionSeconds: String(settings.antiSnipingExtensionSeconds),
    antiSnipingMaxExtensions: String(settings.antiSnipingMaxExtensions),
  };
}

/** Reglas de la Arena. Los cambios se aplican desde la SIGUIENTE ronda (la de hoy no cambia). */
export function SettingsView() {
  const [form, setForm] = useState<FormState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    getSettings()
      .then((settings) => setForm(toForm(settings)))
      .catch((e) => setError(e instanceof ApiError ? e.message : "We couldn’t load the settings."));
  }, []);

  if (!form) return error ? <Alert tone="danger">{error}</Alert> : <PageSpinner label="Loading settings…" />;

  const set = (key: keyof FormState) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [key]: event.target.value });
    setSaved(false);
  };

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setError(null);
    setFieldErrors({});
    const minBidPoints = parsePoints(form.minBid);
    const minIncrementPoints = parsePoints(form.minIncrement);
    if (!minBidPoints || !minIncrementPoints) {
      setFieldErrors({ minBidPoints: minBidPoints ? "" : "Enter a number of points.", minIncrementPoints: minIncrementPoints ? "" : "Enter a number of points." });
      return;
    }
    setSaving(true);
    try {
      const updated = await updateSettings({
        minBidPoints,
        minIncrementPoints,
        carryOverPercent: Number(form.carryOverPercent),
        closeTime: form.closeTime,
        timeZone: form.timeZone.trim(),
        antiSnipingWindowSeconds: Number(form.antiSnipingWindowSeconds),
        antiSnipingExtensionSeconds: Number(form.antiSnipingExtensionSeconds),
        antiSnipingMaxExtensions: Number(form.antiSnipingMaxExtensions),
      });
      setForm(toForm(updated));
      setSaved(true);
    } catch (e) {
      if (e instanceof ApiError) {
        setFieldErrors(e.fieldErrors);
        setError(Object.keys(e.fieldErrors).length > 0 ? "Check the highlighted fields." : e.message);
      } else {
        setError("We couldn’t save the settings.");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="max-w-3xl space-y-6 rounded-lg bg-surface p-5 shadow-lift ring-2 ring-ink sm:p-7" noValidate>
      <Alert tone="info" title="They apply from the next round">
        Today’s Race keeps the rules it opened with: nobody changes the rules mid-game. Every change is recorded in
        the audit log.
      </Alert>
      {error && <Alert tone="danger">{error}</Alert>}
      {saved && <Alert tone="success" title="Settings saved">They apply from the next round.</Alert>}

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-2xl font-display font-black uppercase">Bids</legend>
        <TextField label="Minimum first bid (points)" inputMode="numeric" value={form.minBid} onChange={set("minBid")} error={fieldErrors.minBidPoints} />
        <TextField label="Minimum for each extra bid (points)" inputMode="numeric" value={form.minIncrement} onChange={set("minIncrement")} error={fieldErrors.minIncrementPoints} />
        <TextField
          label="Percentage kept by those who don’t win"
          inputMode="numeric"
          value={form.carryOverPercent}
          onChange={set("carryOverPercent")}
          error={fieldErrors.carryOverPercent}
          hint="50 = they keep half their points for the next day."
        />
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-2xl font-display font-black uppercase">Daily close</legend>
        <TextField label="Closing time (HH:mm)" value={form.closeTime} onChange={set("closeTime")} error={fieldErrors.closeTime} />
        <TextField label="Time zone" value={form.timeZone} onChange={set("timeZone")} error={fieldErrors.timeZone} hint="Europe/Madrid" />
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-3 text-2xl font-display font-black uppercase">Last-minute bids</legend>
        <TextField label="If there are (seconds) left…" inputMode="numeric" value={form.antiSnipingWindowSeconds} onChange={set("antiSnipingWindowSeconds")} error={fieldErrors.antiSnipingWindowSeconds} />
        <TextField label="…extend by (seconds)" inputMode="numeric" value={form.antiSnipingExtensionSeconds} onChange={set("antiSnipingExtensionSeconds")} error={fieldErrors.antiSnipingExtensionSeconds} />
        <TextField label="Maximum extensions" inputMode="numeric" value={form.antiSnipingMaxExtensions} onChange={set("antiSnipingMaxExtensions")} error={fieldErrors.antiSnipingMaxExtensions} />
      </fieldset>

      <Button type="submit" size="lg" loading={saving}>
        Save settings
      </Button>
    </form>
  );
}
