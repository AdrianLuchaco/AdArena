"use client";

import { useEffect, useState } from "react";
import { ApiError, getMyAdProfile, saveMyAdProfile } from "@/lib/api";
import type { AdProfileInput } from "@/lib/types";
import { ShowcasePreview } from "./ShowcasePreview";
import { Alert } from "../ui/Alert";
import { Button, ButtonLink } from "../ui/Button";
import { TextArea, TextField } from "../ui/Field";
import { PageSpinner } from "../ui/Spinner";
import { ImageUploader } from "./ImageUploader";

const EMPTY: AdProfileInput = { companyName: "", websiteUrl: "", description: "", imageId: null };

type LoadState = "loading" | "ready" | "error";

/** Formulario del perfil de anuncio con vista previa en directo de cómo se verá en la portada. */
export function AdProfileEditor() {
  const [form, setForm] = useState<AdProfileInput>(EMPTY);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [isNew, setIsNew] = useState(true);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [savedCount, setSavedCount] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    let active = true;
    getMyAdProfile()
      .then((profile) => {
        if (!active) return;
        if (profile) {
          setForm({
            companyName: profile.companyName,
            websiteUrl: profile.websiteUrl,
            description: profile.description,
            imageId: profile.imageId,
          });
          setImageUrl(profile.imageUrl);
          setIsNew(false);
        }
        setLoadState("ready");
      })
      .catch(() => active && setLoadState("error"));
    return () => {
      active = false;
    };
  }, []);

  function update<K extends keyof AdProfileInput>(key: K, value: AdProfileInput[K]) {
    setForm((current) => ({ ...current, [key]: value }));
    setSaved(false);
    setFieldErrors((current) => ({ ...current, [key]: "" }));
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors({});
    try {
      const profile = await saveMyAdProfile(form);
      setForm((current) => ({ ...current, websiteUrl: profile.websiteUrl, companyName: profile.companyName, description: profile.description }));
      setIsNew(false);
      setSaved(true);
      setSavedCount((count) => count + 1);
    } catch (e) {
      if (e instanceof ApiError) {
        setFieldErrors(e.fieldErrors);
        setError(Object.keys(e.fieldErrors).length > 0 ? "Check the fields marked in red." : e.message);
      } else {
        setError("We couldn’t save your ad. Try again.");
      }
    } finally {
      setSaving(false);
    }
  }

  if (loadState === "loading") return <PageSpinner label="Loading your ad…" />;
  if (loadState === "error") {
    return <Alert tone="danger" title="We couldn’t load your ad">Reload the page to try again.</Alert>;
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
      <form onSubmit={handleSubmit} className="space-y-6 rounded-lg bg-surface p-5 shadow-lift ring-2 ring-ink sm:p-7" noValidate>
        <div>
          <h2 className="text-4xl">{isNew ? "Create your ad" : "Your ad"}</h2>
          <p className="mt-1 text-sm leading-relaxed text-ink-soft">
            It’s what everyone sees on the homepage if you win, and what shows in the Arena while you compete. You
            need it ready to bid, and you can change it whenever you want.
          </p>
        </div>

        {error && <Alert tone="danger">{error}</Alert>}
        {saved && (
          <Alert tone="success" title="Ad saved!">
            It’s ready to compete in the Arena.
          </Alert>
        )}

        <ImageUploader
          imageUrl={imageUrl}
          error={fieldErrors.imageId}
          onUploaded={(image) => {
            update("imageId", image.id);
            setImageUrl(image.url);
          }}
        />
        <TextField
          label="Your project or brand name"
          placeholder="Aurora Coffee"
          value={form.companyName}
          maxChars={80}
          maxLength={80}
          onChange={(e) => update("companyName", e.target.value)}
          error={fieldErrors.companyName}
        />
        <TextField
          label="Your website"
          placeholder="www.yourwebsite.com"
          inputMode="url"
          autoComplete="url"
          value={form.websiteUrl}
          onChange={(e) => update("websiteUrl", e.target.value)}
          error={fieldErrors.websiteUrl}
          hint="It must be secure (https). If you leave out https://, we add it for you."
        />
        <TextArea
          label="Short description"
          placeholder="Say in one or two sentences what you offer and why people should visit."
          value={form.description}
          maxChars={300}
          maxLength={300}
          onChange={(e) => update("description", e.target.value)}
          error={fieldErrors.description}
        />

        <div className="flex flex-wrap items-center gap-3 pt-1">
          <Button type="submit" size="lg" loading={saving}>
            {isNew ? "Create ad" : "Save changes"}
          </Button>
          {!isNew && (
            <ButtonLink href="/arena" variant="secondary" size="lg">
              Go to the Arena
            </ButtonLink>
          )}
        </div>
      </form>

      <div className="space-y-3 lg:sticky lg:top-24 lg:self-start">
        <p className="text-sm font-semibold text-muted">How it looks if you win</p>
        <ShowcasePreview
          enabled={!isNew}
          savedKey={savedCount}
          ad={{
            companyName: form.companyName,
            description: form.description,
            websiteUrl: form.websiteUrl ? (form.websiteUrl.includes("://") ? form.websiteUrl : `https://${form.websiteUrl}`) : "",
            imageUrl,
          }}
        />
      </div>
    </div>
  );
}
