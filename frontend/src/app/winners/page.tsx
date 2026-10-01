import type { Metadata } from "next";
import { pageJsonLd, pageMetadata, SITE_URL } from "@/lib/site";
import { HistoryClient } from "@/components/history/HistoryClient";
import { JsonLd } from "@/components/seo/JsonLd";
import { getHistoryOnServer } from "@/lib/server-data";
import { formatLongDate } from "@/lib/format";

const TITLE = "Winners: startups that took the homepage";
const DESCRIPTION =
  "Every startup and side project that won the LaunchCrown homepage, day by day, and everyone who competed for it.";

export const metadata: Metadata = pageMetadata({ title: TITLE, description: DESCRIPTION, path: "/winners" });

// La página se vuelve a generar como mucho cada 5 minutos (el historial cambia una vez al día, a medianoche)
export const revalidate = 300;

/**
 * Ganadores. La primera página del historial se lee en el servidor, así llega escrita en el HTML y Google
 * ve los ganadores (antes solo veía el título: la lista se cargaba después, en el navegador). Si el
 * servidor no consigue los datos, la página funciona como siempre: los pide el navegador.
 */
export default async function WinnersPage() {
  const history = await getHistoryOnServer();
  const winners = (history?.items ?? []).flatMap((round) =>
    round.winner ? [{ round, winner: round.winner }] : [],
  );

  const winnersList =
    winners.length > 0
      ? [
          {
            "@type": "ItemList",
            "@id": `${SITE_URL}/winners#list`,
            name: "LaunchCrown homepage winners",
            itemListOrder: "https://schema.org/ItemListOrderDescending",
            numberOfItems: winners.length,
            itemListElement: winners.map(({ round, winner }, index) => ({
              "@type": "ListItem",
              position: index + 1,
              name: `${winner.companyName} (${formatLongDate(round.showcaseDate)})`,
              url: winner.websiteUrl,
            })),
          },
        ]
      : [];

  return (
    <>
      <JsonLd
        data={pageJsonLd({
          path: "/winners",
          name: TITLE,
          description: DESCRIPTION,
          type: "CollectionPage",
          breadcrumb: [["Winners", "/winners"]],
          extra: winnersList,
        })}
      />
      <HistoryClient initial={history} />
    </>
  );
}
