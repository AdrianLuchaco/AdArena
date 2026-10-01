import { jsonLd } from "@/lib/site";

/** Datos estructurados para Google (schema.org). No se ven en la página: solo los leen los buscadores. */
export function JsonLd({ data }: { data: object }) {
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLd(data) }} />;
}
