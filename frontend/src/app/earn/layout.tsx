import type { Metadata } from "next";
import { EarnShell } from "@/components/earn/EarnShell";

export const metadata: Metadata = {
  title: { default: "Earn points", template: "%s · Earn points · LaunchCrown" },
  description: "Earn Crown Points by watching the projects in the Race and visiting bonus links.",
};

export default function EarnLayout({ children }: LayoutProps<"/earn">) {
  return <EarnShell>{children}</EarnShell>;
}
