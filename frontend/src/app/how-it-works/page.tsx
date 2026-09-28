import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRightIcon } from "@/components/icons";

export const metadata: Metadata = {
  title: "How it works",
  description:
    "The full AdArena guide: how to bid for the homepage, what happens if you win, how to earn Arena Points by watching websites, bonus links and promoting your link for free.",
};

const SECTIONS = [
  { id: "first-day", label: "Your first day" },
  { id: "arena", label: "The Arena: bidding" },
  { id: "winning", label: "If you win" },
  { id: "points", label: "Arena Points" },
  { id: "watching", label: "Earning by watching" },
  { id: "bonus-links", label: "Bonus links" },
  { id: "promote", label: "Promote your link" },
  { id: "faq", label: "Questions" },
];

/** La guía completa: lo principal (pujar y ganar) primero; después, cómo se consiguen los puntos. */
export default function HowItWorksPage() {
  return (
    <div className="flex-1">
      <header className="track relative isolate border-b-2 border-ink text-white">
        <div className="mx-auto max-w-6xl px-4 py-14 sm:px-6 sm:py-20">
          <h1 className="max-w-[16ch] text-6xl text-balance sm:text-8xl">How AdArena works</h1>
          <p className="mt-5 max-w-2xl text-lg leading-relaxed text-white/85 sm:text-xl">
            Every day, projects bid Arena Points for the homepage. Points can’t be bought: you earn them by watching
            other people’s websites. The highest bid at midnight goes full screen for 24 hours.
          </p>
          <nav className="-mx-4 mt-8 flex gap-2 overflow-x-auto px-4 pb-1 lg:hidden" aria-label="Sections">
            {SECTIONS.map((section) => (
              <a
                key={section.id}
                href={`#${section.id}`}
                className="shrink-0 rounded-md bg-ink/30 px-3 py-2 text-sm font-semibold ring-1 ring-white/20 hover:bg-ink/50"
              >
                {section.label}
              </a>
            ))}
          </nav>
        </div>
      </header>

      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-12 sm:px-6 lg:grid-cols-[13rem_minmax(0,1fr)] lg:py-16">
        <aside className="hidden lg:block">
          <nav className="sticky top-24 border-l-2 border-ink" aria-label="Guide sections">
            {SECTIONS.map((section) => (
              <a
                key={section.id}
                href={`#${section.id}`}
                className="block py-1.5 pl-4 text-sm font-medium text-ink-soft transition hover:bg-ink/5 hover:text-ink"
              >
                {section.label}
              </a>
            ))}
          </nav>
        </aside>

        <div className="min-w-0 space-y-16">
          <FirstDay />
          <TheArena />
          <IfYouWin />
          <Points />
          <Watching />
          <BonusLinks />
          <Promote />
          <Faq />

          <section className="rounded-lg bg-gold p-8 shadow-lift ring-2 ring-ink sm:p-10">
            <p className="font-display text-5xl font-black uppercase leading-none">Ready?</p>
            <p className="mt-3 max-w-xl text-ink/80">Sign up, collect your 200 points and bid for tomorrow’s homepage.</p>
            <div className="mt-6 flex flex-wrap gap-3">
              <Link
                href="/signup"
                className="inline-flex h-12 items-center gap-2 rounded-md bg-ink px-6 font-semibold text-white hover:bg-brand"
              >
                Sign up free <ArrowRightIcon className="size-5" />
              </Link>
              <Link
                href="/arena"
                className="inline-flex h-12 items-center rounded-md px-6 font-semibold ring-2 ring-ink hover:bg-white/40"
              >
                See today’s Arena
              </Link>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ apartados

function Section({ id, title, intro, children }: { id: string; title: string; intro: string; children: React.ReactNode }) {
  return (
    <section id={id} className="scroll-mt-24">
      <h2 className="text-5xl sm:text-6xl">{title}</h2>
      <p className="mt-4 max-w-3xl text-lg leading-relaxed text-ink-soft">{intro}</p>
      <div className="mt-6 space-y-5 leading-relaxed text-ink-soft">{children}</div>
    </section>
  );
}

/** Reglas como lista de definiciones: el nombre a la izquierda y la explicación a la derecha. */
function Rules({ items }: { items: [string, React.ReactNode][] }) {
  return (
    <dl className="divide-y divide-line border-y-2 border-ink">
      {items.map(([term, text]) => (
        <div key={term} className="grid gap-1 py-4 sm:grid-cols-[13rem_1fr] sm:gap-6">
          <dt className="font-bold text-ink">{term}</dt>
          <dd>{text}</dd>
        </div>
      ))}
    </dl>
  );
}

function Steps({ items }: { items: [string, string][] }) {
  return (
    <ol className="grid gap-px overflow-hidden rounded-lg bg-ink ring-2 ring-ink sm:grid-cols-2 xl:grid-cols-3">
      {items.map(([title, text], index) => (
        <li key={title} className="bg-surface p-5">
          <span className="font-display text-5xl font-black leading-none text-brand">{index + 1}</span>
          <p className="mt-2 font-bold text-ink">{title}</p>
          <p className="mt-1 text-sm">{text}</p>
        </li>
      ))}
    </ol>
  );
}

const linkClass = "font-semibold text-brand hover:underline";

function FirstDay() {
  return (
    <Section id="first-day" title="Your first day" intro="Five steps, from signing up to being on the homepage.">
      <Steps
        items={[
          ["Sign up", "It’s free and you get 200 Arena Points to start (once)."],
          ["Set up your ad", "In Account: your project’s name, an image, one line and your website."],
          ["Earn more points", "In Earn points, open a project’s website: every 10 seconds you watch it, +10 points."],
          ["Bid in the Arena", "Bid your points. You can bid several times and it adds up. We tell you if someone outbids you."],
          ["Win the homepage", "At midnight the highest bid wins: its website goes full screen for 24 hours."],
        ]}
      />
    </Section>
  );
}

function TheArena() {
  return (
    <Section
      id="arena"
      title="The Arena: bidding"
      intro="There’s a round every day that closes at 00:00 (Madrid time). Whoever has the highest total when it closes wins the next day’s homepage."
    >
      <Rules
        items={[
          ["Bidding", "You need your ad ready and some points. Your first bid of the day is at least 100 points; after that you can add more (at least 100 each time). Your bids for the day add up."],
          ["Standings", "Highest bid first, live. On a tie, whoever reached that total first goes ahead. If someone outbids you, we tell you straight away."],
          ["Last-minute bids", "A bid in the last 2 minutes pushes the close back 2 more minutes (up to a limit), so everyone has time to answer."],
          ["If you don’t win", "You automatically keep 50% of your points as your starting bid for the next day. The other 50% is lost. The winner spends their whole bid."],
        ]}
      />
      <div className="rounded-md bg-canvas p-5 ring-1 ring-line">
        <p className="font-bold text-ink">An example</p>
        <p className="mt-2 text-sm">
          Ana bids 1,000 + 600 = 1,600 points. Luis bids 1,200. At midnight Ana wins: she spends her 1,600 and her
          website goes on the homepage. Luis starts the next day with 600 points already bid (half of 1,200); the other
          600 are lost.
        </p>
      </div>
      <Link href="/arena" className={`inline-flex items-center gap-2 ${linkClass}`}>
        Go to the Arena <ArrowRightIcon className="size-4" />
      </Link>
    </Section>
  );
}

function IfYouWin() {
  return (
    <Section
      id="winning"
      title="If you win"
      intro="Your ad takes over the AdArena homepage for 24 hours as a full-screen animated presentation built from your own website."
    >
      <Rules
        items={[
          [
            "It builds itself",
            <>
              We read your website (logo, colour, headline, section titles and photos) and build a five-scene
              presentation in your brand colour. You can preview it in{" "}
              <Link href="/account" className={linkClass}>
                Account
              </Link>
              .
            </>,
          ],
          ["Reviewed first", "An admin reviews your ad and presentation. Once approved it goes live exactly as reviewed, even if your website changes later. If it’s rejected, you get 100% of your points back and the runner-up takes the spot."],
          ["500 bonus points", "When your ad goes live, you get 500 points so you can bid again."],
          ["Make it look great", "A website with a good share image (the one that shows when you paste your link in a chat), a logo, a brand colour and clear section titles makes a great presentation."],
        ]}
      />
    </Section>
  );
}

function Points() {
  const rows = [
    ["Signing up", "200", "Once"],
    ["Watching an Arena project’s website", "10 every 10 s, plus 40 at 60 s", "Up to 100 per website per day"],
    ["Bonus links (watching a community link)", "20 per link", "10 links a day"],
    ["Winning the Arena (when your ad goes live)", "500", "So you can bid again"],
  ];
  return (
    <Section
      id="points"
      title="Arena Points"
      intro="The points you bid with. They aren’t money: they can’t be bought, sold, exchanged for anything outside AdArena or moved to another account."
    >
      <div className="overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
        <table className="w-full text-left text-sm">
          <thead className="bg-ink text-white">
            <tr>
              <th className="px-5 py-3 font-semibold">How you get them</th>
              <th className="px-5 py-3 font-semibold">Points</th>
              <th className="hidden px-5 py-3 font-semibold sm:table-cell">Limit</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map(([how, points, limit]) => (
              <tr key={how}>
                <td className="px-5 py-3.5 text-ink">
                  {how}
                  <span className="mt-0.5 block text-xs text-muted sm:hidden">{limit}</span>
                </td>
                <td className="tabular px-5 py-3.5 font-semibold text-ink">{points}</td>
                <td className="hidden px-5 py-3.5 sm:table-cell">{limit}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p>
        Your balance is always at the top of the page, and every movement is listed in{" "}
        <Link href="/account/points" className={linkClass}>
          Account, My points
        </Link>
        . Points are only spent when you bid.
      </p>
    </Section>
  );
}

function Watching() {
  return (
    <Section
      id="watching"
      title="Earning by watching"
      intro="Earn points lists the projects bidding today. Open one and you get the points bar on top and their actual website below. Points only count while you’re looking at it."
    >
      <Rules
        items={[
          ["Inside AdArena", "Most websites show right here, under the bar, and you can browse them normally. The ring fills every 10 seconds: +10 points. At 60 seconds, a +40 bonus."],
          [
            "In their own window",
            "Some websites don’t allow being shown inside others (YouTube, Instagram, many shops). Press Open and it opens in a separate window. Points count while you’re on that website, that is, while you’re away from AdArena. Coming back to AdArena or pressing I’m done stops the count. If your browser blocks the window, allow pop-ups for AdArena.",
          ],
        ]}
      />
      <div className="rounded-lg bg-night p-6 text-white">
        <p className="font-bold">The count stops straight away if you…</p>
        <ul className="mt-3 grid gap-2 text-sm text-white/80 sm:grid-cols-2">
          {[
            "Switch tabs or minimise the browser (inside AdArena)",
            "Go 45 seconds without touching anything (inside AdArena)",
            "Come back to AdArena from the separate window",
            "Press I’m done",
          ].map((text) => (
            <li key={text} className="flex items-start gap-2">
              <span className="mt-1.5 size-1.5 shrink-0 bg-gold" /> {text}
            </li>
          ))}
        </ul>
        <p className="mt-4 text-sm text-white/60">Paused time doesn’t count, but you don’t lose what was already in the ring.</p>
      </div>
      <Rules
        items={[
          ["Why not faster?", "Nobody can watch two websites at once. So AdArena’s server keeps one clock per person: ten tabs, or a website and a bonus link at the same time, earn the same as one. And it never pays for more time than has really passed, even if someone tampers with their browser."],
          ["Limits", "Up to 100 points a day per project (six 10-second rounds plus the bonus). Your own projects don’t earn points. Everything resets every day at 00:00 Madrid time."],
        ]}
      />
      <Link href="/earn" className={`inline-flex items-center gap-2 ${linkClass}`}>
        Go to Earn points <ArrowRightIcon className="size-4" />
      </Link>
    </Section>
  );
}

function BonusLinks() {
  return (
    <Section
      id="bonus-links"
      title="Bonus links"
      intro="Links the community promotes: YouTube channels, Instagram or TikTok profiles, websites. Each one gives 20 points for watching it for 10 seconds."
    >
      <Rules
        items={[
          ["Watch and earn", "Open it, watch it for 10 seconds and the points are added. It opens like Arena websites do: inside AdArena or in its own window."],
          ["Once a day", "Each link pays once a day, and up to 10 links a day."],
          ["No likes for points", "You earn by watching. Following or liking is up to you and never earns points (social networks forbid it)."],
          ["Reporting", "Something wrong with a link (broken, misleading, dangerous)? Open it, press the info button and report it. After 3 reports from different people it’s hidden until we review it."],
        ]}
      />
    </Section>
  );
}

function Promote() {
  return (
    <Section
      id="promote"
      title="Promote your link"
      intro="Post your channel, profile or website and it appears in everyone’s bonus links. It costs no points and no money."
    >
      <Steps
        items={[
          ["Paste your link", "In Promote. https links only. We detect the network (YouTube, X, Instagram…) automatically."],
          ["It’s live at once", "We read your link to add its image and logo. You can have up to 5 live at the same time."],
          ["Track your visits", "See how many people watched it today and in total. Pause or delete it whenever you want."],
        ]}
      />
      <Rules
        items={[
          ["Rules", "Nothing illegal, misleading, sexual, violent, gambling-related or with malware. Don’t ask for likes or follows in exchange for points. If a promotion breaks the rules, we hide it and tell you why."],
        ]}
      />
      <Link href="/promote" className={`inline-flex items-center gap-2 ${linkClass}`}>
        Promote my link <ArrowRightIcon className="size-4" />
      </Link>
    </Section>
  );
}

function Faq() {
  const items = [
    ["Can I buy points?", "No. Arena Points are only earned by taking part: signing up, watching websites, bonus links and winning the Arena."],
    ["Do points expire?", "No. Points you don’t spend stay in your account."],
    ["Why did the count stop?", "Because you stopped watching the website: you switched tabs, went a while without touching anything, came back to AdArena from its window or pressed I’m done. Go back to it and the count carries on."],
    ["Why do some websites open in a separate window?", "Each website decides whether it can be shown inside others. The ones that can’t (like YouTube or Instagram) open separately, and points count while you’re on them."],
    ["Can I use several accounts?", "No. It’s forbidden and points earned that way are cancelled. Points can’t be moved between accounts either."],
    ["Are there ads on AdArena?", "Yes, Google ads on Promote and small ones under the standings. Never on pages where you earn points: points never depend on seeing or clicking ads."],
    ["My presentation doesn’t look right. What can I do?", "In Account you can ask us to read your website again. If it can’t be read, your classic ad is shown instead (image, name and description)."],
  ];
  return (
    <Section id="faq" title="Questions" intro="What people ask us most.">
      <div className="divide-y divide-line overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
        {items.map(([question, answer]) => (
          <details key={question} className="group px-5 py-4 open:bg-canvas/60">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold text-ink">
              {question}
              <span className="grid size-7 shrink-0 place-items-center rounded-sm bg-canvas text-lg leading-none transition group-open:rotate-45">
                +
              </span>
            </summary>
            <p className="mt-2 text-sm leading-relaxed">{answer}</p>
          </details>
        ))}
      </div>
      <p className="text-sm">
        The full rules are in the{" "}
        <Link href="/legal/terms" className={linkClass}>
          terms and conditions
        </Link>
        .
      </p>
    </Section>
  );
}
