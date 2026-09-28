import type { Metadata } from "next";
import { ArenaClient } from "@/components/arena/ArenaClient";

export const metadata: Metadata = {
  title: "Today’s Arena",
  description: "Countdown, live standings and bidding for tomorrow’s homepage.",
};

export default function ArenaPage() {
  return <ArenaClient />;
}
