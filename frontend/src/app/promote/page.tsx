import type { Metadata } from "next";
import { PromoteView } from "@/components/promote/PromoteView";

export const metadata: Metadata = {
  title: "Promote your link for free",
  description: "Post your YouTube, X, Instagram or website for free so the LaunchCrown community visits it.",
};

export default function PromotePage() {
  return <PromoteView />;
}
