import type { Metadata } from "next";
import { pageMetadata } from "@/lib/site";
import { TasksView } from "@/components/earn/TasksView";

export const metadata: Metadata = {
  ...pageMetadata({
    title: "Bonus links",
    description:
      "Visit YouTube channels, X profiles and websites of other makers for 10 seconds and earn Crown Points. Free, no follows or likes required.",
    path: "/earn/links",
  }),
  // El layout añade "· Earn points · LaunchCrown"
  title: "Bonus links",
};

export default function ExtraCreditsPage() {
  return <TasksView />;
}
