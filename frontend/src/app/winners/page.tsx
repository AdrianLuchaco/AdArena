import type { Metadata } from "next";
import { pageMetadata } from "@/lib/site";
import { HistoryClient } from "@/components/history/HistoryClient";

export const metadata: Metadata = pageMetadata({
  title: "Winners: startups that took the homepage",
  description:
    "Every startup and side project that won the LaunchCrown homepage, day by day, and everyone who competed for it.",
  path: "/winners",
});

export default function WinnersPage() {
  return <HistoryClient />;
}
