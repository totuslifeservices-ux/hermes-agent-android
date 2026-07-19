"use client";

import { useLocale } from "@/components/features/locale-provider";
import { useTheme } from "next-themes";
import { t } from "@/i18n/translations";
import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession, signOut } from "next-auth/react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
  SheetClose,
} from "@/components/ui/sheet";
import {
  LayoutDashboard,
  Users,
  Clock,
  Settings,
  Search,
  Menu,
  Sun,
  Moon,
  Languages,
  LogOut,
  User,
  ChevronLeft,
} from "lucide-react";
import { AuthProvider } from "@/components/features/auth-provider";

const navItems = [
  { href: "/dashboard", label: "dashboard", icon: LayoutDashboard },
  { href: "/dashboard/waiting-room", label: "waitingRoom", icon: Users },
  { href: "/dashboard/history", label: "history", icon: Clock },
  { href: "/dashboard/settings", label: "settings", icon: Settings },
];

function Sidebar({ collapsed, onClose }: { collapsed?: boolean; onClose?: () => void }) {
  const { locale, setLocale } = useLocale();
  const pathname = usePathname();

  return (
    <aside
      className={cn(
        "flex h-full flex-col border-r bg-card",
        collapsed ? "w-16 items-center px-2" : "w-64 px-4"
      )}
    >
      {/* Logo */}
      <div className={cn("flex items-center border-b py-4", collapsed ? "justify-center" : "gap-3 px-2")}>
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-foreground text-lg font-bold">
          T
        </div>
        {!collapsed && (
          <span className="text-base font-bold tracking-tight">
            {t(locale, "common", "appName")}
          </span>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 space-y-1 py-4">
        {navItems.map((item) => {
          const isActive = pathname === item.href || pathname.startsWith(item.href + "/");
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onClose}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary/10 text-primary"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground",
                collapsed && "justify-center px-0"
              )}
            >
              <Icon className="h-5 w-5 shrink-0" />
              {!collapsed && <span>{t(locale, "nav", item.label)}</span>}
            </Link>
          );
        })}
      </nav>

      {/* Bottom section */}
      <div className={cn("border-t py-4", collapsed ? "flex flex-col items-center gap-3" : "space-y-2 px-2")}>
        <button
          onClick={() => setLocale(locale === "en" ? "fr" : "en")}
          className={cn(
            "flex items-center gap-2 rounded-lg text-sm text-muted-foreground transition-colors hover:text-foreground",
            collapsed ? "justify-center p-2" : "px-2 py-1.5"
          )}
        >
          <Languages className="h-4 w-4" />
          {!collapsed && <span>EN / FR</span>}
        </button>
      </div>
    </aside>
  );
}

function DashboardLayoutContent({ children }: { children: React.ReactNode }) {
  const { locale, setLocale } = useLocale();
  const { theme, setTheme } = useTheme();
  const { data: session } = useSession();
  const pathname = usePathname();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const initials = session?.user?.name
    ?.split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase() || "?";

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Mobile sidebar */}
      <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
        <SheetTrigger className="fixed top-3 left-3 z-40 rounded-lg p-2 text-muted-foreground hover:bg-muted lg:hidden">
          <Menu className="h-5 w-5" />
        </SheetTrigger>
        <SheetContent side="left" className="w-64 p-0">
          <Sidebar onClose={() => setMobileMenuOpen(false)} />
        </SheetContent>
      </Sheet>

      {/* Desktop sidebar */}
      <div
        className={cn(
          "hidden lg:flex shrink-0 transition-all duration-300",
          sidebarCollapsed ? "w-16" : "w-64"
        )}
      >
        <Sidebar collapsed={sidebarCollapsed} />
      </div>

      {/* Collapse toggle for desktop */}
      <button
        onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
        className="absolute left-64 top-1/2 z-30 hidden -translate-x-1/2 rounded-full border bg-background p-1 text-muted-foreground shadow-sm transition-all hover:text-foreground lg:block"
        style={{ left: sidebarCollapsed ? "4rem" : "16rem" }}
      >
        <ChevronLeft className={cn("h-3 w-3 transition-transform", sidebarCollapsed && "rotate-180")} />
      </button>

      {/* Main area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top header bar */}
        <header className="flex h-16 shrink-0 items-center gap-4 border-b bg-card px-4 sm:px-6">
          {/* Spacer for mobile menu button */}
          <div className="lg:hidden w-10" />

          {/* Search */}
          <div className="hidden sm:block flex-1 max-w-md">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder={t(locale, "common", "search")}
                className="h-9 pl-9 text-sm"
              />
            </div>
          </div>

          <div className="flex-1 sm:flex-none" />

          {/* Right side controls */}
          <div className="flex items-center gap-2">
            {/* Language switcher */}
            <button
              onClick={() => setLocale(locale === "en" ? "fr" : "en")}
              className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              <Languages className="h-4 w-4" />
              <span className="hidden sm:inline">{locale === "en" ? "FR" : "EN"}</span>
            </button>

            {/* Dark mode toggle */}
            <button
              onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
              className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              aria-label="Toggle dark mode"
            >
              {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>

            {/* User menu */}
            <DropdownMenu>
              <DropdownMenuTrigger className="flex items-center gap-2 rounded-lg p-1.5 transition-colors hover:bg-muted">
                <Avatar className="h-8 w-8">
                  <AvatarFallback className="bg-primary/10 text-primary text-xs">{initials}</AvatarFallback>
                </Avatar>
                <span className="hidden text-sm font-medium sm:inline">
                  {session?.user?.name || "User"}
                </span>
              </DropdownMenuTrigger>
              <DropdownMenuContent className="w-56">
                <div className="flex items-center gap-2 px-2 py-1.5">
                  <Avatar className="h-9 w-9">
                    <AvatarFallback className="bg-primary/10 text-primary text-xs">{initials}</AvatarFallback>
                  </Avatar>
                  <div className="flex flex-col">
                    <span className="text-sm font-medium">{session?.user?.name}</span>
                    <span className="text-xs text-muted-foreground">{session?.user?.email}</span>
                  </div>
                </div>
                <DropdownMenuSeparator />
                <DropdownMenuItem className="cursor-pointer">
                  <Link href="/dashboard/settings" className="flex items-center w-full"> 
                    <User className="mr-2 h-4 w-4" />
                    {t(locale, "nav", "settings")}
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => signOut({ callbackUrl: "/" })}
                  className="cursor-pointer text-destructive focus:text-destructive"
                >
                  <LogOut className="mr-2 h-4 w-4" />
                  {t(locale, "nav", "logout")}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">
          {children}
        </main>
      </div>
    </div>
  );
}

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthProvider>
      <DashboardLayoutContent>{children}</DashboardLayoutContent>
    </AuthProvider>
  );
}
