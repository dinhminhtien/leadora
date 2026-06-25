import { NextResponse } from "next/server";

import { createSupabaseServerClient } from "@/services/supabase/server";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8085/api/v1";

type ApiErrorBody = {
  errorCode?: string;
  message?: string;
};

/**
 * OAuth callback handler.
 * Supabase redirects here after Google OAuth with an auth code.
 * We exchange the code for a session, verify the email is provisioned in Leadora,
 * then redirect to the dashboard (or "next" param).
 */
export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url);
  const code = searchParams.get("code");
  const next = searchParams.get("next") ?? "/dashboard";

  if (!code) {
    return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`);
  }

  const supabase = await createSupabaseServerClient();
  const { error: exchangeError } = await supabase.auth.exchangeCodeForSession(code);

  if (exchangeError) {
    return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`);
  }

  const {
    data: { session },
  } = await supabase.auth.getSession();

  if (!session?.access_token) {
    await supabase.auth.signOut();
    return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`);
  }

  const verifyResponse = await fetch(`${API_BASE_URL}/auth/oauth/verify`, {
    headers: {
      Authorization: `Bearer ${session.access_token}`,
      Accept: "application/json",
    },
    cache: "no-store",
  });

  if (!verifyResponse.ok) {
    await supabase.auth.signOut();
    let loginError = "auth_callback_failed";
    try {
      const body = (await verifyResponse.json()) as ApiErrorBody;
      if (body.errorCode === "ACCOUNT_NOT_PROVISIONED") {
        loginError = "access_denied";
      } else if (body.errorCode === "ACCOUNT_INACTIVE") {
        loginError = "account_inactive";
      }
    } catch {
      // keep default login error
    }
    return NextResponse.redirect(`${origin}/login?error=${loginError}`);
  }

  return NextResponse.redirect(`${origin}${next}`);
}
