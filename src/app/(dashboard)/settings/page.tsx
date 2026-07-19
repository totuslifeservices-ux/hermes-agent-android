"use client";

import { useLocale } from "@/components/features/locale-provider";
import { useTheme } from "next-themes";
import { t } from "@/i18n/translations";
import { useState } from "react";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
  Select,
  SelectItem,
} from "@/components/ui/select";
import {
  User,
  Palette,
  Shield,
  Sun,
  Moon,
  Save,
  Languages,
  Lock,
  Key,
  Clock,
  Smartphone,
} from "lucide-react";
import { toast } from "sonner";

export default function SettingsPage() {
  const { locale, setLocale } = useLocale();
  const { theme, setTheme } = useTheme();
  const [saving, setSaving] = useState(false);
  const [mfaEnabled, setMfaEnabled] = useState(false);
  const [autoLockMinutes, setAutoLockMinutes] = useState("5");
  const [profile, setProfile] = useState({
    name: "",
    email: "",
    clinicName: "",
    specialty: "",
  });

  async function handleSave() {
    setSaving(true);
    // Simulate save
    await new Promise((r) => setTimeout(r, 800));
    toast.success(t(locale, "common", "save"));
    setSaving(false);
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
      className="mx-auto max-w-3xl space-y-8"
    >
      <div>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">
          {t(locale, "settings", "title")}
        </h1>
        <p className="mt-1 text-muted-foreground">
          Manage your account settings and preferences
        </p>
      </div>

      {/* ── Profile Section ── */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <User className="h-5 w-5" />
            </div>
            <div>
              <CardTitle>{t(locale, "settings", "profile")}</CardTitle>
              <CardDescription>Update your personal information</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="name">{t(locale, "auth", "name")}</Label>
              <Input
                id="name"
                value={profile.name}
                onChange={(e) => setProfile((p) => ({ ...p, name: e.target.value }))}
                placeholder="Dr. Jane Smith"
                className="h-11"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">{t(locale, "auth", "email")}</Label>
              <Input
                id="email"
                type="email"
                value={profile.email}
                onChange={(e) => setProfile((p) => ({ ...p, email: e.target.value }))}
                placeholder="jane@clinic.ca"
                className="h-11"
              />
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="clinicName">{t(locale, "auth", "clinicName")}</Label>
              <Input
                id="clinicName"
                value={profile.clinicName}
                onChange={(e) => setProfile((p) => ({ ...p, clinicName: e.target.value }))}
                placeholder="Maple Health Clinic"
                className="h-11"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="specialty">{t(locale, "auth", "specialty")}</Label>
              <Input
                id="specialty"
                value={profile.specialty}
                onChange={(e) => setProfile((p) => ({ ...p, specialty: e.target.value }))}
                placeholder="Family Medicine"
                className="h-11"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* ── Appearance Section ── */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/10 text-amber-500">
              <Palette className="h-5 w-5" />
            </div>
            <div>
              <CardTitle>{t(locale, "settings", "appearance")}</CardTitle>
              <CardDescription>Customize your visual experience</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Dark mode */}
          <div className="flex items-center justify-between rounded-lg border p-4">
            <div className="flex items-start gap-3">
              {theme === "dark" ? (
                <Moon className="mt-0.5 h-5 w-5 text-primary" />
              ) : (
                <Sun className="mt-0.5 h-5 w-5 text-amber-500" />
              )}
              <div>
                <p className="text-sm font-medium">{t(locale, "settings", "darkMode")}</p>
                <p className="text-xs text-muted-foreground">
                  {theme === "dark" ? "Dark mode is active" : "Light mode is active"}
                </p>
              </div>
            </div>
            <Switch
              checked={theme === "dark"}
              onCheckedChange={(checked: boolean) => setTheme(checked ? "dark" : "light")}
            />
          </div>

          {/* Language */}
          <div className="flex items-center justify-between rounded-lg border p-4">
            <div className="flex items-start gap-3">
              <Languages className="mt-0.5 h-5 w-5 text-blue-500" />
              <div>
                <p className="text-sm font-medium">{t(locale, "settings", "language")}</p>
                <p className="text-xs text-muted-foreground">
                  {locale === "en" ? "English" : "Français"}
                </p>
              </div>
            </div>
            <Select
              value={locale}
              onValueChange={(val: unknown) => setLocale(val as "en" | "fr")}
            >
              <SelectItem value="en">{t(locale, "settings", "english")}</SelectItem>
              <SelectItem value="fr">{t(locale, "settings", "french")}</SelectItem>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* ── Security Section ── */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-red-500/10 text-red-500">
              <Shield className="h-5 w-5" />
            </div>
            <div>
              <CardTitle>{t(locale, "settings", "security")}</CardTitle>
              <CardDescription>Manage your security preferences</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* MFA */}
          <div className="flex items-center justify-between rounded-lg border p-4">
            <div className="flex items-start gap-3">
              <Smartphone className="mt-0.5 h-5 w-5 text-primary" />
              <div>
                <p className="text-sm font-medium">{t(locale, "settings", "mfa")}</p>
                <p className="text-xs text-muted-foreground">
                  Add an extra layer of security to your account
                </p>
              </div>
            </div>
            <Switch
              checked={mfaEnabled}
              onCheckedChange={setMfaEnabled}
            />
          </div>

          {/* Change password */}
          <div className="rounded-lg border p-4">
            <div className="flex items-start gap-3">
              <Key className="mt-0.5 h-5 w-5 text-orange-500 shrink-0" />
              <div className="flex-1">
                <p className="text-sm font-medium">{t(locale, "settings", "changePassword")}</p>
                <p className="text-xs text-muted-foreground mb-3">
                  Update your password regularly to keep your account secure
                </p>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Input type="password" placeholder="Current password" className="h-10 text-sm" />
                  <Input type="password" placeholder="New password" className="h-10 text-sm" />
                </div>
              </div>
            </div>
          </div>

          {/* Auto-lock timer */}
          <div className="flex items-center justify-between rounded-lg border p-4">
            <div className="flex items-start gap-3">
              <Clock className="mt-0.5 h-5 w-5 text-purple-500" />
              <div>
                <p className="text-sm font-medium">{t(locale, "settings", "autoLock")}</p>
                <p className="text-xs text-muted-foreground">
                  Automatically lock your session after inactivity
                </p>
              </div>
            </div>
            <Select value={autoLockMinutes} onValueChange={(val: unknown) => setAutoLockMinutes(val as string)}>
              <SelectItem value="1">1 min</SelectItem>
              <SelectItem value="5">5 min</SelectItem>
              <SelectItem value="10">10 min</SelectItem>
              <SelectItem value="15">15 min</SelectItem>
              <SelectItem value="30">30 min</SelectItem>
              <SelectItem value="never">Never</SelectItem>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* Save button */}
      <div className="flex justify-end">
        <Button
          onClick={handleSave}
          disabled={saving}
          size="lg"
          className="h-11 px-8 text-base"
        >
          {saving ? (
            <span className="flex items-center gap-2">
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
              {t(locale, "common", "loading")}
            </span>
          ) : (
            <span className="flex items-center gap-2">
              <Save className="h-4 w-4" />
              {t(locale, "settings", "saveChanges")}
            </span>
          )}
        </Button>
      </div>
    </motion.div>
  );
}
