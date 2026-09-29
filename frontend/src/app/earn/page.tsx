import type { Metadata } from "next";
import { EarnProjectsView } from "@/components/earn/EarnProjectsView";
import { pageMetadata } from "@/lib/site";

export const metadata: Metadata = {
  ...pageMetadata({
    title: "Earn points by discovering startups",
    description:
      "Discover new startups and side projects and earn Crown Points for every minute you watch. Then bid them to put your own project on the homepage.",
    path: "/earn",
  }),
  // El layout añade "· Earn points · LaunchCrown" al título: aquí va entero
  title: { absolute: "Earn points by discovering startups · LaunchCrown" },
};

export default function EarnPage() {
  return <EarnProjectsView />;
}
