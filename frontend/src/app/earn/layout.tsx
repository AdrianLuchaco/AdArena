import type { Metadata } from "next";
import { EarnShell } from "@/components/earn/EarnShell";

export const metadata: Metadata = {
  title: { default: "Earn points", template: "%s · Earn points · AdArena" },
  description: "Earn Arena Points by watching the projects in the Arena and visiting bonus links.",
};

export default function EarnLayout({ children }: LayoutProps<"/earn">) {
  return <EarnShell>{children}</EarnShell>;
}
