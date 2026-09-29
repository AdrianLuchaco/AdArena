import type { Metadata } from "next";
import { EarnShell } from "@/components/earn/EarnShell";

export const metadata: Metadata = {
  title: { default: "Earn points", template: "%s · Earn points · LaunchCrown" },
};

export default function EarnLayout({ children }: LayoutProps<"/earn">) {
  return <EarnShell>{children}</EarnShell>;
}
