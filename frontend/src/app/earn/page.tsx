import type { Metadata } from "next";
import { EarnProjectsView } from "@/components/earn/EarnProjectsView";
import { EARN_DESCRIPTION, EARN_TITLE } from "@/components/content/PageGuides";
import { pageMetadata } from "@/lib/site";

export const metadata: Metadata = {
  ...pageMetadata({ title: EARN_TITLE, description: EARN_DESCRIPTION, path: "/earn" }),
  // El layout añade "· Earn points · LaunchCrown" al título: aquí va entero
  title: { absolute: "Earn points by discovering startups · LaunchCrown" },
};

export default function EarnPage() {
  return <EarnProjectsView />;
}
