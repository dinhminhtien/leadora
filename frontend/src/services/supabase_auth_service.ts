import { createSupabaseBrowserClient } from "@/services/supabase/client";
import type { LoginCredentials } from "@/shared/types/auth";

// Lazily create the browser client on first use. Creating it at module load runs during
// Next.js static prerendering (on the server, with no browser env), where the public
// Supabase env vars can be absent — `createBrowserClient` then throws "Your project's URL
// and API key are required", which fails the production build (e.g. on Vercel). Deferring
// creation to the first call keeps Supabase strictly browser-side.
let client: ReturnType<typeof createSupabaseBrowserClient> | null = null;
function sb() {
  if (!client) client = createSupabaseBrowserClient();
  return client;
}

export const supabaseAuthService = {
  async signInWithPassword(credentials: LoginCredentials) {
    const { data, error } = await sb().auth.signInWithPassword({
      email: credentials.email,
      password: credentials.password,
    });
    if (error) throw error;
    return data;
  },

  async signInWithGoogle(redirectTo?: string) {
    const { data, error } = await sb().auth.signInWithOAuth({
      provider: "google",
      options: {
        redirectTo: `${window.location.origin}/auth/callback${redirectTo ? `?next=${redirectTo}` : ""}`,
      },
    });
    if (error) throw error;
    return data;
  },

  async signOut() {
    const { error } = await sb().auth.signOut();
    if (error) throw error;
  },

  clearLocalSession() {
    if (typeof window !== "undefined") {
      localStorage.removeItem("accessToken");
      document.cookie = "accessToken=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
      
      // Clear all Supabase keys from localStorage
      const keysToRemove: string[] = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && (key.startsWith("sb-") || key.includes("supabase"))) {
          keysToRemove.push(key);
        }
      }
      keysToRemove.forEach((key) => localStorage.removeItem(key));

      // Clear Supabase cookies
      document.cookie.split(";").forEach((cookie) => {
        const name = cookie.split("=")[0].trim();
        if (name.startsWith("sb-") || name.includes("supabase")) {
          document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`;
        }
      });
    }
  },

  async resetPasswordForEmail(email: string) {
    const { data, error } = await sb().auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/reset-password`,
    });
    if (error) throw error;
    return data;
  },

  async updatePassword(newPassword: string) {
    const { data, error } = await sb().auth.updateUser({
      password: newPassword,
    });
    if (error) throw error;
    return data;
  },

  async getUser() {
    const {
      data: { user },
      error,
    } = await sb().auth.getUser();
    if (error) throw error;
    return user;
  },

  async getSession() {
    const {
      data: { session },
      error,
    } = await sb().auth.getSession();
    if (error) throw error;
    return session;
  },

  onAuthStateChange(
    callback: (
      event: string,
      session: Awaited<
        ReturnType<ReturnType<typeof sb>["auth"]["getSession"]>
      >["data"]["session"],
    ) => void,
  ) {
    return sb().auth.onAuthStateChange(callback);
  },
};
