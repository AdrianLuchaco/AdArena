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

const PATH = "/guides/where-to-launch-your-startup";
const TITLE = "Where to launch your startup: 13 free places (2026)";
const DESCRIPTION =
  "The best places to launch a startup or side project for free in 2026: launch platforms, communities and directories, what each one is good for, and a 4-week plan.";
const PUBLISHED = "2026-10-01";
const UPDATED = "2026-10-01";
const UPDATED_LABEL = "1 October 2026";

const baseMetadata = pageMetadata({ title: TITLE, description: DESCRIPTION, path: PATH });

export const metadata: Metadata = {
  ...baseMetadata,
  openGraph: { ...baseMetadata.openGraph, type: "article", publishedTime: PUBLISHED, modifiedTime: UPDATED },
};

type Platform = { name: string; url: string; what: string; bestFor: string; outcome: string };

/** Los sitios, agrupados por tipo. Revisar de vez en cuando: las webs cambian sus reglas. */
const TIERS: { title: string; intro: string; platforms: Platform[] }[] = [
  {
    title: "Big launch days",
    intro: "One day, a lot of attention, then it fades. Prepare well and bring your own people on the day.",
    platforms: [
      {
        name: "Product Hunt",
        url: "https://www.producthunt.com",
        what: "Daily leaderboard of new tech products, voted by the community.",
        bestFor: "Polished products with an audience ready to support the launch.",
        outcome: "A strong day of traffic if you rank high; little if you don’t.",
      },
      {
        name: "Hacker News (Show HN)",
        url: "https://news.ycombinator.com/showhn.html",
        what: "“Show HN” posts on Hacker News, for things people can try.",
        bestFor: "Developer tools and technical products you can demo right away.",
        outcome: "Large, unpredictable spikes and very direct feedback.",
      },
      {
        name: "Reddit",
        url: "https://www.reddit.com/r/SideProject/",
        what: "Communities such as r/SideProject and niche subreddits for your audience.",
        bestFor: "Telling the story of what you built, in the right niche.",
        outcome: "Good feedback and early users if you follow each subreddit’s rules.",
      },
      {
        name: "Indie Hackers",
        url: "https://www.indiehackers.com",
        what: "Community of bootstrapped founders sharing what they build.",
        bestFor: "Build-in-public posts, milestones and lessons learned.",
        outcome: "Slower traffic, but founders who reply and remember you.",
      },
    ],
  },
  {
    title: "Launch platforms and directories",
    intro: "Smaller audiences, but easier to stand out. Several of them give you a lasting listing.",
    platforms: [
      {
        name: "LaunchCrown",
        url: SITE_URL,
        what: "Daily race for one homepage: the highest bid of earned points wins it for 24 hours.",
        bestFor: "Startups and side projects with no budget. Free, and you can try again every day.",
        outcome: "If you win, a full-screen homepage for 24 hours and a permanent spot on the winners page.",
      },
      {
        name: "BetaList",
        url: "https://betalist.com",
        what: "Directory of early-stage startups, reviewed before they’re published.",
        bestFor: "Pre-launch and beta products looking for early adopters.",
        outcome: "Sign-ups from people who like trying new things.",
      },
      {
        name: "Uneed",
        url: "https://www.uneed.best",
        what: "Daily launch platform and directory of tools.",
        bestFor: "SaaS and tools for makers.",
        outcome: "A launch day plus a listing that stays.",
      },
      {
        name: "Peerlist Launchpad",
        url: "https://peerlist.io/launchpad",
        what: "Weekly launches inside Peerlist, a network for builders.",
        bestFor: "Products made by developers and designers.",
        outcome: "Feedback and visibility among other builders.",
      },
      {
        name: "Microlaunch",
        url: "https://microlaunch.net",
        what: "Launch platform where products stay featured for longer than a day.",
        bestFor: "Small products that need more than 24 hours to get noticed.",
        outcome: "Steadier, smaller traffic over several weeks.",
      },
      {
        name: "DevHunt",
        url: "https://devhunt.org",
        what: "Launch platform focused on developer tools.",
        bestFor: "APIs, libraries, CLIs and other tools for developers.",
        outcome: "A targeted audience if your users are developers.",
      },
    ],
  },
  {
    title: "Software directories",
    intro: "Not a launch day: a listing people find months later when they search for a solution.",
    platforms: [
      {
        name: "AlternativeTo",
        url: "https://alternativeto.net",
        what: "Crowd-sourced list of alternatives to well-known software.",
        bestFor: "Products that replace a known tool (“an alternative to X”).",
        outcome: "Slow but steady visits from people actively looking to switch.",
      },
      {
        name: "SaaSHub",
        url: "https://www.saashub.com",
        what: "Software directory with alternatives and comparisons.",
        bestFor: "SaaS products in an established category.",
        outcome: "Long-term listing that can show up in searches.",
      },
      {
        name: "G2",
        url: "https://www.g2.com",
        what: "Business software reviews.",
        bestFor: "B2B products once you have customers who can leave reviews.",
        outcome: "Trust for buyers comparing tools; needs reviews to matter.",
      },
    ],
  },
];

const PLATFORMS = TIERS.flatMap((tier) => tier.platforms);

/** Para Google: es un artículo (con fecha y autor) que contiene una lista de sitios. */
const ARTICLE_JSON_LD = [
  {
    "@type": "Article",
    "@id": `${SITE_URL}${PATH}#article`,
    headline: TITLE,
    description: DESCRIPTION,
    datePublished: PUBLISHED,
    dateModified: UPDATED,
    author: { "@id": ORGANIZATION_ID },
    publisher: { "@id": ORGANIZATION_ID },
    image: `${SITE_URL}/opengraph-image`,
    mainEntityOfPage: { "@id": `${SITE_URL}${PATH}#webpage` },
    inLanguage: "en",
  },
  {
    "@type": "ItemList",
    "@id": `${SITE_URL}${PATH}#places`,
    name: "Places to launch a startup for free",
    numberOfItems: PLATFORMS.length,
    itemListElement: PLATFORMS.map((platform, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: platform.name,
      url: platform.url,
    })),
  },
];

/** Guía "dónde lanzar tu startup": lo que la gente busca de verdad (listas y comparativas), contado con honestidad. */
export default function LaunchGuidePage() {
  return (
    <div className="flex-1">
      <JsonLd
        data={pageJsonLd({
          path: PATH,
          name: TITLE,
          description: DESCRIPTION,
          breadcrumb: [["Where to launch your startup", PATH]],
          dateModified: UPDATED,
          extra: ARTICLE_JSON_LD,
        })}
      />
      <ContentHero
        eyebrow="Guide"
        title="Where to launch your startup in 2026"
        intro="13 places to launch a startup or side project for free: big launch days, smaller launch platforms and directories that keep sending visitors for months. What each one is good for, and how to use them together."
        updated={UPDATED_LABEL}
      />

      <ContentBody>
        <ContentSection
          title="The short answer"
          intro={
            <p>
              Don’t bet everything on one launch day. The founders who get their first users usually launch in several
              places over a few weeks: first in small communities where they can get feedback, then on one big launch
              day (Product Hunt or Hacker News), and finally in directories that keep sending visitors long after the
              launch. All the places in this guide are free to submit to. Some also sell paid extras, like a faster
              review or a featured spot; those change often, so check each site before you pay for anything.
            </p>
          }
        />

        <ContentSection title="How to choose where to launch">
          <DefinitionRows
            items={[
              ["Who your users are", "Developers live on Hacker News, DevHunt and Peerlist. Founders read Indie Hackers. General consumers are easier to reach in niche subreddits than on launch platforms."],
              ["How ready you are", "Pre-launch or beta? BetaList and small communities. Polished and easy to try? A big launch day."],
              ["How much time you have", "Big launch days need preparation and a full day of answering comments. Directories take ten minutes and keep working."],
              ["What you want", "Feedback, first users, links back to your site or long-term discovery: each place is good at one or two of these, not all."],
            ]}
          />
        </ContentSection>

        {TIERS.map((tier) => (
          <ContentSection key={tier.title} title={tier.title} intro={<p>{tier.intro}</p>}>
            <ContentTable
              caption={`${tier.title}: places to launch a startup`}
              head={["Place", "What it is", "Best for", "What to expect"]}
              rows={tier.platforms.map((platform) => [
                <a key="name" href={platform.url} className={contentLinkClass} rel="noopener">
                  {platform.name}
                </a>,
                platform.what,
                platform.bestFor,
                platform.outcome,
              ])}
            />
          </ContentSection>
        ))}

        <p className="rounded-md bg-surface p-5 text-sm leading-relaxed text-ink-soft ring-1 ring-line">
          <strong className="text-ink">Disclosure:</strong> LaunchCrown is our own product, so we’re not neutral about
          it. We’ve tried to describe it with the same honesty as everything else here: it’s new and its community is
          still small, and it works best alongside the other places on this list, not instead of them.
        </p>

        <ContentSection
          title="A 4-week launch plan"
          intro={<p>A simple order that works for most small products. Adjust it to your audience.</p>}
        >
          <DefinitionRows
            items={[
              ["Week 1: feedback", "Share it in one or two communities where your users are (a niche subreddit, Indie Hackers). Ask for feedback, not upvotes, and fix what people point out."],
              ["Week 2: early adopters", "Submit to BetaList, Peerlist or DevHunt, depending on your audience. Start earning Crown Points on LaunchCrown and bid for the homepage."],
              ["Week 3: the big day", "Launch on Product Hunt or post a Show HN. Be there all day to answer every comment. Tell your own network the same morning."],
              ["Week 4: stay findable", "List it on AlternativeTo, SaaSHub and, for B2B, G2. Write a short “what we learned from launching” post: it brings a second wave."],
            ]}
          />
        </ContentSection>

        <ContentSection title="Mistakes to avoid">
          <ul className="ml-5 list-disc space-y-2">
            <li>Launching everywhere on the same day: you can’t answer everyone, and you learn nothing between launches.</li>
            <li>Asking for upvotes in exchange for something. Most platforms forbid it and can remove your launch.</li>
            <li>Posting the same text everywhere. Each community expects a different tone and level of detail.</li>
            <li>Paying for featured spots before you know which audience actually converts for you.</li>
            <li>Forgetting the page people land on: a clear headline, a screenshot and one obvious button matter more than the platform.</li>
          </ul>
        </ContentSection>

        <ContentSection title="Questions">
          <Questions
            items={[
              [
                "What’s the best free place to launch a startup?",
                "There isn’t one best place: it depends on who your users are. For developer tools, Hacker News and DevHunt. For indie products, Indie Hackers and Reddit. For a big one-day push, Product Hunt. Combining several over a few weeks works better than any single one.",
              ],
              [
                "Is Product Hunt still worth it in 2026?",
                <>
                  It can be, if you prepare and already have people who will support your launch. Without that, a launch
                  can go unnoticed. We compare it in detail in{" "}
                  <Link href="/alternatives/product-hunt" className={contentLinkClass}>
                    LaunchCrown vs Product Hunt
                  </Link>
                  .
                </>,
              ],
              [
                "How can I promote my startup for free?",
                <>
                  Launch in communities and directories like the ones above, write about what you’re building, and take
                  part in places that reward participation instead of money. On{" "}
                  <Link href="/how-it-works" className={contentLinkClass}>
                    LaunchCrown
                  </Link>
                  , for example, you earn points by discovering other projects and bid them to win the homepage for a day.
                </>,
              ],
              [
                "Should I launch before my product is finished?",
                "Launch early in small communities to get feedback, and save the big launch day for when people can sign up and try it in a minute.",
              ],
            ]}
          />
        </ContentSection>

        <ReadyBox title="Try it today" text="Sign up for free, collect 200 Crown Points and bid for tomorrow’s LaunchCrown homepage." />

        <RelatedPages
          links={[
            ["LaunchCrown vs Product Hunt", "/alternatives/product-hunt"],
            ["How LaunchCrown works", "/how-it-works"],
            ["Promote your link for free", "/promote"],
          ]}
        />
      </ContentBody>
    </div>
  );
}
