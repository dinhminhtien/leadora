import { NextResponse, type NextRequest } from "next/server";
import type { User } from "@supabase/supabase-js";

import { ROUTE_PATHS } from "@/app/routes/route_paths";

const PUBLIC_ROUTE_PREFIXES = [
  ROUTE_PATHS.login,
  ROUTE_PATHS.forgotPassword,
  ROUTE_PATHS.resetPassword,
  ROUTE_PATHS.feedback,
  "/auth/callback",
];

export function isPublicRoute(pathname: string) {
  return PUBLIC_ROUTE_PREFIXES.some((route) => pathname.startsWith(route));
}

/**
 * Returns a redirect response if the user should be redirected,
 * or undefined if the request should continue normally.
 */
export function handleAuthMiddleware(
  request: NextRequest,
  user: User | null,
): NextResponse | undefined {
  const { pathname } = request.nextUrl;
  const isAuthenticated = process.env.NODE_ENV === "development" ? true : Boolean(user);// Restore production auth checks

  // Authenticated user visiting login → redirect to dashboard
  if (isAuthenticated && pathname === ROUTE_PATHS.login) {
    return NextResponse.redirect(new URL(ROUTE_PATHS.dashboard, request.url));
  }

  // Unauthenticated user visiting protected route → redirect to login
  if (!isAuthenticated && !isPublicRoute(pathname)) {
    const loginUrl = new URL(ROUTE_PATHS.login, request.url);
    loginUrl.searchParams.set("next", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return undefined;
}
