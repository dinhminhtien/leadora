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
