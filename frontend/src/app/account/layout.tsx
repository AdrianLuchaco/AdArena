import type { Metadata } from "next";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { PanelShell } from "@/components/panel/PanelShell";

export const metadata: Metadata = { title: { default: "Account", template: "%s · Account · LaunchCrown" } };

export default function PanelLayout({ children }: LayoutProps<"/account">) {
  return (
    <RequireAuth>
      <PanelShell>{children}</PanelShell>
    </RequireAuth>
  );
}
