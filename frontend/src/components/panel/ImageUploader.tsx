"use client";

import Image from "next/image";
import { useRef, useState } from "react";
import { ApiError, uploadImage } from "@/lib/api";
import { apiUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import type { UploadedImage } from "@/lib/types";
import { UploadIcon } from "../icons";
import { Spinner } from "../ui/Spinner";

const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPT = "image/jpeg,image/png,image/webp,image/gif";

/**
 * Zona para subir la imagen del anuncio: clic o arrastrar y soltar. La imagen se sube al
 * momento; el servidor la optimiza y nos devuelve su id, que se guarda al pulsar "Guardar".
 */
export function ImageUploader({
  imageUrl,
  onUploaded,
  error,
}: {
  imageUrl: string | null;
  onUploaded: (image: UploadedImage) => void;
  error?: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  async function handleFile(file: File | undefined) {
    if (!file) return;
    setLocalError(null);
    if (file.size > MAX_BYTES) {
      setLocalError("The image is over 5 MB. Try a lighter one.");
      return;
    }
    setUploading(true);
    try {
      onUploaded(await uploadImage(file));
    } catch (e) {
      setLocalError(e instanceof ApiError ? e.message : "We couldn’t upload the image.");
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  const message = localError ?? error;

  return (
    <div className="space-y-1.5">
      <p className="text-sm font-semibold text-ink">Image or logo</p>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          void handleFile(e.dataTransfer.files[0]);
        }}
        className={cn(
          "group relative flex w-full items-center gap-4 overflow-hidden rounded-md border-2 border-dashed p-3 text-left transition",
          dragging ? "border-brand bg-brand-soft" : "border-line bg-surface hover:border-brand/50",
          message && "border-danger/60",
        )}
      >
        <div className="relative grid size-20 shrink-0 place-items-center overflow-hidden rounded-sm bg-canvas">
          {imageUrl ? (
            <Image src={apiUrl(imageUrl)} alt="Current ad image" fill sizes="80px" className="object-cover" />
          ) : (
            <UploadIcon className="size-7 text-muted" />
          )}
          {uploading && (
            <div className="absolute inset-0 grid place-items-center bg-white/70">
              <Spinner className="size-6 text-brand" />
            </div>
          )}
        </div>
        <div className="min-w-0">
          <p className="font-semibold text-ink">
            {uploading ? "Uploading and optimising…" : imageUrl ? "Change image" : "Upload an image"}
          </p>
          <p className="text-sm text-muted">Drag it here or click. JPG, PNG, WebP or GIF, up to 5 MB.</p>
          <p className="text-sm text-muted">Landscape works best (for example 1200 × 900).</p>
        </div>
      </button>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        className="sr-only"
        tabIndex={-1}
        onChange={(e) => void handleFile(e.target.files?.[0])}
      />
      {message && <p className="text-[13px] text-danger">{message}</p>}
    </div>
  );
}
