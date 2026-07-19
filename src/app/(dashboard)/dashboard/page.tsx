"use client";

import { useLocale } from "@/components/features/locale-provider";
import { useSession } from "next-auth/react";
import { t } from "@/i18n/translations";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import {
  Copy,
  Check,
  Users,
  Clock,
  Video,
  Calendar,
  UserPlus,
  UserX,
} from "lucide-react";
import { toast } from "sonner";

// ── Mock data helpers ──
function getRoomUrl(slug?: string | null) {
  if (typeof window === "undefined") return "";
  return `${window.location.protocol}//${window.location.host}/join/${slug || "demo"}`;
}

interface WaitingPatient {
  id: string;
  name: string;
  joinedAt: string;
}

interface SessionRecord {
  id: string;
  patientName: string;
  date: string;
  duration: string;
  status: "completed" | "missed" | "in-progress";
}

// Mock data for development — replace with real API calls
const MOCK_WAITING: WaitingPatient[] = [
  { id: "1", name: "Alice Johnson", joinedAt: new Date(Date.now() - 1000 * 60 * 3).toISOString() },
  { id: "2", name: "Bob Williams", joinedAt: new Date(Date.now() - 1000 * 60 * 7).toISOString() },
];

const MOCK_SESSIONS: SessionRecord[] = [
  { id: "s1", patientName: "Carol Davis", date: "2026-07-18", duration: "22 min", status: "completed" },
  { id: "s2", patientName: "David Lee", date: "2026-07-18", duration: "15 min", status: "completed" },
  { id: "s3", patientName: "Eve Martin", date: "2026-07-17", duration: "30 min", status: "completed" },
  { id: "s4", patientName: "Frank Brown", date: "2026-07-16", duration: "—", status: "missed" },
];

export default function DashboardPage() {
  const { locale } = useLocale();
  const { data: session, status } = useSession();
  const router = useRouter();
  const [copied, setCopied] = useState(false);
  const [waitingPatients, setWaitingPatients] = useState<WaitingPatient[]>(MOCK_WAITING);
  const [sessions] = useState<SessionRecord[]>(MOCK_SESSIONS);
  const [now, setNow] = useState(Date.now());

  // Tick clock for timing display
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(id);
  }, []);

  const slug = (session?.user as any)?.slug || "dr.smith";
  const providerName = session?.user?.name || "Dr. Smith";
  const roomUrl = getRoomUrl(slug);

  // Poll for waiting patients
  useEffect(() => {
    const interval = setInterval(async () => {
      try {
        const res = await fetch(`/api/waiting-room/${slug}/queue`);
        if (res.ok) {
          const data = await res.json();
          if (data.data?.patients) {
            setWaitingPatients(data.data.patients);
          }
        }
      } catch {
        // Use mock data silently
      }
    }, 5000);
    return () => clearInterval(interval);
  }, [slug]);

  // Redirect if unauthenticated
  useEffect(() => {
    if (status === "unauthenticated") {
      router.push("/login");
    }
  }, [status, router]);

  async function copyRoomLink() {
    try {
      await navigator.clipboard.writeText(roomUrl);
      setCopied(true);
      toast.success(t(locale, "dashboard", "copied"));
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error(t(locale, "common", "error"));
    }
  }

  async function handleAdmit(patientId: string) {
    try {
      await fetch(`/api/waiting-room/${slug}/admit`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ patientId }),
      });
      setWaitingPatients((prev) => prev.filter((p) => p.id !== patientId));
      toast.success("Patient admitted");
    } catch {
      toast.error(t(locale, "common", "error"));
    }
  }

  async function handleDecline(patientId: string) {
    try {
      await fetch(`/api/waiting-room/${slug}/decline`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ patientId }),
      });
      setWaitingPatients((prev) => prev.filter((p) => p.id !== patientId));
    } catch {
      toast.error(t(locale, "common", "error"));
    }
  }

  if (status === "loading") {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
      </div>
    );
  }

  const stats = [
    { label: t(locale, "dashboard", "activePatients"), value: waitingPatients.length, icon: Users, color: "text-primary", bg: "bg-primary/10" },
    { label: "Today's Sessions", value: sessions.filter(s => s.date === new Date().toISOString().split("T")[0]).length, icon: Video, color: "text-amber-500", bg: "bg-amber-500/10" },
    { label: "This Week", value: sessions.length, icon: Calendar, color: "text-blue-500", bg: "bg-blue-500/10" },
    { label: "Avg. Duration", value: "22 min", icon: Clock, color: "text-emerald-500", bg: "bg-emerald-500/10" },
  ];

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
      className="space-y-8"
    >
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">
          {t(locale, "dashboard", "title")}
        </h1>
        <p className="mt-1 text-muted-foreground">
          Welcome back, {providerName}
        </p>
      </div>

      {/* Room URL card */}
      <Card className="border-primary/20 bg-gradient-to-br from-primary/5 to-transparent">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg">{t(locale, "dashboard", "yourRoom")}</CardTitle>
          <CardDescription>{t(locale, "dashboard", "roomUrl")}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="flex-1 rounded-lg border bg-background px-4 py-3 font-mono text-sm break-all">
              {roomUrl}
            </div>
            <Button
              onClick={copyRoomLink}
              variant={copied ? "default" : "outline"}
              className="shrink-0 h-11"
            >
              {copied ? (
                <><Check className="mr-2 h-4 w-4" />{t(locale, "dashboard", "copied")}</>
              ) : (
                <><Copy className="mr-2 h-4 w-4" />{t(locale, "dashboard", "copyLink")}</>
              )}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Stats grid */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat, i) => {
          const Icon = stat.icon;
          return (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.05 }}
            >
              <Card>
                <CardContent className="p-6">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm text-muted-foreground">{stat.label}</p>
                      <p className="mt-1 text-3xl font-bold">{stat.value}</p>
                    </div>
                    <div className={cn("flex h-12 w-12 items-center justify-center rounded-xl", stat.bg)}>
                      <Icon className={cn("h-6 w-6", stat.color)} />
                    </div>
                  </div>
                </CardContent>
              </Card>
            </motion.div>
          );
        })}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Patient queue */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-lg">{t(locale, "dashboard", "activePatients")}</CardTitle>
                <CardDescription>
                  {waitingPatients.length > 0
                    ? `${waitingPatients.length} patient${waitingPatients.length > 1 ? "s" : ""} waiting`
                    : t(locale, "dashboard", "noPatients")}
                </CardDescription>
              </div>
              {waitingPatients.length > 0 && (
                <Badge variant="default" className="bg-primary text-primary-foreground">
                  {waitingPatients.length}
                </Badge>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {waitingPatients.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <UserPlus className="h-10 w-10 text-muted-foreground/50 mb-3" />
                <p className="text-sm text-muted-foreground">{t(locale, "dashboard", "noPatients")}</p>
                <p className="mt-1 text-xs text-muted-foreground/70">
                  Share your room link for patients to join
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {waitingPatients.map((patient) => (
                  <div
                    key={patient.id}
                    className="flex items-center justify-between rounded-lg border p-3 transition-colors hover:bg-muted/50"
                  >
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary font-medium text-sm">
                        {patient.name.split(" ").map(n => n[0]).join("").slice(0, 2).toUpperCase()}
                      </div>
                      <div>
                        <p className="text-sm font-medium">{patient.name}</p>
                        <p className="text-xs text-muted-foreground">
                          Waiting {Math.floor((now - new Date(patient.joinedAt).getTime()) / 60000)} min
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button
                        size="sm"
                        onClick={() => handleAdmit(patient.id)}
                        className="h-8 bg-primary text-primary-foreground hover:bg-primary/90"
                      >
                        <UserPlus className="mr-1 h-3.5 w-3.5" />
                        {t(locale, "dashboard", "admit")}
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => handleDecline(patient.id)}
                        className="h-8 text-muted-foreground hover:text-destructive"
                      >
                        <UserX className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Session history */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t(locale, "dashboard", "sessionHistory")}</CardTitle>
            <CardDescription>Your most recent patient sessions</CardDescription>
          </CardHeader>
          <CardContent>
            {sessions.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <Video className="h-10 w-10 text-muted-foreground/50 mb-3" />
                <p className="text-sm text-muted-foreground">{t(locale, "dashboard", "noSessions")}</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left text-muted-foreground">
                      <th className="pb-3 pr-4 font-medium">Patient</th>
                      <th className="pb-3 pr-4 font-medium">Date</th>
                      <th className="pb-3 pr-4 font-medium">Duration</th>
                      <th className="pb-3 text-right font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sessions.map((s) => (
                      <tr key={s.id} className="border-b last:border-0">
                        <td className="py-3 pr-4">
                          <div className="flex items-center gap-2">
                            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-muted text-xs font-medium">
                              {s.patientName.split(" ").map(n => n[0]).join("")}
                            </div>
                            <span className="font-medium">{s.patientName}</span>
                          </div>
                        </td>
                        <td className="py-3 pr-4 text-muted-foreground">{s.date}</td>
                        <td className="py-3 pr-4 text-muted-foreground">{s.duration}</td>
                        <td className="py-3 text-right">
                          <Badge
                            variant={
                              s.status === "completed" ? "default" :
                              s.status === "missed" ? "destructive" :
                              "secondary"
                            }
                            className="text-xs"
                          >
                            {s.status}
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </motion.div>
  );
}
