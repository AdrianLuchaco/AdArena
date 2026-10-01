import type { Metadata } from "next";
import { EarnShell } from "@/components/earn/EarnShell";
import { EarnGuide } from "@/components/content/PageGuides";

export const metadata: Metadata = {
  title: { default: "Earn points", template: "%s · Earn points · LaunchCrown" },
};

export default function EarnLayout({ children }: LayoutProps<"/earn">) {
  return <EarnShell guide={<EarnGuide />}>{children}</EarnShell>;
}
