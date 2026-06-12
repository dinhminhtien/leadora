"use client";

import { useEffect } from "react";

import { supabaseAuthService } from "@/services/supabase_auth_service";
import { useAuthStore } from "@/stores/auth_store";

/**
 * AuthProvider listens to Supabase auth state changes and syncs
 * the Zustand auth store. Wrap your app with this provider.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { setUser, clearUser, setLoading } = useAuthStore();

  useEffect(() => {
    // Check initial session
    setLoading(true);
    supabaseAuthService
      .getUser()
      .then((supabaseUser) => {
        if (supabaseUser) {
          setUser({
            id: supabaseUser.id,
            email: supabaseUser.email ?? "",
            name:
              supabaseUser.user_metadata?.full_name ??
              supabaseUser.user_metadata?.name ??
              supabaseUser.email ??
              "",
            roles: supabaseUser.user_metadata?.roles ?? [],
          });
        } else {
          clearUser();
        }
      })
      .catch(() => {
        clearUser();
      });

    // Listen for auth state changes (sign in, sign out, token refresh)
    const {
      data: { subscription },
    } = supabaseAuthService.onAuthStateChange((_event, session) => {
      if (session?.user) {
        setUser({
          id: session.user.id,
          email: session.user.email ?? "",
          name:
            session.user.user_metadata?.full_name ??
            session.user.user_metadata?.name ??
            session.user.email ??
            "",
          roles: session.user.user_metadata?.roles ?? [],
        });
      } else {
        clearUser();
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [setUser, clearUser, setLoading]);

  return <>{children}</>;
}
