import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Toaster } from "sonner";
import { ThemeProvider } from "@/components/features/theme-provider";
import { LocaleProvider } from "@/components/features/locale-provider";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Totus Telehealth — Secure Canadian Telehealth",
  description:
    "PHIPA-compliant telehealth platform built for Canadian healthcare providers. End-to-end encrypted video, no downloads for patients.",
  keywords: ["telehealth", "canadian telehealth", "PHIPA", "video call", "doxy.me alternative"],
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={inter.className}>
        <ThemeProvider>
          <LocaleProvider>
            {children}
            <Toaster position="top-right" richColors />
          </LocaleProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
