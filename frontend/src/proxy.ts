import type { NextRequest } from "next/server";

import { handleAuthMiddleware } from "@/app/middleware/auth_middleware";

export function proxy(request: NextRequest) {
  return handleAuthMiddleware(request);
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
