import type { Metadata } from "next";
import Link from "next/link";
import {
  ContentBody,
  ContentHero,
  ContentSection,
  ContentTable,
  DefinitionRows,
  Questions,
  ReadyBox,
  RelatedPages,
  contentLinkClass,
} from "@/components/content/ContentPage";
import { JsonLd } from "@/components/seo/JsonLd";
import { ORGANIZATION_ID, SITE_URL, pageJsonLd, pageMetadata } from "@/lib/site";

const PATH = "/alternatives/product-hunt";
const TITLE = "Free Product Hunt alternative: LaunchCrown vs Product Hunt";
const DESCRIPTION =
  "LaunchCrown vs Product Hunt, compared honestly: how each one picks a winner, what you get, what it costs and when Product Hunt is still the better choice.";
const UPDATED = "2026-10-01";
const UPDATED_LABEL = "1 October 2026";

const baseMetadata = pageMetadata({ title: TITLE, description: DESCRIPTION, path: PATH });

export const metadata: Metadata = {
  ...baseMetadata,
  // Sin "· LaunchCrown" al final: el nombre ya va en el título
  title: { absolute: TITLE },
  openGraph: { ...baseMetadata.openGraph, type: "article", publishedTime: UPDATED, modifiedTime: UPDATED },
};

const ROWS: [string, string, string][] = [
  ["What it is", "A daily race for one homepage.", "A daily leaderboard of new tech products."],
  [
    "How the winner is picked",
    "The highest bid of Crown Points when the clock hits midnight (Madrid time).",
    "Community upvotes and comments during the launch day.",
  ],
  [
    "What the winner gets",
    "The whole LaunchCrown homepage, full screen, for 24 hours, plus 500 points and a permanent spot on the winners page.",
    "A top spot on the day’s leaderboard and the attention of a large tech audience.",
  ],
  ["What it costs", "Free. Points can’t be bought, only earned.", "Free to launch."],
  [
    "What helps you win",
    "Taking part: you earn points by discovering other projects, and keep 50% of a losing bid for the next day.",
    "Preparation and an audience: followers, a network and a team ready to support the launch.",
  ],
  ["How often you can try", "Every day. There’s a new Race daily.", "Usually once per product, and again for major updates."],
  ["Audience size", "Small and new: LaunchCrown started in September 2026.", "Very large and well established."],
];

const COMPARISON_JSON_LD = [
  {
    "@type": "Article",
    "@id": `${SITE_URL}${PATH}#article`,
    headline: TITLE,
    description: DESCRIPTION,
    datePublished: UPDATED,
    dateModified: UPDATED,
    author: { "@id": ORGANIZATION_ID },
    publisher: { "@id": ORGANIZATION_ID },
    image: `${SITE_URL}/opengraph-image`,
    mainEntityOfPage: { "@id": `${SITE_URL}${PATH}#webpage` },
    about: [
      { "@type": "Thing", name: "LaunchCrown", url: SITE_URL },
      { "@type": "Thing", name: "Product Hunt", url: "https://www.producthunt.com" },
    ],
    inLanguage: "en",
  },
];

/** Comparativa honesta con Product Hunt: lo que busca quien escribe "product hunt alternative". */
export default function ProductHuntAlternativePage() {
  return (
    <div className="flex-1">
      <JsonLd
        data={pageJsonLd({
          path: PATH,
          name: TITLE,
          description: DESCRIPTION,
          breadcrumb: [["LaunchCrown vs Product Hunt", PATH]],
          dateModified: UPDATED,
          extra: COMPARISON_JSON_LD,
        })}
      />
      <ContentHero
        eyebrow="Comparison"
        title="LaunchCrown vs Product Hunt"
        intro="Looking for a free Product Hunt alternative? Here is how LaunchCrown compares: how each one picks a winner, what you get, what it costs, and when Product Hunt is still the better choice."
        updated={UPDATED_LABEL}
      />

      <ContentBody>
        <ContentSection
          title="The short answer"
          intro={
            <p>
              Product Hunt is one big launch day in front of a very large tech audience, where the products with the
              most support win. LaunchCrown is a small daily race where anyone can win the whole homepage for 24 hours
              by bidding points they earned by discovering other projects, with no audience or budget needed. They
              work well together: use LaunchCrown to get early visitors and feedback, and Product Hunt for your big
              launch day.
            </p>
          }
        />

        <ContentSection title="Side by side">
          <ContentTable
            caption="LaunchCrown compared with Product Hunt"
            head={["", "LaunchCrown", "Product Hunt"]}
            rows={ROWS.map(([topic, launchCrown, productHunt]) => [topic, launchCrown, productHunt])}
          />
          <p className="text-sm">
            We run LaunchCrown, so we’re not neutral. Product Hunt’s details can change; check{" "}
            <a href="https://www.producthunt.com" className={contentLinkClass} rel="noopener">
              producthunt.com
            </a>{" "}
            for its current rules.
          </p>
        </ContentSection>

        <ContentSection title="When LaunchCrown is the better choice">
          <DefinitionRows
            items={[
              ["No audience yet", "You don’t need followers or a launch team: you need points, and you earn them yourself."],
              ["No budget", "Nothing is for sale. Nobody can pay to beat you."],
              ["You want more than one shot", "There’s a Race every day, and half of a losing bid carries over to the next one."],
              ["You want the whole stage", "The winner isn’t one item in a list: their website is the full-screen homepage for a day."],
            ]}
          />
        </ContentSection>

        <ContentSection title="When Product Hunt is the better choice">
          <DefinitionRows
            items={[
              ["You have an audience", "If you can bring supporters on the day, Product Hunt’s reach is much bigger than ours."],
              ["You want press and investors", "Product Hunt is followed by journalists, investors and early adopters across the tech world."],
              ["A polished, finished product", "A big launch day pays off most when people can sign up and try your product in a minute."],
            ]}
          />
          <p>
            Most products benefit from both, plus a few more places. Our guide on{" "}
            <Link href="/guides/where-to-launch-your-startup" className={contentLinkClass}>
              where to launch your startup
            </Link>{" "}
            lists 13 of them, with a simple 4-week plan.
          </p>
        </ContentSection>

        <ContentSection title="Questions">
          <Questions
            items={[
              [
                "Is LaunchCrown free?",
                "Yes. Signing up, earning points, bidding and winning are free. Crown Points can’t be bought: you earn them by watching other projects’ websites.",
              ],
              [
                "Can I launch on LaunchCrown and Product Hunt?",
                "Yes, and it’s a good idea. Win a day on LaunchCrown to get early visitors and feedback, then use what you learned for your Product Hunt launch.",
              ],
              [
                "What happens if I don’t win the Race?",
                <>
                  You keep 50% of your bid as a head start for the next day’s Race. The full rules are in{" "}
                  <Link href="/how-it-works" className={contentLinkClass}>
                    how LaunchCrown works
                  </Link>
                  .
                </>,
              ],
              [
                "Who can take part?",
                "Startups, side projects, indie hackers and creators. Every winning ad is reviewed before it goes live, and anything illegal or misleading is rejected.",
              ],
            ]}
          />
        </ContentSection>

        <ReadyBox title="Your turn" text="Sign up for free, collect 200 Crown Points and bid for tomorrow’s homepage." />

        <RelatedPages
          links={[
            ["Where to launch your startup", "/guides/where-to-launch-your-startup"],
            ["How LaunchCrown works", "/how-it-works"],
            ["About LaunchCrown", "/about"],
          ]}
        />
      </ContentBody>
    </div>
  );
}
