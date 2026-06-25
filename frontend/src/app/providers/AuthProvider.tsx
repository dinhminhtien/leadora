"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { authService } from "@/services/auth_service";
import { supabaseAuthService } from "@/services/supabase_auth_service";
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
      let token = typeof window !== "undefined" ? localStorage.getItem("accessToken") : null;

      if (!token) {
        try {
          const session = await supabaseAuthService.getSession();
          if (session?.access_token) {
            token = session.access_token;
          }
        } catch (e) {
          console.warn("Failed to check Supabase session in AuthProvider", e);
        }
      }

      if (!token) {
        clearUser();
        setLoading(false);
        return;
      }

      try {
        const response = await authService.getProfile();
        setUser(response.data);
      } catch (e: unknown) {
        supabaseAuthService.clearLocalSession();
        clearUser();

        const axiosError = e as { response?: { status?: number; data?: { errorCode?: string } } };
        const errorCode = axiosError.response?.data?.errorCode;
        if (errorCode === "ACCOUNT_NOT_PROVISIONED") {
          router.replace(`${ROUTE_PATHS.login}?error=access_denied`);
        } else if (errorCode === "ACCOUNT_INACTIVE") {
          router.replace(`${ROUTE_PATHS.login}?error=account_inactive`);
        }
      } finally {
        setLoading(false);
      }
    };

    checkSession();
  }, [setUser, clearUser, setLoading, router]);

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
