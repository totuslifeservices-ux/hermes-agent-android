"use client";

import { useLocale } from "@/components/features/locale-provider";
import { t } from "@/i18n/translations";
import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { UserPlus, HeartHandshake, Shield } from "lucide-react";
import { toast } from "sonner";

// Mock provider data — in production, fetch from API
const MOCK_PROVIDER: Record<string, { name: string; clinic: string; specialty: string }> = {
  demo: { name: "Dr. Jane Smith", clinic: "Maple Health Clinic", specialty: "Family Medicine" },
};

export default function JoinPage() {
  const { locale } = useLocale();
  const params = useParams();
  const router = useRouter();
  const slug = params.slug as string;
  const [patientName, setPatientName] = useState("");
  const [loading, setLoading] = useState(false);

  const provider = MOCK_PROVIDER[slug] || { name: "Your Provider", clinic: "Clinic", specialty: "Healthcare" };

  async function handleJoin(e: React.FormEvent) {
    e.preventDefault();

    if (!patientName.trim()) {
      toast.error("Please enter your name");
      return;
    }

    setLoading(true);
    try {
      const res = await fetch("/api/waiting-room/join", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          slug,
          patientName: patientName.trim(),
        }),
      });

      if (!res.ok) {
        toast.error(t(locale, "common", "error"));
        setLoading(false);
        return;
      }

      router.push(`/waiting/${slug}?name=${encodeURIComponent(patientName.trim())}`);
    } catch {
      toast.error(t(locale, "common", "error"));
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-teal-50 via-white to-amber-50 px-4 dark:from-teal-950/20 dark:via-zinc-950 dark:to-amber-950/20">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md"
      >
        {/* Provider branding */}
        <div className="mb-6 text-center">
          <div className="mx-auto mb-4 flex h-20 w-20 items-center justify-center rounded-full bg-primary/10">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary text-primary-foreground text-2xl font-bold">
              {provider.clinic.charAt(0)}
            </div>
          </div>
          <h1 className="text-2xl font-bold">{provider.clinic}</h1>
          <p className="mt-1 text-muted-foreground">{provider.name} &bull; {provider.specialty}</p>
        </div>

        <Card className="border-0 shadow-xl dark:shadow-teal-500/5">
          <CardHeader className="text-center">
            <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <HeartHandshake className="h-6 w-6" />
            </div>
            <CardTitle className="text-xl">{t(locale, "waitingRoom", "welcome")}</CardTitle>
            <CardDescription className="text-base">
              {t(locale, "waitingRoom", "message")}
            </CardDescription>
          </CardHeader>
          <form onSubmit={handleJoin}>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <label
                  htmlFor="patientName"
                  className="text-sm font-medium leading-none"
                >
                  Your Name
                </label>
                <Input
                  id="patientName"
                  type="text"
                  placeholder={t(locale, "waitingRoom", "namePlaceholder")}
                  value={patientName}
                  onChange={(e) => setPatientName(e.target.value)}
                  required
                  autoComplete="name"
                  className="h-12 text-base text-center"
                  autoFocus
                />
              </div>
            </CardContent>
            <CardFooter>
              <Button
                type="submit"
                className="w-full h-12 text-base font-medium"
                disabled={loading}
              >
                {loading ? (
                  <span className="flex items-center gap-2">
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                    {t(locale, "common", "loading")}
                  </span>
                ) : (
                  <span className="flex items-center gap-2">
                    <UserPlus className="h-5 w-5" />
                    {t(locale, "waitingRoom", "joinButton")}
                  </span>
                )}
              </Button>
            </CardFooter>
          </form>
        </Card>

        {/* Trust badges */}
        <div className="mt-8 flex items-center justify-center gap-6 text-xs text-muted-foreground">
          <div className="flex items-center gap-1.5">
            <Shield className="h-3.5 w-3.5" />
            PHIPA Compliant
          </div>
          <div className="flex items-center gap-1.5">
            <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
            End-to-End Encrypted
          </div>
          <div className="flex items-center gap-1.5">
            <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
            Canadian-Hosted
          </div>
        </div>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          {t(locale, "waitingRoom", "branding")}
        </p>
      </motion.div>
    </div>
  );
}
