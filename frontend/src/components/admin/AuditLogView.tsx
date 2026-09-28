"use client";

import { useEffect, useState } from "react";
import { ApiError, getAuditLog } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { AuditEntry } from "@/lib/types";
import { Alert } from "../ui/Alert";
import { PageSpinner } from "../ui/Spinner";

const ACTIONS: Record<string, string> = {
  AD_SLOT_APPROVED: "Approved an ad",
  AD_SLOT_REJECTED: "Rejected an ad",
  TOP_UP_CONFIRMED: "Confirmed a top-up (legacy)",
  TOP_UP_REJECTED: "Discarded a top-up (legacy)",
  TASK_HIDDEN: "Hid a promotion",
  TASK_RESTORED: "Restored a promotion",
  SETTINGS_UPDATED: "Changed the settings",
};

/** Todo lo que se ha hecho desde el panel de administración: quién, qué y cuándo. */
export function AuditLogView() {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getAuditLog(100)
      .then(setEntries)
      .catch((e) => setError(e instanceof ApiError ? e.message : "We couldn’t load the audit log."));
  }, []);

  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!entries) return <PageSpinner label="Loading the audit log…" />;
  if (entries.length === 0) {
    return <p className="rounded-md bg-surface px-5 py-6 text-ink-soft ring-1 ring-line">No actions yet.</p>;
  }

  return (
    <ul className="divide-y divide-line overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
      {entries.map((entry) => (
        <li key={entry.id} className="px-5 py-4">
          <div className="flex flex-wrap items-baseline gap-x-3">
            <span className="font-semibold">{ACTIONS[entry.action] ?? entry.action}</span>
            <span className="text-sm text-muted">{entry.adminName}</span>
            <span className="ml-auto text-sm text-muted">{formatDateTime(entry.createdAt)}</span>
          </div>
          {entry.details && (
            <pre className="mt-2 overflow-x-auto rounded-md bg-canvas p-3 text-xs text-ink-soft">
              {JSON.stringify(JSON.parse(entry.details), null, 2)}
            </pre>
          )}
        </li>
      ))}
    </ul>
  );
}
