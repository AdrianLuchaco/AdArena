import type { Metadata } from "next";
import { ModerationView } from "@/components/admin/ModerationView";

export const metadata: Metadata = { title: "Moderation" };

export default function ModerationPage() {
  return <ModerationView />;
}
