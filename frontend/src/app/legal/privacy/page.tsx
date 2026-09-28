import type { Metadata } from "next";
import { LegalPage } from "@/components/legal/LegalPage";

export const metadata: Metadata = { title: "Privacy policy" };

export default function PrivacyPage() {
  return (
    <LegalPage title="Privacy policy" updated="27 September 2026">
      <p>We handle your data carefully and only for what’s necessary. In short:</p>

      <h2>What data we keep</h2>
      <ul>
        <li>Your name, email and password (encrypted: nobody, not even us, can read it).</li>
        <li>Your ad: project name, website, description and image.</li>
        <li>Your bids and your Arena Points movements.</li>
        <li>
          Which projects you watched and for how long, and which bonus links you completed, to give you your points,
          apply daily limits and prevent cheating.
        </li>
        <li>The links you promote and the reports you send.</li>
        <li>
          For the websites you link to (your ad and your promotions): what their page shows publicly (title,
          description, logo, colour, section titles and photos), for their card and the winner’s presentation.
        </li>
      </ul>

      <h2>What for</h2>
      <ul>
        <li>To manage your account, your points, your bids and the publication of your ad and promotions.</li>
        <li>To notify you by email, for example when someone outbids you.</li>
        <li>To detect abuse (bots, duplicate accounts) and protect the Arena.</li>
      </ul>

      <h2>Your rights</h2>
      <p>You can ask for access, correction or deletion of your data, and the other GDPR rights, by writing to us.</p>

      <h2>Cookies</h2>
      <p>
        We use one essential technical cookie to keep you logged in. On Promote and under the Arena standings we show
        Google AdSense ads: Google may use cookies to show and measure them, and will ask for your consent first with its
        own notice. You can change your choice in that notice or in your Google account’s ad settings. Pages where you
        earn points have no ads.
      </p>
      <p>
        When you watch another project’s website inside AdArena, that website belongs to its owner and may use its own
        cookies, just as if you visited it directly. YouTube videos are shown in their privacy-enhanced mode.
      </p>
    </LegalPage>
  );
}
