import Link from "next/link";
import { ContentSection, DefinitionRows, RelatedPages, contentLinkClass } from "./ContentPage";

/**
 * Texto fijo al pie de Promote y Earn points. Va en el HTML del servidor (Google lo lee sin
 * ejecutar JavaScript) y se ve igual con o sin sesión. Las cifras salen de application.yml:
 * si cambian allí, cambiarlas aquí y en "How it works".
 */
function GuideBand({ children }: { children: React.ReactNode }) {
  return (
    <div className="border-t-2 border-ink">
      <div className="mx-auto w-full max-w-4xl space-y-14 px-4 py-12 sm:px-6 lg:py-16">{children}</div>
    </div>
  );
}

export function PromoteGuide() {
  return (
    <GuideBand>
      <ContentSection
        title="Two free ways to promote your startup"
        intro="LaunchCrown never charges money. You can put your startup, side project or channel in front of the community in two ways, and you can use both at once."
      >
        <DefinitionRows
          items={[
            [
              "Promote a link",
              <>
                Post up to 5 links (your website, a YouTube channel, an X, Instagram or TikTok profile, a GitHub repo).
                They go live straight away in everyone’s Bonus links. It costs no points.
              </>,
            ],
            [
              "Win the homepage",
              <>
                Bid Crown Points in the{" "}
                <Link href="/race" className={contentLinkClass}>
                  daily Race
                </Link>
                . The highest total bid at midnight (Madrid time) takes over the whole LaunchCrown homepage, full
                screen, for 24 hours.
              </>,
            ],
          ]}
        />
      </ContentSection>

      <ContentSection
        title="How a promoted link works"
        intro="Promoted links appear in Bonus links, the second way to earn points on LaunchCrown. People open your link, spend at least 10 seconds on it and earn points for doing so."
      >
        <DefinitionRows
          items={[
            [
              "Who sees it",
              "Every logged-in member who opens Bonus links. Each person can complete up to 10 links a day.",
            ],
            [
              "What they earn",
              "20 Crown Points per link, after 10 seconds on it. Featured links are shown first and give 100.",
            ],
            [
              "What you get",
              "Real visits from founders, makers and creators. This page shows how many people watched each link today and in total.",
            ],
            ["How many", "Up to 5 active links per account. You can pause, resume or delete them at any time."],
            [
              "Rules",
              "Nothing illegal, misleading, sexual, violent, gambling-related or with malware, and no asking for likes or follows in exchange for points. A link reported by 3 people is hidden for review.",
            ],
          ]}
        />
      </ContentSection>

      <ContentSection title="What to promote, and how to get more from it">
        <p>
          Anything with a public https address works: a startup landing page, a product demo, a newsletter signup, a
          YouTube channel, a GitHub repository or your X profile. LaunchCrown detects the network automatically.
        </p>
        <p>
          Visitors decide in a few seconds whether to stay. Send them to a page that says what you do in one sentence,
          shows the product above the fold and has one clear next step (sign up, subscribe, follow). A link that works
          well on a phone keeps more of them.
        </p>
        <p>
          Want more than a link? Watching other people’s projects earns the Crown Points you need for the Race. Read{" "}
          <Link href="/how-it-works" className={contentLinkClass}>
            how LaunchCrown works
          </Link>{" "}
          or our guide on{" "}
          <Link href="/guides/where-to-launch-your-startup" className={contentLinkClass}>
            where to launch your startup
          </Link>{" "}
          for other free places to get your first users.
        </p>
      </ContentSection>

      <RelatedPages
        links={[
          ["How it works", "/how-it-works"],
          ["Earn points", "/earn"],
          ["LaunchCrown vs Product Hunt", "/alternatives/product-hunt"],
        ]}
      />
    </GuideBand>
  );
}

export function EarnGuide() {
  return (
    <GuideBand>
      <ContentSection
        title="How to earn Crown Points"
        intro="Crown Points are LaunchCrown’s currency for the daily Race. They can’t be bought: you earn them by discovering other people’s projects."
      >
        <DefinitionRows
          items={[
            ["Sign up", "200 points, once, when you create your account."],
            [
              "Race websites",
              "10 points every 10 seconds you watch a project bidding in today’s Race, plus 40 more at 60 seconds. Up to 100 points per website per day.",
            ],
            [
              "Bonus links",
              "20 points per link the community promotes, after 10 seconds on it. Featured links give 100. Up to 10 links a day.",
            ],
            ["Win the Race", "500 points when your ad goes live on the homepage, so you can bid again."],
          ]}
        />
      </ContentSection>

      <ContentSection title="What counts as watching">
        <p>
          Points only count while the website is actually on your screen. Switching tabs or apps pauses the count, and
          the server keeps one clock per person: ten tabs at once earn the same as one. It never pays for more time than
          has really passed.
        </p>
        <p>
          Nothing here depends on ads: you never earn points for seeing or clicking an ad. The full details are in{" "}
          <Link href="/how-it-works#watching" className={contentLinkClass}>
            how the count works
          </Link>
          .
        </p>
      </ContentSection>

      <ContentSection title="What to do with your points">
        <p>
          Bid them in the{" "}
          <Link href="/race" className={contentLinkClass}>
            daily Race
          </Link>{" "}
          for tomorrow’s homepage. Bids start at 100 points and add up during the day. If you don’t win, you keep 50% of
          your bid for the next day’s Race. The winner takes over the whole LaunchCrown homepage for 24 hours.
        </p>
        <p>
          Want people to find your own project too?{" "}
          <Link href="/promote" className={contentLinkClass}>
            Promote your link for free
          </Link>{" "}
          and it shows up in everyone’s Bonus links.
        </p>
      </ContentSection>

      <RelatedPages
        links={[
          ["How it works", "/how-it-works"],
          ["Today’s Race", "/race"],
          ["Past winners", "/winners"],
        ]}
      />
    </GuideBand>
  );
}
