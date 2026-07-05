"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { authService } from "@/services/auth_service";
import { supabaseAuthService } from "@/services/supabase_auth_service";
import { useAuthStore } from "@/stores/auth_store";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { getUserRole, dashboardPathForRole } from "@/shared/auth/access";

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
  const { user, setUser, clearUser, setLoading, isAuthenticated, isLoading } = useAuthStore();
  const pathname = usePathname();
  const router = useRouter();

  const isPublicRoute = pathname === "/" || PUBLIC_ROUTES.some((route) => pathname.startsWith(route));

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
        } else if (errorCode === "ACCOUNT_LOCKED") {
          router.replace(`${ROUTE_PATHS.login}?error=account_locked`);
        }
      } finally {
        setLoading(false);
      }
    };

    checkSession();
  }, [setUser, clearUser, setLoading, router]);

  // Client-side route guard:
  // - Unauthenticated user on a protected route → bounce to login (keep destination).
  // - Authenticated user sitting on the login page → send them where they belong
  //   (the `next` deep-link if present, otherwise their role dashboard). Without this,
  //   an already-signed-in user can still open /login and stay there.
  useEffect(() => {
    if (isLoading) return;

    if (!isAuthenticated && !isPublicRoute) {
      router.replace(`${ROUTE_PATHS.login || "/login"}?next=${encodeURIComponent(pathname)}`);
      return;
    }

    if (isAuthenticated && pathname === ROUTE_PATHS.login) {
      const next = new URLSearchParams(window.location.search).get("next");
      const home = dashboardPathForRole(getUserRole(user));
      router.replace(next && next !== ROUTE_PATHS.dashboard ? next : home);
    }
  }, [isLoading, isAuthenticated, isPublicRoute, pathname, router, user]);

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
