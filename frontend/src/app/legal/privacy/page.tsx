import type { Metadata } from "next";
import { LegalPage } from "@/components/legal/LegalPage";
import { CONTACT_EMAIL, LEGAL_UPDATED } from "@/lib/legal";

export const metadata: Metadata = { title: "Privacy policy" };

export default function PrivacyPage() {
  return (
    <LegalPage title="Privacy policy" updated={LEGAL_UPDATED}>
      <p>
        We handle your data carefully and only for what’s necessary to run LaunchCrown. This page explains what we keep,
        why, who helps us process it and what rights you have under the EU General Data Protection Regulation (GDPR) and
        Spanish data protection law.
      </p>

      <h2>Who is responsible</h2>
      <p>
        LaunchCrown is run from Spain. The data controller is the owner of LaunchCrown, whom you can reach at{" "}
        <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> for anything related to your data.
      </p>

      <h2>What data we keep</h2>
      <ul>
        <li>
          <strong>Your account:</strong> name, email and password. The password is stored hashed: nobody, not even us,
          can read it.
        </li>
        <li>
          <strong>Your ad:</strong> project name, website, description and image.
        </li>
        <li>
          <strong>Your activity:</strong> your bids, your Crown Points movements, which projects you watched and for how
          long, and which bonus links you completed. We need this to give you your points, apply daily limits and
          prevent cheating.
        </li>
        <li>
          <strong>Your promotions</strong> and the reports you send about other users’ content.
        </li>
        <li>
          <strong>The websites you link to</strong> (your ad and your promotions): what their page shows publicly
          (title, description, logo, colour, section titles and photos), to build their card and the winner’s
          presentation.
        </li>
        <li>
          <strong>Technical data:</strong> your IP address, used only for a short time to limit abuse (for example, too
          many login attempts). We don’t use it to track you.
        </li>
      </ul>

      <h2>What we use it for, and on what legal basis</h2>
      <ul>
        <li>
          <strong>To provide the service</strong> (your account, points, bids and the publication of your ad and
          promotions): because it is necessary to fulfil the terms you accept when you sign up.
        </li>
        <li>
          <strong>To send you service emails</strong> (for example, when someone outbids you, or to reset your
          password): same basis. We don’t send marketing emails.
        </li>
        <li>
          <strong>To detect abuse</strong> (bots, duplicate accounts, fake activity) and keep the Race fair: our
          legitimate interest, and yours, in a fair competition.
        </li>
        <li>
          <strong>To show ads</strong>, if you allow it in the consent notice: your consent, which you can withdraw at
          any time.
        </li>
      </ul>

      <h2>What is public</h2>
      <p>
        While you compete, your ad (project name, image, description and website) and your bid total are visible to
        everyone on the homepage and in the Race, and winners stay in the public winners’ history. Your promotions are
        shown to logged-in users. Your name and email are never shown to other users.
      </p>

      <h2>Who helps us</h2>
      <p>
        We don’t sell your data. We only share it with the providers we need to run LaunchCrown, who process it on our
        behalf:
      </p>
      <ul>
        <li>
          <strong>Supabase</strong> (database) and <strong>Render</strong> (server), with data stored in Frankfurt,
          Germany.
        </li>
        <li>
          <strong>Vercel</strong> (website hosting and delivery).
        </li>
        <li>
          <strong>Brevo</strong> (sending emails), based in the EU.
        </li>
        <li>
          <strong>Ezoic</strong> and its advertising partners, only when ads are active and you have consented.
        </li>
      </ul>
      <p>
        Some of these companies are based in the United States. When data leaves the EU, it is protected by the EU–US
        Data Privacy Framework or the European Commission’s standard contractual clauses.
      </p>

      <h2>How long we keep it</h2>
      <ul>
        <li>Your account data, while your account exists.</li>
        <li>
          When you ask us to delete your account, we delete or anonymise your personal data within 30 days. Winners’
          history entries may be kept without your personal data.
        </li>
        <li>Data that the law requires us to keep, for as long as it requires.</li>
      </ul>

      <h2>Your rights</h2>
      <p>
        You can ask to access, correct or delete your data, to restrict or object to its processing, and to receive it
        in a portable format, by writing to <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> from the email of
        your account. We will answer within one month. If you think we haven’t handled your data properly, you can also
        complain to the Spanish Data Protection Agency (
        <a href="https://www.aepd.es" target="_blank" rel="noopener noreferrer">
          aepd.es
        </a>
        ).
      </p>

      <h2>Minimum age</h2>
      <p>
        LaunchCrown is for people aged 14 or over, the age from which Spanish law lets you consent to the use of your data.
        If we find out that an account belongs to someone younger, we will delete it.
      </p>

      <h2>Security</h2>
      <p>
        All connections to LaunchCrown are encrypted (HTTPS), passwords are hashed, and access to the database is limited to
        the server. No system is 100% secure, but if a breach ever affected your data, we would tell you and the
        authorities as the law requires.
      </p>

      <h2>Cookies</h2>
      <p>
        We use one essential technical cookie to keep you logged in. It doesn’t need your consent because the website
        can’t work without it.
      </p>
      <p>
        When ads are shown (on the homepage, on Promote and under the Race standings), our advertising partner Ezoic
        and the advertisers it works with may use cookies to show and measure them. Before that, a consent notice asks
        for your choice, and you can change it at any time from that notice. Pages where you earn points have no ads.
      </p>
      <p>
        When you watch another project’s website inside LaunchCrown, that website belongs to its owner and may use its own
        cookies, just as if you visited it directly. YouTube videos are shown in their privacy-enhanced mode.
      </p>

      <h2>Changes to this policy</h2>
      <p>
        If we make important changes, we will update the date at the top of this page and, when it affects you, let you
        know by email or on the website.
      </p>
    </LegalPage>
  );
}
