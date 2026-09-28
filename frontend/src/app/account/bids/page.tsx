import type { Metadata } from "next";
import { MyBidsView } from "@/components/panel/MyBidsView";

export const metadata: Metadata = { title: "My bids" };

export default function MyBidsPage() {
  return <MyBidsView />;
}
