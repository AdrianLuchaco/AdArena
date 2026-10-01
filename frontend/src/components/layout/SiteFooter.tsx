import Link from "next/link";
import { LogoMark } from "../Logo";

const LINKS = [
  { href: "/how-it-works", label: "How it works" },
  { href: "/guides/where-to-launch-your-startup", label: "Where to launch" },
  { href: "/alternatives/product-hunt", label: "vs Product Hunt" },
  { href: "/about", label: "About" },
  { href: "/legal/terms", label: "Terms" },
  { href: "/legal/privacy", label: "Privacy" },
];

export function SiteFooter() {
  return (
    <footer className="border-t-2 border-ink bg-canvas">
      <div className="mx-auto flex max-w-6xl flex-col gap-5 px-4 py-8 text-sm text-muted sm:px-6 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-2">
          <LogoMark className="size-5" />
          <span>© {new Date().getFullYear()} LaunchCrown. One homepage, one winner, every day.</span>
        </div>
        <nav className="flex flex-wrap gap-x-5 gap-y-2" aria-label="Footer">
          {LINKS.map((link) => (
            <Link key={link.href} href={link.href} className="font-medium hover:text-ink">
              {link.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}
