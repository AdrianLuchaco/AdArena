import type { Metadata } from "next";
import { ProjectViewer } from "@/components/earn/ProjectViewer";

export const metadata: Metadata = { title: "Watch and earn" };

/** Ver la web de un proyecto de la Arena y ganar puntos mientras la miras. */
export default async function ProjectPage(props: PageProps<"/watch/[id]">) {
  const { id } = await props.params;
  return <ProjectViewer id={id} />;
}
