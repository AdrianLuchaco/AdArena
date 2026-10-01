import type { Metadata } from "next";
import Link from "next/link";
import {
  ContentBody,
  ContentHero,
  ContentSection,
  DefinitionRows,
  ReadyBox,
  RelatedPages,
  contentLinkClass,
} from "@/components/content/ContentPage";
import { JsonLd } from "@/components/seo/JsonLd";
import { CONTACT_EMAIL } from "@/lib/legal";
import { pageJsonLd, pageMetadata } from "@/lib/site";

const TITLE = "About LaunchCrown";
const DESCRIPTION =
  "What LaunchCrown is, why it exists and how it works: a free daily race where startups and side projects win the homepage with points they earn, never buy.";
const UPDATED = "2026-10-01";

export const metadata: Metadata = {
  ...pageMetadata({ title: TITLE, description: DESCRIPTION, path: "/about" }),
  // Sin "· LaunchCrown" al final: el nombre ya va en el título
  title: { absolute: "About LaunchCrown: the free daily race for startups" },
};

/** Quiénes somos y qué es LaunchCrown: la respuesta corta a "¿qué es esto?" para personas, Google y las IA. */
export default function AboutPage() {
  return (
    <div className="flex-1">
      <JsonLd
        data={pageJsonLd({
          path: "/about",
          name: TITLE,
          description: DESCRIPTION,
          type: "AboutPage",
          breadcrumb: [["About", "/about"]],
          dateModified: UPDATED,
        })}
      />
      <ContentHero
        eyebrow="About"
        title="One homepage, one winner, every day"
        intro="LaunchCrown is a free daily race for one homepage. Startups, side projects and creators bid Crown Points, and the highest bid at midnight takes over the whole LaunchCrown homepage for 24 hours."
      />

      <ContentBody>
        <ContentSection
          title="What is LaunchCrown?"
          intro={
            <p>
              LaunchCrown is a free way to promote a startup or side project. Every day there is one Race: projects bid
              Crown Points, and when the clock hits midnight (Madrid time) the highest total bid wins. The winner’s
              website becomes the full-screen homepage of LaunchCrown for the next 24 hours. Crown Points can’t be
              bought. You earn them by discovering other people’s projects, so the only way to the top is to take part.
            </p>
          }
        />

        <ContentSection
          title="Why it exists"
          intro={
            <p>
              Launching something new is hard when you don’t have an audience or an ad budget. Launch days on big
              platforms reward people who already have followers, and paid ads reward people who already have money.
              LaunchCrown tries a different trade: you give attention to other makers, and you get attention back.
            </p>
          }
        >
          <DefinitionRows
            items={[
              ["Fair by design", "Points are earned, never sold. Nobody can pay to win the homepage."],
              ["Every day is a new chance", "There is a new Race every day. If you don’t win, you keep 50% of your bid for the next one."],
              ["Real attention", "The winner gets the whole homepage, full screen, for 24 hours, built automatically from their own website."],
              ["Discovery for everyone", "Visitors find new startups and indie projects every day and earn points while they do."],
            ]}
          />
        </ContentSection>

        <ContentSection title="How it works, in short">
          <ol className="grid gap-px overflow-hidden rounded-lg bg-ink ring-2 ring-ink md:grid-cols-3">
            {[
              ["Earn points", "Sign up and get 200 Crown Points. Earn more by watching other projects’ websites: 10 points every 10 seconds, plus a bonus at 60 seconds."],
              ["Bid", "Set up your ad and bid at least 100 points. Your bids for the day add up, and a bid in the last 2 minutes extends the clock."],
              ["Win the homepage", "Highest bid at midnight wins 24 hours on the homepage, plus 500 points to bid again."],
            ].map(([title, text], index) => (
              <li key={title} className="flex flex-col bg-surface p-6">
                <span className="font-display text-6xl font-black leading-none text-brand" aria-hidden="true">
                  {index + 1}
                </span>
                <h3 className="mt-3 text-2xl">{title}</h3>
                <p className="mt-2 leading-relaxed text-ink-soft">{text}</p>
              </li>
            ))}
          </ol>
          <p>
            The full rules, with examples, are in{" "}
            <Link href="/how-it-works" className={contentLinkClass}>
              how LaunchCrown works
            </Link>
            .
          </p>
        </ContentSection>

        <ContentSection title="Who runs it">
          <DefinitionRows
            items={[
              ["Independent", "LaunchCrown is a small, independent project run from Spain. It isn’t part of any big platform."],
              ["Started", "The first Race closed on 28 September 2026. Every winner since then is listed on the winners page."],
              [
                "Reviewed by people",
                "Every winning ad is reviewed before it goes live. Anything illegal, misleading or harmful is rejected, and the points are returned.",
              ],
              [
                "Contact",
                <a key="contact" href={`mailto:${CONTACT_EMAIL}`} className={contentLinkClass}>
                  {CONTACT_EMAIL}
                </a>,
              ],
            ]}
          />
          <p>
            Past winners are on the{" "}
            <Link href="/winners" className={contentLinkClass}>
              winners page
            </Link>
            , and the rules on points, ads and accounts are in the{" "}
            <Link href="/legal/terms" className={contentLinkClass}>
              terms and conditions
            </Link>
            .
          </p>
        </ContentSection>

        <ReadyBox text="Sign up, collect your 200 points and bid for tomorrow’s homepage. It’s free." />

        <RelatedPages
          links={[
            ["Where to launch your startup", "/guides/where-to-launch-your-startup"],
            ["LaunchCrown vs Product Hunt", "/alternatives/product-hunt"],
            ["How LaunchCrown works", "/how-it-works"],
          ]}
        />
      </ContentBody>
    </div>
  );
}
