"use client";

import Image from "next/image";
import Link from "next/link";
import { Fragment, useCallback, useEffect, useState } from "react";
import { ApiError, createPromotion, deletePromotion, getMyPromotions, pausePromotion, resumePromotion } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import { formatDateTime, formatPoints, prettyUrl } from "@/lib/format";
import type { Promotion, PromotionsOverview, PromotionStatus, SocialPlatform } from "@/lib/types";
import { AdSenseScript, AdSenseUnit } from "../ads/AdSenseUnit";
import { ModeChip } from "../earn/EarnProjectsView";
import { platformColor, PlatformBadge, platformSurface } from "../earn/PlatformBadge";
import { Alert } from "../ui/Alert";
import { Button, ButtonLink } from "../ui/Button";
import { TextField } from "../ui/Field";
import { PageSpinner } from "../ui/Spinner";
import { useToast } from "../ui/Toaster";
import { BookIcon, CheckIcon, EyeIcon, MegaphoneIcon } from "../icons";
import { SiteAvatar } from "../viewer/SiteAvatar";

const STATUS: Record<PromotionStatus, { text: string; className: string }> = {
  ACTIVE: { text: "Live", className: "bg-success-soft text-success" },
  PAUSED: { text: "Paused", className: "bg-canvas text-muted ring-1 ring-line" },
  HIDDEN: { text: "Hidden for review", className: "bg-danger-soft text-danger" },
};

const PLATFORM_LABEL: Record<SocialPlatform, string> = {
  YOUTUBE: "YouTube",
  X: "X (Twitter)",
  INSTAGRAM: "Instagram",
  TIKTOK: "TikTok",
  TWITCH: "Twitch",
  LINKEDIN: "LinkedIn",
  FACEBOOK: "Facebook",
  GITHUB: "GitHub",
  WEB: "Website",
};

/** La red de un enlace, igual que la detecta el servidor (solo para la vista previa). */
function platformOf(url: string): SocialPlatform {
  const host = url.trim().replace(/^https?:\/\//i, "").split(/[/:?#]/)[0].toLowerCase();
  const is = (domain: string) => host === domain || host.endsWith(`.${domain}`);
  if (is("youtube.com") || is("youtu.be")) return "YOUTUBE";
  if (is("x.com") || is("twitter.com")) return "X";
  if (is("instagram.com")) return "INSTAGRAM";
  if (is("tiktok.com")) return "TIKTOK";
  if (is("twitch.tv")) return "TWITCH";
  if (is("linkedin.com")) return "LINKEDIN";
  if (is("facebook.com") || is("fb.com")) return "FACEBOOK";
  if (is("github.com")) return "GITHUB";
  return "WEB";
}

/**
 * Promote: publica gratis tus enlaces (YouTube, X, Instagram, tu web…) para que salgan en los
 * bonus links de la comunidad. Es la página con más anuncios de AdSense: aquí nadie gana
 * puntos, así se cumplen las normas de Google (nunca se paga por ver anuncios).
 */
export function PromoteView() {
  const { status } = useAuth();

  return (
    <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 sm:py-10">
      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="min-w-0 space-y-8">
          <header className="rounded-lg bg-brand p-6 text-white shadow-lift ring-2 ring-ink sm:p-10">
            <MegaphoneIcon className="size-10" />
            <h1 className="mt-4 max-w-[14ch] text-6xl text-balance sm:text-7xl">Promote your link for free</h1>
            <p className="mt-4 max-w-2xl text-lg text-white/85">
              Post your YouTube channel, your Instagram or X profile, your TikTok or your website. It shows up in
              everyone’s bonus links, and people earn points for watching it. It costs no points and no money.
            </p>
            <ul className="mt-6 flex flex-wrap gap-x-6 gap-y-2 text-sm text-white/90">
              {["Live straight away", "Up to 5 at once", "See how many people watch it each day"].map((text) => (
                <li key={text} className="flex items-center gap-2">
                  <CheckIcon className="size-4" /> {text}
                </li>
              ))}
            </ul>
            <Link href="/how-it-works#promote" className="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-white/90 underline-offset-4 hover:underline">
              <BookIcon className="size-4" /> How promotions work
            </Link>
          </header>

          <AdSenseUnit slot="banner" />

          {status === "loading" ? <PageSpinner /> : status === "anonymous" ? <HowItWorks /> : <MyPromotions />}
        </div>

        <aside className="hidden lg:block" aria-label="Advertising">
          <div className="sticky top-24 space-y-6">
            <AdSenseUnit slot="sidebar" />
            <AdSenseUnit slot="infeed" />
          </div>
        </aside>
      </div>
      <AdSenseScript />
    </div>
  );
}

function HowItWorks() {
  const steps = [
    ["Paste your link", "Your channel, profile or website. Secure (https) links only."],
    ["It shows in bonus links", "Other people watch it to earn points. If they like it, they follow you."],
    ["Track your visits", "This page shows how many people watched it today and in total."],
  ];
  return (
    <div className="space-y-6">
      <ol className="grid gap-px overflow-hidden rounded-lg bg-ink ring-2 ring-ink sm:grid-cols-3">
        {steps.map(([title, text], index) => (
          <li key={title} className="bg-surface p-5">
            <span className="font-display text-5xl font-black leading-none text-brand">{index + 1}</span>
            <p className="mt-2 font-bold">{title}</p>
            <p className="mt-1 text-sm text-ink-soft">{text}</p>
          </li>
        ))}
      </ol>
      <AdSenseUnit slot="infeed" className="lg:hidden" />
      <div className="flex flex-wrap gap-3">
        <ButtonLink href="/signup?next=/promote" size="lg">
          Sign up free
        </ButtonLink>
        <ButtonLink href="/login?next=/promote" variant="secondary" size="lg">
          Log in
        </ButtonLink>
      </div>
    </div>
  );
}

function MyPromotions() {
  const toast = useToast();
  const [overview, setOverview] = useState<PromotionsOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setOverview(await getMyPromotions());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "We couldn’t load your promotions.");
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  async function act(action: () => Promise<unknown>, success: string) {
    try {
      await action();
      toast({ tone: "success", title: success });
      await load();
    } catch (e) {
      toast({ tone: "danger", title: "That didn’t work", message: e instanceof ApiError ? e.message : undefined });
    }
  }

  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!overview) return <PageSpinner label="Loading your promotions…" />;

  const live = overview.promotions.filter((p) => p.status !== "HIDDEN").length;

  return (
    <div className="space-y-8">
      <PromotionForm full={live >= overview.maxActive} maxActive={overview.maxActive} rewardPoints={overview.rewardPoints} onCreated={load} />

      <AdSenseUnit slot="banner" />

      <section className="space-y-3">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <h2 className="text-4xl">Your promotions</h2>
          <p className="tabular text-sm text-muted">
            {live} of {overview.maxActive} live
          </p>
        </div>
        {overview.promotions.length === 0 ? (
          <p className="rounded-lg bg-surface px-5 py-8 text-center text-ink-soft ring-1 ring-line">
            You haven’t posted any yet. Paste your first link above.
          </p>
        ) : (
          <ul className="space-y-3">
            {overview.promotions.map((promotion, index) => (
              <Fragment key={promotion.id}>
                <PromotionRow
                  promotion={promotion}
                  onPause={() => void act(() => pausePromotion(promotion.id), "Promotion paused")}
                  onResume={() => void act(() => resumePromotion(promotion.id), "Promotion live again")}
                  onDelete={() => {
                    if (window.confirm(`Delete “${promotion.title}”?`)) {
                      void act(() => deletePromotion(promotion.id), "Promotion deleted");
                    }
                  }}
                />
                {/* Un anuncio entre la lista cada tres promociones */}
                {index % 3 === 1 && (
                  <li>
                    <AdSenseUnit slot="infeed" />
                  </li>
                )}
              </Fragment>
            ))}
          </ul>
        )}
      </section>

      <AdSenseUnit slot="banner" />
    </div>
  );
}

function PromotionForm({
  full,
  maxActive,
  rewardPoints,
  onCreated,
}: {
  full: boolean;
  maxActive: number;
  rewardPoints: number;
  onCreated: () => Promise<void>;
}) {
  const toast = useToast();
  const [title, setTitle] = useState("");
  const [url, setUrl] = useState("");
  const [description, setDescription] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors({});
    try {
      await createPromotion({ title, url, description });
      setTitle("");
      setUrl("");
      setDescription("");
      toast({ tone: "success", title: "Promotion live!", message: "It now shows in other people’s bonus links." });
      await onCreated();
    } catch (e) {
      if (e instanceof ApiError) {
        setFieldErrors(e.fieldErrors);
        setError(Object.keys(e.fieldErrors).length > 0 ? "Check the highlighted fields." : e.message);
      } else {
        setError("We couldn’t post your promotion.");
      }
    } finally {
      setSaving(false);
    }
  }

  const platform = platformOf(url);

  return (
    <section className="grid gap-6 rounded-lg bg-surface p-5 shadow-lift ring-2 ring-ink sm:p-7 md:grid-cols-[1.15fr_1fr]">
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <div>
          <h2 className="text-4xl">New promotion</h2>
          <p className="mt-1 text-sm text-ink-soft">
            Whoever watches it earns {formatPoints(rewardPoints)}. You can have up to {maxActive} live at once.
          </p>
        </div>
        {full && (
          <Alert tone="info" title={`You already have ${maxActive} promotions`}>
            Delete one to post another.
          </Alert>
        )}
        {error && <Alert tone="danger">{error}</Alert>}
        <TextField
          label="Link"
          placeholder="youtube.com/@yourchannel"
          inputMode="url"
          autoComplete="url"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          error={fieldErrors.url}
          hint="Your channel, profile or website. We detect YouTube, X, Instagram… automatically."
        />
        <TextField
          label="Title"
          placeholder="My easy recipes channel"
          value={title}
          maxChars={80}
          maxLength={80}
          onChange={(e) => setTitle(e.target.value)}
          error={fieldErrors.title}
        />
        <TextField
          label="Short description (optional)"
          placeholder="A new recipe every week, in under 15 minutes."
          value={description}
          maxChars={200}
          maxLength={200}
          onChange={(e) => setDescription(e.target.value)}
          error={fieldErrors.description}
        />
        <p className="text-xs leading-relaxed text-muted">
          Don’t ask for likes or follows in exchange for points: YouTube, X and Instagram forbid it and could penalise
          your account. People earn points for watching your link; if they like it, they’ll follow you.
        </p>
        <Button type="submit" size="lg" loading={saving} disabled={full}>
          Post for free
        </Button>
      </form>

      {/* Vista previa en directo: así la verán los demás en Bonus links */}
      <div>
        <p className="flex items-center gap-2 text-sm font-semibold text-ink-soft">
          <EyeIcon className="size-4" /> How others will see it
        </p>
        <div className="mt-3 overflow-hidden rounded-lg bg-surface ring-2 ring-ink">
          <div className={cn("relative grid aspect-[16/8] place-items-center", platformSurface(platform))}>
            <span className="font-display text-4xl font-black uppercase opacity-90">{PLATFORM_LABEL[platform]}</span>
            <span className="tabular absolute right-3 top-3 rounded-sm bg-gold px-2.5 py-1 text-sm font-extrabold text-ink ring-1 ring-ink">
              +{rewardPoints}
            </span>
          </div>
          <div className="space-y-1.5 p-4">
            <p className="truncate font-bold">{title.trim() || "Your promotion’s title"}</p>
            <p className="line-clamp-2 text-sm text-ink-soft">{description.trim() || "Your short description will appear here."}</p>
            <p className="truncate text-xs text-muted">{url.trim() ? prettyUrl(url.trim()) : "your-link.com"}</p>
            <span className="mt-2 inline-flex rounded-md bg-ink px-4 py-2 text-sm font-semibold text-white">Watch and earn</span>
          </div>
        </div>
        <p className="mt-3 text-xs leading-relaxed text-muted">
          After you post it we read your link to add its image and logo. If your website allows it, it shows inside
          AdArena; if not (like YouTube or Instagram), it opens in its own window.
        </p>
      </div>
    </section>
  );
}

function PromotionRow({
  promotion,
  onPause,
  onResume,
  onDelete,
}: {
  promotion: Promotion;
  onPause: () => void;
  onResume: () => void;
  onDelete: () => void;
}) {
  const status = STATUS[promotion.status];
  return (
    <li className="overflow-hidden rounded-lg bg-surface ring-2 ring-ink/15 sm:flex">
      <div className={cn("relative aspect-[16/8] shrink-0 sm:aspect-auto sm:w-48", !promotion.site.imageUrl && platformSurface(promotion.platform))}>
        {promotion.site.imageUrl ? (
          <Image src={apiUrl(promotion.site.imageUrl)} alt="" fill sizes="12rem" className="object-cover" />
        ) : (
          <span className="absolute inset-0 grid place-items-center font-display text-2xl font-black uppercase opacity-90">
            {promotion.platformLabel}
          </span>
        )}
      </div>
      <div className="min-w-0 flex-1 p-5">
        <div className="flex flex-wrap items-center gap-2">
          <PlatformBadge platform={promotion.platform} label={promotion.platformLabel} />
          <span className={cn("rounded-sm px-2 py-0.5 text-xs font-semibold", status.className)}>{status.text}</span>
          <ModeChip mode={promotion.site.mode} />
          <span className="ml-auto text-sm text-muted">since {formatDateTime(promotion.createdAt)}</span>
        </div>
        <div className="mt-3 flex items-center gap-2.5">
          <SiteAvatar
            name={promotion.site.siteName || promotion.title}
            iconUrl={promotion.site.iconUrl}
            color={promotion.site.themeColor ?? platformColor(promotion.platform)}
            className="size-8 rounded-sm text-sm"
          />
          <div className="min-w-0">
            <p className="truncate font-bold">{promotion.title}</p>
            <a href={promotion.url} target="_blank" rel="noopener noreferrer" className="block truncate text-sm text-brand hover:underline">
              {prettyUrl(promotion.url)}
            </a>
          </div>
        </div>
        {promotion.description && <p className="mt-2 text-sm text-ink-soft">{promotion.description}</p>}
        {promotion.status === "HIDDEN" && promotion.hiddenReason && (
          <Alert tone="danger" className="mt-3" title="We’ve hidden it">
            {promotion.hiddenReason}
          </Alert>
        )}
        <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2">
          <p className="text-sm">
            <strong className="tabular font-display text-xl">{promotion.visitsToday}</strong> <span className="text-muted">visits today</span>
          </p>
          <p className="text-sm">
            <strong className="tabular font-display text-xl">{promotion.totalVisits}</strong> <span className="text-muted">in total</span>
          </p>
          <div className="ml-auto flex gap-2">
            {promotion.status === "ACTIVE" && (
              <Button size="sm" variant="secondary" onClick={onPause}>
                Pause
              </Button>
            )}
            {promotion.status === "PAUSED" && (
              <Button size="sm" variant="secondary" onClick={onResume}>
                Resume
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={onDelete}>
              Delete
            </Button>
          </div>
        </div>
      </div>
    </li>
  );
}
