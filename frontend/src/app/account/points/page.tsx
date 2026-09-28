import type { Metadata } from "next";
import { PointsView } from "@/components/panel/PointsView";

export const metadata: Metadata = { title: "My points" };

export default function PointsPage() {
  return <PointsView />;
}
