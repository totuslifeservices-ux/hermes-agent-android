"use client";

import { useLocale } from "@/components/features/locale-provider";
import { t } from "@/i18n/translations";
import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectItem } from "@/components/ui/select";
import { UserPlus, Eye, EyeOff } from "lucide-react";
import { toast } from "sonner";

const SPECIALTIES = [
  "General Practice",
  "Cardiology",
  "Dermatology",
  "Endocrinology",
  "Family Medicine",
  "Gastroenterology",
  "Internal Medicine",
  "Neurology",
  "Obstetrics & Gynecology",
  "Ophthalmology",
  "Orthopedics",
  "Pediatrics",
  "Psychiatry",
  "Radiology",
  "Surgery",
  "Urology",
  "Other",
];

export default function RegisterPage() {
  const { locale } = useLocale();
  const router = useRouter();
  const [formData, setFormData] = useState({
    name: "",
    email: "",
    password: "",
    confirmPassword: "",
    clinicName: "",
    specialty: "",
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  function updateField(field: string, value: string) {
    setFormData((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (formData.password !== formData.confirmPassword) {
      toast.error("Passwords do not match");
      return;
    }

    if (formData.password.length < 8) {
      toast.error("Password must be at least 8 characters");
      return;
    }

    setLoading(true);
    try {
      const res = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: formData.name,
          email: formData.email,
          password: formData.password,
          clinicName: formData.clinicName,
          specialty: formData.specialty,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        toast.error(data.error || t(locale, "common", "error"));
        return;
      }

      toast.success("Account created successfully!");
      router.push("/login");
    } catch {
      toast.error(t(locale, "common", "error"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
    >
      <Card className="border-0 shadow-xl dark:shadow-teal-500/5">
        <CardHeader className="space-y-1 text-center">
          <Link href="/" className="mx-auto mb-2">
            <div className="flex items-center justify-center gap-2">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground text-lg font-bold">
                T
              </div>
              <span className="text-xl font-bold tracking-tight">{t(locale, "common", "appName")}</span>
            </div>
          </Link>
          <CardTitle className="text-2xl font-bold">{t(locale, "auth", "register")}</CardTitle>
          <CardDescription>
            Create your provider account and start seeing patients virtually
          </CardDescription>
        </CardHeader>
        <form onSubmit={handleSubmit}>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">{t(locale, "auth", "name")}</Label>
              <Input
                id="name"
                type="text"
                placeholder="Dr. Jane Smith"
                value={formData.name}
                onChange={(e) => updateField("name", e.target.value)}
                required
                autoComplete="name"
                className="h-11 text-base"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">{t(locale, "auth", "email")}</Label>
              <Input
                id="email"
                type="email"
                placeholder="jane@clinic.ca"
                value={formData.email}
                onChange={(e) => updateField("email", e.target.value)}
                required
                autoComplete="email"
                className="h-11 text-base"
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="clinicName">{t(locale, "auth", "clinicName")}</Label>
                <Input
                  id="clinicName"
                  type="text"
                  placeholder="Maple Health Clinic"
                  value={formData.clinicName}
                  onChange={(e) => updateField("clinicName", e.target.value)}
                  required
                  className="h-11 text-base"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="specialty">{t(locale, "auth", "specialty")}</Label>
                <Select
                  value={formData.specialty}
                  onValueChange={(val: unknown) => updateField("specialty", val as string)}
                >
                  {SPECIALTIES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {s}
                    </SelectItem>
                  ))}
                </Select>
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="password">{t(locale, "auth", "password")}</Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder="••••••••"
                    value={formData.password}
                    onChange={(e) => updateField("password", e.target.value)}
                    required
                    autoComplete="new-password"
                    className="h-11 pr-10 text-base"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirmPassword">{t(locale, "auth", "confirmPassword")}</Label>
                <div className="relative">
                  <Input
                    id="confirmPassword"
                    type={showConfirmPassword ? "text" : "password"}
                    placeholder="••••••••"
                    value={formData.confirmPassword}
                    onChange={(e) => updateField("confirmPassword", e.target.value)}
                    required
                    autoComplete="new-password"
                    className="h-11 pr-10 text-base"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  >
                    {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>
            </div>
          </CardContent>
          <CardFooter className="flex flex-col gap-4">
            <Button
              type="submit"
              className="w-full h-11 text-base font-medium"
              disabled={loading}
            >
              {loading ? (
                <span className="flex items-center gap-2">
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  {t(locale, "common", "loading")}
                </span>
              ) : (
                <span className="flex items-center gap-2">
                  <UserPlus className="h-4 w-4" />
                  {t(locale, "auth", "signUp")}
                </span>
              )}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              {t(locale, "auth", "hasAccount")}{" "}
              <Link href="/login" className="font-medium text-primary hover:underline">
                {t(locale, "auth", "signIn")}
              </Link>
            </p>
          </CardFooter>
        </form>
      </Card>
    </motion.div>
  );
}
