import { AuthProvider } from "@/components/features/auth-provider";

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthProvider>
      <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-teal-50 via-white to-amber-50 px-4 py-12 dark:from-teal-950/20 dark:via-zinc-950 dark:to-amber-950/20">
        <div className="w-full max-w-md">
          {children}
        </div>
        <p className="mt-8 text-center text-sm text-muted-foreground">
          &copy; {new Date().getFullYear()} Totus Telehealth. All rights reserved.
        </p>
      </div>
    </AuthProvider>
  );
}
