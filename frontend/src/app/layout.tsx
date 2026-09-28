import type { Metadata, Viewport } from "next";
import { Archivo, Big_Shoulders } from "next/font/google";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { ToastProvider } from "@/components/ui/Toaster";
import { ArenaProvider } from "@/lib/arena-context";
import { AuthProvider } from "@/lib/auth-context";
import "./globals.css";

// Tipografías: Archivo para el texto y Big Shoulders (rotulación de estadio) para titulares y cifras
const body = Archivo({ variable: "--font-body", subsets: ["latin"] });
const heading = Big_Shoulders({ variable: "--font-heading", subsets: ["latin"], axes: ["opsz"] });

export const metadata: Metadata = {
  title: {
    default: "AdArena · Win the homepage, every day",
    template: "%s · AdArena",
  },
  description:
    "Every day, projects bid for our homepage. Whoever bids the most by midnight takes over the whole homepage for 24 hours.",
};

export const viewport: Viewport = {
  themeColor: "#e9ecf1",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" data-scroll-behavior="smooth" className={`${body.variable} ${heading.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col">
        <ToastProvider>
          <AuthProvider>
            <ArenaProvider>
              <SiteHeader />
              <main className="flex flex-1 flex-col">{children}</main>
              <SiteFooter />
            </ArenaProvider>
          </AuthProvider>
        </ToastProvider>
      </body>
    </html>
  );
}
