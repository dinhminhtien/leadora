"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { authService } from "@/services/auth_service";
import { useAuthStore } from "@/stores/auth_store";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

const PUBLIC_ROUTES = [
  "/login",
  "/forgot-password",
  "/reset-password",
  "/feedback",
  "/auth/callback",
];

/**
 * AuthProvider verifies the local JWT token, syncs the Zustand auth store,
 * and handles secure client-side redirection for protected routes.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { setUser, clearUser, setLoading, isAuthenticated, isLoading } = useAuthStore();
  const pathname = usePathname();
  const router = useRouter();

  const isPublicRoute = PUBLIC_ROUTES.some((route) => pathname.startsWith(route));

  useEffect(() => {
    const checkSession = async () => {
      setLoading(true);
      const token = localStorage.getItem("accessToken");
      if (!token) {
        clearUser();
        return;
      }

      try {
        const response = await authService.getProfile();
        setUser(response.data);
      } catch (e) {
        localStorage.removeItem("accessToken");
        clearUser();
      }
    };

    checkSession();
  }, [setUser, clearUser, setLoading]);

  // Enforce redirection to login screen for unauthenticated users accessing protected routes
  useEffect(() => {
    if (!isLoading && !isAuthenticated && !isPublicRoute) {
      router.replace(`${ROUTE_PATHS.login || "/login"}?next=${encodeURIComponent(pathname)}`);
    }
  }, [isLoading, isAuthenticated, isPublicRoute, pathname, router]);

  // Show a clean, professional loading screen during session verification of protected routes
  if (isLoading && !isPublicRoute) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-4 animate-pulse">
          <div className="size-10 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="text-sm font-medium text-muted-foreground">Verifying session...</p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
