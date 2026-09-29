import type { Metadata } from "next";
import { pageMetadata } from "@/lib/site";
import { PromoteView } from "@/components/promote/PromoteView";

export const metadata: Metadata = pageMetadata({
  title: "Promote your startup for free",
  description:
    "Post your startup, YouTube channel, X profile or website for free. The LaunchCrown community visits it and earns points for watching.",
  path: "/promote",
});

export default function PromotePage() {
  return <PromoteView />;
}
