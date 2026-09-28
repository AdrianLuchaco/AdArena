import type { Metadata } from "next";
import { TasksView } from "@/components/earn/TasksView";

export const metadata: Metadata = { title: "Bonus links" };

export default function ExtraCreditsPage() {
  return <TasksView />;
}
