import type { Metadata } from "next";
import { LegalPage } from "@/components/legal/LegalPage";

export const metadata: Metadata = { title: "Terms and conditions" };

export default function TermsPage() {
  return (
    <LegalPage title="Terms and conditions" updated="27 September 2026 (version 2026-09-27-en)">
      <p>
        On AdArena, projects compete every day by bidding <strong>Arena Points</strong> to take over the website’s
        homepage for 24 hours (the “Arena”). By creating an account you accept these terms. Please read them carefully,
        especially the section on <strong>what happens to your points if you don’t win</strong>.
      </p>

      <h2>1. What Arena Points are</h2>
      <ul>
        <li>
          They are points for taking part in AdArena. <strong>They are not money</strong> and have no monetary value:
          they can’t be bought, sold, exchanged for money or for anything outside AdArena, or transferred to another
          account.
        </li>
        <li>
          You get them for free: 200 when you sign up (once), by watching other users’ project websites, through bonus
          links and, if you win the Arena, 500 when your ad goes live on the homepage.
        </li>
        <li>
          We may change how many points each action earns and the daily limits. If we close AdArena or your account, the
          points disappear without compensation, because they have no monetary value.
        </li>
      </ul>

      <h2>2. How the Arena works</h2>
      <ul>
        <li>There is a bidding round every day that closes at 00:00 (Madrid time).</li>
        <li>You can bid several times: your bids for the day add up.</li>
        <li>If someone bids in the last 2 minutes, the close is pushed back 2 minutes (up to a maximum number of extensions).</li>
        <li>Whoever has the highest total at the close wins. On a tie, whoever reached that total first wins.</li>
      </ul>

      <h2>3. What happens to your points</h2>
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
          <strong>If we don’t review your winning ad in time</strong> (before its day on the homepage ends), you get 100%
          of your points back.
        </li>
      </ul>

      <h2>4. Earning points fairly</h2>
      <ul>
        <li>
          <strong>Watching websites:</strong> you earn points while you watch a project’s website, inside AdArena or, if
          that website doesn’t allow being shown inside others, in its own window. Inside AdArena, the count stops if you
          switch tabs, go to another app or stop using it. With a separate window, points count while you’re away from
          AdArena on that website, and stop when you come back to AdArena or say you’re done.
        </li>
        <li>
          <strong>Bonus links:</strong> you earn points by watching links other users promote. We will never ask you for
          likes, follows or subscriptions in exchange for points.
        </li>
        <li>
          Other users’ websites you see on AdArena are the responsibility of their owners. They are shown isolated: they
          can’t access your AdArena account.
        </li>
        <li>
          There are daily limits. Using bots, scripts, extensions that simulate activity, multiple accounts or any other
          trick to earn points is forbidden. If we detect it, we may cancel the points earned that way and suspend the
          account.
        </li>
        <li>No points are ever earned for seeing or clicking ads.</li>
      </ul>

      <h2>5. Promoting your links</h2>
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

      <h2>6. What is shown publicly</h2>
      <p>
        While you compete, your ad (name, image, description and website) and your current bid total are shown on the
        homepage and in the Arena. When the day ends, they stay in the winners’ history as they were at the close. Your
        promotions are shown to logged-in users.
      </p>

      <h2>7. Prohibited content</h2>
      <p>We will not publish ads or promotions that include, among other things:</p>
      <ul>
        <li>Illegal, misleading or fraudulent content, or content that infringes third-party rights.</li>
        <li>Sexually explicit or violent content, or content that incites hatred or discrimination.</li>
        <li>Gambling, betting, weapons, drugs or restricted products.</li>
        <li>Links to websites with malware, phishing or impersonation.</li>
      </ul>

      <h2>8. Your ad and your website</h2>
      <p>You are responsible for the content of your ad and your website. You can edit it until the day’s round closes.</p>
      <ul>
        <li>
          By saving your ad or posting a promotion, you authorise us to read the public page you link to (its title,
          description, logo, colour, section titles and photos) to show its card and, if you win, to build your animated
          homepage presentation. We only read what anyone can see on that page.
        </li>
        <li>
          While you compete, other users can view your website inside AdArena (if your website allows it) to earn
          points. Your website decides whether it can be shown inside others.
        </li>
        <li>
          Visits you receive from AdArena (through your ad or your promotions) come from people who earn points for
          watching you. If your website shows ads (for example Google AdSense), check your ad network’s rules on this
          kind of traffic: that is your responsibility.
        </li>
        <li>
          The winner’s presentation is reviewed before it goes live and is published exactly as approved. On the
          homepage, the “Visit website” button opens your website in a new tab.
        </li>
      </ul>

      <h2>9. Advertising</h2>
      <p>
        AdArena may be funded by ads served by our advertising partner, Ezoic, and the advertisers it works with. They
        appear on the homepage, on Promote and, small, under the Arena standings. Never on pages where you earn points:
        points never depend on seeing or clicking ads. Those ads are chosen by our partner; we don’t review them one by
        one and they are not part of the Arena.
      </p>
    </LegalPage>
  );
}
