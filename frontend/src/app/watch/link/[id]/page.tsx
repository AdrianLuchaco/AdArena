import type { Metadata } from "next";
import { TaskViewer } from "@/components/earn/TaskViewer";

export const metadata: Metadata = { title: "Bonus link" };

/** Ver un enlace de Créditos extra (la web o el perfil que promociona otro usuario) y ganar sus puntos. */
export default async function VisitPage(props: PageProps<"/watch/link/[id]">) {
  const { id } = await props.params;
  return <TaskViewer id={id} />;
}
