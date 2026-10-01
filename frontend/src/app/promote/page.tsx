import type { Metadata } from "next";
import { pageJsonLd, pageMetadata } from "@/lib/site";
import { JsonLd } from "@/components/seo/JsonLd";
import { PromoteView } from "@/components/promote/PromoteView";
import { PromoteGuide } from "@/components/content/PageGuides";

const TITLE = "Promote your startup for free";
const DESCRIPTION =
  "Post your startup, YouTube channel, X profile or website for free. The LaunchCrown community visits it and earns points for watching.";

export const metadata: Metadata = pageMetadata({ title: TITLE, description: DESCRIPTION, path: "/promote" });

export default function PromotePage() {
  return (
    <>
      <JsonLd data={pageJsonLd({ path: "/promote", name: TITLE, description: DESCRIPTION, breadcrumb: [["Promote", "/promote"]] })} />
      <PromoteView />
      <PromoteGuide />
    </>
  );
}
