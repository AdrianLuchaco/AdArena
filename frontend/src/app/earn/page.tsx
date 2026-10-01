import type { Metadata } from "next";
import { EarnProjectsView } from "@/components/earn/EarnProjectsView";
import { JsonLd } from "@/components/seo/JsonLd";
import { pageJsonLd, pageMetadata } from "@/lib/site";

const TITLE = "Earn points by discovering startups";
const DESCRIPTION =
  "Discover new startups and side projects and earn Crown Points for every 10 seconds you watch. Then bid them to put your own project on the homepage.";

export const metadata: Metadata = {
  ...pageMetadata({ title: TITLE, description: DESCRIPTION, path: "/earn" }),
  // El layout añade "· Earn points · LaunchCrown" al título: aquí va entero
  title: { absolute: "Earn points by discovering startups · LaunchCrown" },
};

export default function EarnPage() {
  return (
    <>
      <JsonLd data={pageJsonLd({ path: "/earn", name: TITLE, description: DESCRIPTION, breadcrumb: [["Earn points", "/earn"]] })} />
      <EarnProjectsView />
    </>
  );
}
