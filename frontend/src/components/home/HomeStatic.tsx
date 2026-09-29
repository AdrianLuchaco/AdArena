import Link from "next/link";
import { AdUnit } from "../ads/AdUnit";
import { ArrowRightIcon } from "../icons";

/**
 * La parte de la portada que no depende de los datos del día: los 3 pasos para ganar, qué es
 * LaunchCrown y el anuncio pequeño. Es un componente de servidor, así que llega ya escrito en el HTML:
 * Google lo lee sin tener que ejecutar JavaScript ni esperar a la API (el resto de la portada, sí).
 */
export function HomeStatic() {
  return (
    <>
      <HowToWin />
      <AboutLaunchCrown />

      {/* Un anuncio pequeño, al final y lejos de los botones */}
      <div className="mx-auto w-full max-w-5xl px-4 pb-14 sm:px-6">
        <AdUnit placement="homeBanner" />
      </div>
    </>
  );
}

const STEPS = [
  {
    title: "Earn points",
    text: "Get 200 when you sign up. Then earn more for free by watching other projects’ websites.",
    href: "/earn",
    link: "Start earning",
  },
  {
    title: "Bid",
    text: "Set up your ad and bid your points. Bids add up, and we tell you the moment someone outbids you.",
    href: "/race",
    link: "Go to the Race",
  },
  {
    title: "Win the homepage",
    text: "Highest bid at midnight wins. Your website becomes the full-screen homepage for 24 hours, plus 500 points.",
    href: "/winners",
    link: "See past winners",
  },
];

/** Los 3 pasos para ganar (es una secuencia de verdad, por eso van numerados). */
function HowToWin() {
  return (
    <section className="border-y-2 border-ink bg-surface">
      <div className="mx-auto max-w-5xl px-4 py-12 sm:px-6 sm:py-16">
        <h2 className="text-4xl sm:text-5xl">How to win</h2>
        <ol className="mt-8 grid gap-px overflow-hidden rounded-lg bg-ink ring-2 ring-ink md:grid-cols-3">
          {STEPS.map((step, index) => (
            <li key={step.title} className="flex flex-col bg-surface p-6">
              <span className="font-display text-7xl font-black leading-none text-brand" aria-hidden="true">
                {index + 1}
              </span>
              <h3 className="mt-3 text-3xl">{step.title}</h3>
              <p className="mt-2 flex-1 leading-relaxed text-ink-soft">{step.text}</p>
              <Link
                href={step.href}
                className="mt-4 inline-flex items-center gap-1.5 font-semibold text-brand hover:underline"
              >
                {step.link} <ArrowRightIcon className="size-4" />
              </Link>
            </li>
          ))}
        </ol>
        <p className="mt-6 text-ink-soft">
          If you don’t win, you keep 50% of your bid for tomorrow.{" "}
          <Link
            href="/how-it-works"
            className="font-semibold text-ink underline decoration-brand decoration-2 underline-offset-4"
          >
            Read the full guide
          </Link>
        </p>
      </div>
    </section>
  );
}
const AUDIENCES = [
  {
    title: "For founders",
    text: "Launching a startup or a side project? Win a full day on the homepage, in front of people who actually open it and look around.",
  },
  {
    title: "For makers and creators",
    text: "Promote your YouTube channel, your X profile or your website for free in Bonus links, where the community visits it.",
  },
  {
    title: "For the curious",
    text: "Discover new startups and indie projects every day, and earn Crown Points while you do. You can bid them for your own project.",
  },
];

/** Qué es LaunchCrown y para quién: texto de verdad para quien llega nuevo (y para Google). */
function AboutLaunchCrown() {
  return (
    <section className="mx-auto w-full max-w-5xl px-4 py-12 sm:px-6 sm:py-16">
      <h2 className="text-4xl sm:text-5xl">Free promotion for startups</h2>
      <p className="mt-4 max-w-3xl text-lg leading-relaxed text-ink-soft">
        LaunchCrown is a daily race for one homepage. Startups, indie hackers and creators bid Crown Points, and the
        highest bid at midnight takes over the whole homepage for 24 hours. There’s nothing to pay and no ads to buy:
        points can’t be bought, only earned by discovering other people’s projects.
      </p>
      <div className="mt-8 grid gap-6 md:grid-cols-3">
        {AUDIENCES.map((item) => (
          <div key={item.title}>
            <h3 className="text-2xl">{item.title}</h3>
            <p className="mt-2 leading-relaxed text-ink-soft">{item.text}</p>
          </div>
        ))}
      </div>
      <p className="mt-8 flex flex-wrap gap-x-6 gap-y-2 font-semibold">
        <Link href="/promote" className="inline-flex items-center gap-1.5 text-brand hover:underline">
          Promote your link for free <ArrowRightIcon className="size-4" />
        </Link>
        <Link href="/how-it-works" className="inline-flex items-center gap-1.5 text-brand hover:underline">
          How LaunchCrown works <ArrowRightIcon className="size-4" />
        </Link>
      </p>
    </section>
  );
}
