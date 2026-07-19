"use client";

import { useLocale } from "@/components/features/locale-provider";
import { t } from "@/i18n/translations";
import { useState, useEffect } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { motion } from "framer-motion";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Shield, Clock, Loader2 } from "lucide-react";

// Mock provider data
const MOCK_PROVIDER: Record<string, { name: string; clinic: string }> = {
  demo: { name: "Dr. Jane Smith", clinic: "Maple Health Clinic" },
};

export default function WaitingRoomPage() {
  const { locale } = useLocale();
  const params = useParams();
  const searchParams = useSearchParams();
  const slug = params.slug as string;
  const patientName = searchParams.get("name") || "Patient";
  const [position, setPosition] = useState(1);
  const [status, setStatus] = useState<"waiting" | "connecting" | "admitted">("waiting");
  const [elapsed, setElapsed] = useState(0);

  const provider = MOCK_PROVIDER[slug] || { name: "Your Provider", clinic: "Clinic" };

  // Elapsed time counter
  useEffect(() => {
    const interval = setInterval(() => {
      setElapsed((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  // Poll for status changes
  useEffect(() => {
    const interval = setInterval(async () => {
      try {
        const res = await fetch(`/api/waiting-room/${slug}/status?name=${encodeURIComponent(patientName)}`);
        if (res.ok) {
          const data = await res.json();
          if (data.data?.status === "admitted" || data.data?.status === "connecting") {
            setStatus(data.data.status);
          }
          if (data.data?.position !== undefined) {
            setPosition(data.data.position);
          }
        }
      } catch {
        // Silently use mock
      }
    }, 3000);
    return () => clearInterval(interval);
  }, [slug, patientName]);

  // Format elapsed time
  const minutes = Math.floor(elapsed / 60);
  const seconds = elapsed % 60;
  const timeStr = `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-teal-50 via-white to-amber-50 px-4 dark:from-teal-950/20 dark:via-zinc-950 dark:to-amber-950/20">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md text-center"
      >
        {/* Clinic branding */}
        <div className="mb-8">
          <div className="mx-auto mb-4 flex h-24 w-24 items-center justify-center rounded-full bg-primary/10">
            <div className="flex h-20 w-20 items-center justify-center rounded-full bg-primary text-primary-foreground text-3xl font-bold">
              {provider.clinic.charAt(0)}
            </div>
          </div>
          <h1 className="text-2xl font-bold">{provider.clinic}</h1>
          <p className="mt-1 text-muted-foreground">{provider.name}</p>
        </div>

        <Card className="border-0 shadow-xl dark:shadow-teal-500/5">
          <CardContent className="pt-8 pb-8">
            {status === "waiting" && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="flex flex-col items-center"
              >
                {/* Animated pulse / ripple */}
                <div className="relative mb-6">
                  <div className="absolute inset-0 flex items-center justify-center">
                    <div className="h-24 w-24 animate-pulse-ripple rounded-full bg-primary/20" />
                  </div>
                  <div className="absolute inset-0 flex items-center justify-center">
                    <div className="h-20 w-20 animate-pulse-ripple rounded-full bg-primary/15" style={{ animationDelay: "0.5s" }} />
                  </div>
                  <div className="relative flex h-16 w-16 items-center justify-center rounded-full bg-primary text-primary-foreground">
                    <Clock className="h-8 w-8" />
                  </div>
                </div>

                <h2 className="text-xl font-semibold mb-2">
                  {t(locale, "waitingRoom", "waiting")}
                </h2>
                <p className="text-muted-foreground mb-4">
                  {t(locale, "waitingRoom", "joined")}, {patientName}
                </p>

                {/* Queue position */}
                <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-sm font-medium text-primary">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Position in queue: #{position}
                </div>

                {/* Elapsed time */}
                <p className="text-2xl font-mono font-bold text-muted-foreground">
                  {timeStr}
                </p>

                {/* Reassuring message */}
                <div className="mt-6 rounded-lg bg-muted/50 p-4 text-sm text-muted-foreground">
                  <Shield className="mx-auto mb-2 h-5 w-5 text-primary" />
                  <p>
                    Your connection is secure and encrypted. Your provider will admit
                    you as soon as they are available. Please stay on this page.
                  </p>
                </div>
              </motion.div>
            )}

            {status === "connecting" && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="flex flex-col items-center"
              >
                <div className="relative mb-6">
                  <div className="flex h-16 w-16 items-center justify-center rounded-full bg-amber-500 text-white">
                    <Loader2 className="h-8 w-8 animate-spin" />
                  </div>
                </div>
                <h2 className="text-xl font-semibold mb-2">Connecting...</h2>
                <p className="text-muted-foreground">
                  Your provider is connecting to you. Please wait.
                </p>
              </motion.div>
            )}

            {status === "admitted" && (
              <motion.div
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                className="flex flex-col items-center"
              >
                <div className="relative mb-6">
                  <div className="flex h-16 w-16 items-center justify-center rounded-full bg-green-500 text-white">
                    <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  </div>
                </div>
                <h2 className="text-xl font-semibold mb-2">You&apos;re being connected!</h2>
                <p className="text-muted-foreground">
                  Your video call will start momentarily.
                </p>
              </motion.div>
            )}
          </CardContent>
        </Card>

        {/* Branding */}
        <p className="mt-8 text-sm text-muted-foreground">
          {t(locale, "waitingRoom", "branding")}
        </p>
      </motion.div>
    </div>
  );
}
