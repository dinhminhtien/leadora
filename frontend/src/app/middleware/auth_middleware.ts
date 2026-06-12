import { NextResponse, type NextRequest } from "next/server";

import { ROUTE_PATHS } from "@/app/routes/route_paths";

const PUBLIC_ROUTE_PREFIXES = [
  ROUTE_PATHS.login,
  ROUTE_PATHS.forgotPassword,
  ROUTE_PATHS.resetPassword,
  ROUTE_PATHS.feedback,
];

export const ACCESS_TOKEN_COOKIE = "access_token";

export function isPublicRoute(pathname: string) {
  return PUBLIC_ROUTE_PREFIXES.some((route) => pathname.startsWith(route));
}

export function handleAuthMiddleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const isAuthenticated = Boolean(request.cookies.get(ACCESS_TOKEN_COOKIE));

  if (isAuthenticated && pathname === ROUTE_PATHS.login) {
    return NextResponse.redirect(new URL(ROUTE_PATHS.dashboard, request.url));
  }

  if (!isAuthenticated && !isPublicRoute(pathname)) {
    const loginUrl = new URL(ROUTE_PATHS.login, request.url);
    loginUrl.searchParams.set("next", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}
