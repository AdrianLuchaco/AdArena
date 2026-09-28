import type { Metadata } from "next";
import { AdminShell } from "@/components/admin/AdminShell";
import { RequireAuth } from "@/components/auth/RequireAuth";

export const metadata: Metadata = {
  title: { default: "Admin", template: "%s · Admin · AdArena" },
  robots: { index: false, follow: false },
};

export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <RequireAuth>
      <AdminShell>{children}</AdminShell>
    </RequireAuth>
  );
}
