import type { Metadata } from "next";
import { HistoryClient } from "@/components/history/HistoryClient";

export const metadata: Metadata = {
  title: "Winners",
  description: "Every project that won the AdArena homepage, and everyone who competed each day.",
};

export default function WinnersPage() {
  return <HistoryClient />;
}
