"use client";

import { useLocale } from "@/components/features/locale-provider";
import { useTheme } from "next-themes";
import { t } from "@/i18n/translations";
import { useState, useEffect, type ReactNode } from "react";
import Link from "next/link";
import { motion, useScroll, useTransform } from "framer-motion";
import { Button } from "@/components/ui/button";
import {
  Shield,
  Video,
  Globe,
  Languages,
  Clock,
  HeartHandshake,
  ChevronRight,
  Menu,
  X,
  Sun,
  Moon,
  ArrowRight,
  Server,
  Download,
  MessageCircle,
  Lock,
} from "lucide-react";

const fadeInUp = {
  hidden: { opacity: 0, y: 30 },
  visible: (i = 0) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, delay: i * 0.1, ease: [0.25, 0.1, 0.25, 1] as const },
  }),
};

function LanguageSwitcher() {
  const { locale, setLocale } = useLocale();
  return (
    <button
      onClick={() => setLocale(locale === "en" ? "fr" : "en")}
      className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
    >
      <Languages className="h-4 w-4" />
      {locale === "en" ? "FR" : "EN"}
    </button>
  );
}

function DarkModeToggle() {
  const { theme, setTheme } = useTheme();
  return (
    <button
      onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
      className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
      aria-label="Toggle dark mode"
    >
      {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </button>
  );
}

function FeatureCard({ icon, title, description }: { icon: ReactNode; title: string; description: string }) {
  return (
    <motion.div
      variants={fadeInUp}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: "-50px" }}
      className="group relative rounded-2xl border bg-card p-6 transition-all duration-300 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-1"
    >
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
        {icon}
      </div>
      <h3 className="mb-2 text-lg font-semibold">{title}</h3>
      <p className="text-sm leading-relaxed text-muted-foreground">{description}</p>
    </motion.div>
  );
}

export default function LandingPage() {
  const { locale } = useLocale();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { scrollYProgress } = useScroll();
  const headerBg = useTransform(scrollYProgress, [0, 0.05], ["bg-transparent", "bg-background/80 backdrop-blur-xl"]);

  const features = [
    {
      icon: <Lock className="h-6 w-6" />,
      title: "End-to-End Encrypted",
      description: "PHIPA-compliant with full end-to-end encryption. Your patients' data stays private, always.",
    },
    {
      icon: <Download className="h-6 w-6" />,
      title: "No Downloads Required",
      description: "Patients join from any browser with one click. No apps, no accounts, no friction.",
    },
    {
      icon: <Server className="h-6 w-6" />,
      title: "100% Canadian-Hosted",
      description: "All data stays in Canada on Canadian servers. Fully compliant with provincial privacy laws.",
    },
    {
      icon: <Languages className="h-6 w-6" />,
      title: "Bilingual by Default",
      description: "Full English and French support. Switch languages instantly — patients see your room in their language.",
    },
    {
      icon: <MessageCircle className="h-6 w-6" />,
      title: "Built for All Ages",
      description: "Large buttons, clear text, and simple flows. Designed for elderly patients and non-tech-savvy users.",
    },
    {
      icon: <Globe className="h-6 w-6" />,
      title: "Your Own Waiting Room",
      description: "Get a permanent, unique link (totus.ca/dr.smith) to share with patients. Always available.",
    },
  ];

  const steps = [
    { number: "01", title: "Create Your Room", description: "Sign up and get your permanent waiting room link in under 2 minutes." },
    { number: "02", title: "Share the Link", description: "Send your unique Totus link to patients via email, SMS, or your website." },
    { number: "03", title: "Patient Joins", description: "Patient clicks the link, enters their name, and joins the waiting room. No account needed." },
    { number: "04", title: "Start Your Visit", description: "Admit patients from your dashboard and start a secure video call instantly." },
  ];

  return (
    <div className="flex min-h-screen flex-col">
      {/* Header */}
      <motion.header
        style={{ backgroundColor: headerBg, borderColor: useTransform(scrollYProgress, [0, 0.05], ["transparent", "var(--border)"]) }}
        className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between border-b px-4 py-3 transition-colors sm:px-8"
      >
        <Link href="/" className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground text-lg font-bold">
            T
          </div>
          <span className="text-lg font-bold tracking-tight">{t(locale, "common", "appName")}</span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden items-center gap-2 md:flex">
          <LanguageSwitcher />
          <DarkModeToggle />
          <Link href="/login">
            <Button variant="ghost" size="sm">
              {t(locale, "auth", "login")}
            </Button>
          </Link>
          <Link href="/register">
            <Button size="sm" className="bg-primary text-primary-foreground hover:bg-primary/90">
              {t(locale, "auth", "register")}
            </Button>
          </Link>
        </nav>

        {/* Mobile menu button */}
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="flex items-center rounded-lg p-2 text-muted-foreground hover:bg-muted md:hidden"
          aria-label="Toggle menu"
        >
          {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </motion.header>

      {/* Mobile menu */}
      {mobileMenuOpen && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="fixed top-16 left-0 right-0 z-40 border-b bg-background px-4 pb-4 pt-2 md:hidden"
        >
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2 border-b pb-3">
              <LanguageSwitcher />
              <DarkModeToggle />
            </div>
            <Link href="/login" className="w-full" onClick={() => setMobileMenuOpen(false)}>
              <Button variant="outline" className="w-full">
                {t(locale, "auth", "login")}
              </Button>
            </Link>
            <Link href="/register" className="w-full" onClick={() => setMobileMenuOpen(false)}>
              <Button className="w-full bg-primary text-primary-foreground hover:bg-primary/90">
                {t(locale, "auth", "register")}
              </Button>
            </Link>
          </div>
        </motion.div>
      )}

      <main className="flex-1">
        {/* ── Hero Section ── */}
        <section className="relative overflow-hidden pt-28 pb-20 sm:pt-36 sm:pb-28">
          {/* Background decorations */}
          <div className="absolute inset-0 -z-10 overflow-hidden">
            <div className="absolute -top-40 -right-40 h-[500px] w-[500px] rounded-full bg-primary/5 blur-3xl" />
            <div className="absolute -bottom-40 -left-40 h-[400px] w-[400px] rounded-full bg-amber-500/5 blur-3xl" />
            <div className="absolute top-1/2 left-1/3 h-[300px] w-[300px] rounded-full bg-primary/5 blur-3xl" />
          </div>

          <div className="mx-auto max-w-6xl px-4 sm:px-8">
            <div className="mx-auto max-w-3xl text-center">
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6 }}
              >
                <div className="mb-6 inline-flex items-center gap-2 rounded-full border bg-muted/50 px-4 py-1.5 text-sm font-medium text-muted-foreground">
                  <Shield className="h-4 w-4 text-primary" />
                  PHIPA-Compliant &bull; Canadian-Hosted
                </div>
              </motion.div>

              <motion.h1
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.1 }}
                className="text-4xl font-bold leading-tight tracking-tight sm:text-5xl md:text-6xl lg:text-7xl"
              >
                Secure Canadian{" "}
                <span className="bg-gradient-to-r from-primary to-teal-400 bg-clip-text text-transparent">
                  Telehealth
                </span>
              </motion.h1>

              <motion.p
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.2 }}
                className="mt-6 text-lg leading-relaxed text-muted-foreground sm:text-xl max-w-2xl mx-auto"
              >
                The simplest, most secure way to see patients online. 
                No downloads, no complicated setup — just you, your patient, 
                and a crystal-clear video call hosted entirely in Canada.
              </motion.p>

              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.3 }}
                className="mt-10 flex flex-col items-center gap-4 sm:flex-row sm:justify-center"
              >
                <Link href="/register">
                  <Button
                    size="lg"
                    className="h-13 rounded-xl px-8 text-base font-medium shadow-lg shadow-primary/25 transition-all hover:shadow-xl hover:shadow-primary/30 hover:scale-105"
                  >
                    For Providers
                    <ArrowRight className="ml-2 h-4 w-4" />
                  </Button>
                </Link>
                <Link href="/join/demo">
                  <Button
                    variant="outline"
                    size="lg"
                    className="h-13 rounded-xl px-8 text-base font-medium transition-all hover:bg-muted hover:scale-105"
                  >
                    <HeartHandshake className="mr-2 h-4 w-4" />
                    Join as Patient
                  </Button>
                </Link>
              </motion.div>

              <motion.p
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.6, delay: 0.4 }}
                className="mt-6 text-sm text-muted-foreground"
              >
                No credit card &bull; Free for providers &bull; 30-second setup
              </motion.p>
            </div>
          </div>
        </section>

        {/* ── Features Section ── */}
        <section className="border-t bg-muted/30 py-20 sm:py-28">
          <div className="mx-auto max-w-6xl px-4 sm:px-8">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView="visible"
              viewport={{ once: true }}
              className="mb-16 text-center"
            >
              <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
                Everything you need for virtual care
              </h2>
              <p className="mt-4 text-lg text-muted-foreground">
                Built by healthcare providers, for healthcare providers.
              </p>
            </motion.div>

            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {features.map((feature, i) => (
                <FeatureCard key={i} {...feature} />
              ))}
            </div>
          </div>
        </section>

        {/* ── How It Works Section ── */}
        <section className="py-20 sm:py-28">
          <div className="mx-auto max-w-6xl px-4 sm:px-8">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView="visible"
              viewport={{ once: true }}
              className="mb-16 text-center"
            >
              <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
                How it works
              </h2>
              <p className="mt-4 text-lg text-muted-foreground">
                Get started in under 2 minutes.
              </p>
            </motion.div>

            <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
              {steps.map((step, i) => (
                <motion.div
                  key={i}
                  variants={fadeInUp}
                  initial="hidden"
                  whileInView="visible"
                  viewport={{ once: true }}
                  custom={i}
                  className="relative"
                >
                  {i < steps.length - 1 && (
                    <div className="absolute top-6 left-14 hidden h-px w-[calc(100%-3.5rem)] bg-gradient-to-r from-primary/40 to-transparent lg:block" />
                  )}
                  <div className="relative">
                    <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground text-lg font-bold">
                      {step.number}
                    </div>
                    <h3 className="mb-2 text-lg font-semibold">{step.title}</h3>
                    <p className="text-sm leading-relaxed text-muted-foreground">{step.description}</p>
                  </div>
                </motion.div>
              ))}
            </div>

            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView="visible"
              viewport={{ once: true }}
              className="mt-16 text-center"
            >
              <Link href="/register">
                <Button size="lg" className="h-13 rounded-xl px-8 text-base font-medium shadow-lg shadow-primary/25">
                  Get Started Free
                  <ChevronRight className="ml-1 h-4 w-4" />
                </Button>
              </Link>
            </motion.div>
          </div>
        </section>

        {/* ── CTA Section ── */}
        <section className="border-t bg-gradient-to-br from-primary/5 via-transparent to-amber-500/5 py-20 sm:py-28">
          <div className="mx-auto max-w-4xl px-4 text-center sm:px-8">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5 }}
            >
              <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
                Ready to transform your practice?
              </h2>
              <p className="mt-4 text-lg text-muted-foreground">
                Join hundreds of Canadian healthcare providers using Totus Telehealth.
              </p>
              <div className="mt-10 flex flex-col items-center justify-center gap-4 sm:flex-row">
                <Link href="/register">
                  <Button
                    size="lg"
                    className="h-13 rounded-xl px-8 text-base font-medium shadow-lg shadow-primary/25 transition-all hover:shadow-xl hover:scale-105"
                  >
                    Create Your Room
                    <ArrowRight className="ml-2 h-4 w-4" />
                  </Button>
                </Link>
                <Link href="/join/demo">
                  <Button
                    variant="outline"
                    size="lg"
                    className="h-13 rounded-xl px-8 text-base font-medium transition-all hover:bg-muted hover:scale-105"
                  >
                    See a Demo
                  </Button>
                </Link>
              </div>
            </motion.div>
          </div>
        </section>
      </main>

      {/* ── Footer ── */}
      <footer className="border-t py-12">
        <div className="mx-auto max-w-6xl px-4 sm:px-8">
          <div className="flex flex-col items-center justify-between gap-6 sm:flex-row">
            <div className="flex items-center gap-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground text-sm font-bold">
                T
              </div>
              <span className="text-sm font-semibold">{t(locale, "common", "appName")}</span>
            </div>
            <nav className="flex flex-wrap items-center justify-center gap-6 text-sm text-muted-foreground">
              <Link href="/privacy" className="hover:text-foreground transition-colors">Privacy Policy</Link>
              <Link href="/terms" className="hover:text-foreground transition-colors">Terms of Service</Link>
              <Link href="/security" className="hover:text-foreground transition-colors">Security</Link>
              <Link href="/contact" className="hover:text-foreground transition-colors">Contact</Link>
            </nav>
            <p className="text-sm text-muted-foreground">
              &copy; {new Date().getFullYear()} Totus Telehealth. All rights reserved.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
