import { ImageResponse } from "next/og";
import { SITE_NAME } from "@/lib/site";

/**
 * La imagen que se ve al compartir cualquier página de la web en X, WhatsApp, LinkedIn, Slack…
 * (y la que puede mostrar Google). Se genera una vez al compilar, con los colores de la web.
 */
export const alt = `${SITE_NAME}: win the homepage for your startup, every day`;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          padding: "72px 80px",
          background: "#2437ff",
          color: "#ffffff",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 24 }}>
          <svg width="88" height="88" viewBox="0 0 32 32">
            <rect width="32" height="32" rx="5" fill="#0c0f1f" />
            <path d="M6 21V10.5l5.5 5L16 7l4.5 8.5 5.5-5V21z" fill="#ffd23a" />
            <rect x="6" y="23" width="20" height="3" fill="#ffffff" />
          </svg>
          <div style={{ fontSize: 60, fontWeight: 800, letterSpacing: 2 }}>{SITE_NAME.toUpperCase()}</div>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          <div style={{ fontSize: 84, fontWeight: 800, lineHeight: 1, textTransform: "uppercase" }}>
            Win the homepage
          </div>
          <div style={{ fontSize: 84, fontWeight: 800, lineHeight: 1, color: "#ffd23a", textTransform: "uppercase" }}>
            for your startup
          </div>
          <div style={{ fontSize: 34, color: "rgba(255,255,255,0.88)", marginTop: 12 }}>
            One project takes over the homepage every day. Free: no money, just points.
          </div>
        </div>
      </div>
    ),
    size,
  );
}
