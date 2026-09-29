import type { Metadata } from "next";
import { pageMetadata } from "@/lib/site";
import Link from "next/link";
import { LegalPage } from "@/components/legal/LegalPage";
import { CONTACT_EMAIL, LEGAL_UPDATED } from "@/lib/legal";

export const metadata: Metadata = pageMetadata({
  title: "Terms and conditions",
  description:
    "The rules of LaunchCrown: Crown Points, the daily Race for the homepage, prohibited content and your account.",
  path: "/legal/terms",
});

export default function TermsPage() {
  return (
    <LegalPage title="Terms and conditions" updated={`${LEGAL_UPDATED} (version 2026-09-29-en)`}>
      <p>
        On LaunchCrown, projects compete every day by bidding <strong>Crown Points</strong> to take over the website’s
        homepage for 24 hours (the “Race”). By creating an account you accept these terms. Please read them carefully,
        especially the section on <strong>what happens to your points if you don’t win</strong>.
      </p>
      <p>
        LaunchCrown is run from Spain. You can contact us at <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>. How we
        handle your data is explained in the <Link href="/legal/privacy">Privacy policy</Link>.
      </p>

      <h2>1. Your account</h2>
      <ul>
        <li>You must be at least 14 years old to create an account.</li>
        <li>
          One account per person. Give real information and keep your password to yourself: you are responsible for what
          is done from your account.
        </li>
        <li>
          You can stop using LaunchCrown whenever you want and ask us to delete your account by writing to us from its
          email.
        </li>
      </ul>

      <h2>2. What Crown Points are</h2>
      <ul>
        <li>
          They are points for taking part in LaunchCrown. <strong>They are not money</strong> and have no monetary value:
          they can’t be bought, sold, exchanged for money or for anything outside LaunchCrown, or transferred to another
          account.
        </li>
        <li>
          You get them for free: 200 when you sign up (once), by watching other users’ project websites, through bonus
          links and, if you win the Race, 500 when your ad goes live on the homepage.
        </li>
        <li>
          We may change how many points each action earns and the daily limits. If we close LaunchCrown or your account, the
          points disappear without compensation, because they have no monetary value.
        </li>
      </ul>

      <h2>3. How the Race works</h2>
      <ul>
        <li>There is a bidding round every day that closes at 00:00 (Madrid time).</li>
        <li>You can bid several times: your bids for the day add up.</li>
        <li>
          If someone bids in the last 2 minutes, the close is pushed back 2 minutes (up to a maximum number of
          extensions).
        </li>
        <li>Whoever has the highest total at the close wins. On a tie, whoever reached that total first wins.</li>
      </ul>

      <h2>4. What happens to your points</h2>
      <ul>
        <li>
          <strong>If you win:</strong> you spend all the points in your bid and your ad takes over the homepage the
          following day, after review. When it goes live, we give you 500 points so you can bid again.
        </li>
        <li>
          <strong>If you don’t win:</strong> you automatically keep <strong>50%</strong> of your bid as your starting
          bid for the next day. The other <strong>50% is lost</strong>.
        </li>
        <li>
          <strong>If your winning ad is rejected</strong> in review, you get 100% of your points back and the runner-up
          takes the spot.
        </li>
        <li>
          <strong>If we don’t review your winning ad in time</strong> (before its day on the homepage ends), you get
          100% of your points back.
        </li>
      </ul>

      <h2>5. Earning points fairly</h2>
      <ul>
        <li>
          <strong>Watching websites:</strong> you earn points while you watch a project’s website, inside LaunchCrown or, if
          that website doesn’t allow being shown inside others, in its own window. Inside LaunchCrown, the count stops if
          you switch tabs, go to another app or stop using it. With a separate window, points count while you’re away
          from LaunchCrown on that website, and stop when you come back to LaunchCrown or say you’re done.
        </li>
        <li>
          <strong>Bonus links:</strong> you earn points by watching links other users promote. We will never ask you for
          likes, follows or subscriptions in exchange for points.
        </li>
        <li>
          Other users’ websites you see on LaunchCrown are the responsibility of their owners. They are shown isolated: they
          can’t access your LaunchCrown account.
        </li>
        <li>
          There are daily limits. Using bots, scripts, extensions that simulate activity, multiple accounts or any other
          trick to earn points is forbidden. If we detect it, we may cancel the points earned that way and suspend the
          account.
        </li>
        <li>No points are ever earned for seeing or clicking ads.</li>
      </ul>

      <h2>6. Promoting your links</h2>
      <ul>
        <li>
          You can post links to your website or your profiles (YouTube, X, Instagram…) for free so they appear in other
          users’ bonus links. They go live straight away.
        </li>
        <li>
          You are responsible for what you link to. The same prohibited content rules as for ads apply. Users can report
          a promotion; after several reports it is hidden until we review it, and we may hide it if it breaks these
          rules.
        </li>
        <li>
          Don’t ask for likes, follows or subscriptions in exchange for points: the networks themselves forbid it and
          could penalise your account.
        </li>
      </ul>

      <h2>7. What is shown publicly</h2>
      <p>
        While you compete, your ad (name, image, description and website) and your current bid total are shown on the
        homepage and in the Race. When the day ends, they stay in the winners’ history as they were at the close. Your
        promotions are shown to logged-in users.
      </p>

      <h2>8. Prohibited content</h2>
      <p>We will not publish ads or promotions that include, among other things:</p>
      <ul>
        <li>Illegal, misleading or fraudulent content, or content that infringes third-party rights.</li>
        <li>Sexually explicit or violent content, or content that incites hatred or discrimination.</li>
        <li>Gambling, betting, weapons, drugs or restricted products.</li>
        <li>Links to websites with malware, phishing or impersonation.</li>
      </ul>

      <h2>9. Your ad and your website</h2>
      <p>
        You are responsible for the content of your ad and your website. You can edit it until the day’s round closes.
      </p>
      <ul>
        <li>
          By saving your ad or posting a promotion, you authorise us to read the public page you link to (its title,
          description, logo, colour, section titles and photos) to show its card and, if you win, to build your animated
          homepage presentation. We only read what anyone can see on that page.
        </li>
        <li>
          While you compete, other users can view your website inside LaunchCrown (if your website allows it) to earn
          points. Your website decides whether it can be shown inside others.
        </li>
        <li>
          Visits you receive from LaunchCrown (through your ad or your promotions) come from people who earn points for
          watching you. If your website shows ads (for example Google AdSense), check your ad network’s rules on this
          kind of traffic: that is your responsibility.
        </li>
        <li>
          The winner’s presentation is reviewed before it goes live and is published exactly as approved. On the
          homepage, the “Visit website” button opens your website in a new tab.
        </li>
      </ul>

      <h2>10. Advertising</h2>
      <p>
        LaunchCrown may be funded by ads served by our advertising partner, Ezoic, and the advertisers it works with. They
        appear on the homepage, on Promote and, small, under the Race standings. Never on pages where you earn points:
        points never depend on seeing or clicking ads. Those ads are chosen by our partner; we don’t review them one by
        one and they are not part of the Race.
      </p>

      <h2>11. Suspending or closing accounts</h2>
      <p>
        If you break these terms (for example, by cheating to earn points or publishing prohibited content), we may
        remove the content, cancel the points involved, exclude you from the Race or suspend or close your account.
        When possible, we will tell you why by email, and you can reply to explain your side.
      </p>

      <h2>12. Availability and liability</h2>
      <ul>
        <li>
          LaunchCrown is a free service offered “as is”. We work to keep it running, but there may be interruptions,
          maintenance or errors. If a technical problem affects a day’s Race, we may extend, repeat or cancel that
          round and, if so, return the points bid.
        </li>
        <li>We don’t guarantee any number of visits or results for your ad or your promotions.</li>
        <li>
          We are not responsible for the content of other users’ ads, promotions or websites, nor for ads shown by our
          advertising partner. If you see something that breaks these terms, report it or write to us.
        </li>
        <li>Nothing in these terms limits the rights that consumer protection law gives you.</li>
      </ul>

      <h2>13. Changes to these terms</h2>
      <p>
        We may update these terms to improve LaunchCrown or to comply with the law. If the changes are important, we will
        tell you in advance by email or on the website. If you keep using LaunchCrown after they come into force, you accept
        the new version; if you don’t agree, you can close your account.
      </p>

      <h2>14. Applicable law</h2>
      <p>
        These terms are governed by Spanish law. If there is a dispute, we will first try to solve it by talking to you.
        If that isn’t possible, the courts that correspond by law will decide; if you are a consumer, that is the courts
        of your place of residence.
      </p>
    </LegalPage>
  );
}
