import { NextResponse } from "next/server";

import { createSupabaseServerClient } from "@/services/supabase/server";

/**
 * OAuth callback handler.
 * Supabase redirects here after Google OAuth with an auth code.
 * We exchange the code for a session and redirect to the dashboard (or "next" param).
 */
export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url);
  const code = searchParams.get("code");
  const next = searchParams.get("next") ?? "/dashboard";

  if (code) {
    const supabase = await createSupabaseServerClient();
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (!error) {
      return NextResponse.redirect(`${origin}${next}`);
    }
  }

  // If code exchange fails, redirect to login with error
  return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`);
}
