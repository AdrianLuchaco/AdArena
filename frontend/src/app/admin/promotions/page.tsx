import type { Metadata } from "next";
import { AdminTasksView } from "@/components/admin/AdminTasksView";

export const metadata: Metadata = { title: "Promotions" };

export default function AdminPromotionsPage() {
  return <AdminTasksView />;
}
