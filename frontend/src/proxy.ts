import type { NextRequest } from "next/server";

import { handleAuthMiddleware } from "@/app/middleware/auth_middleware";
import { updateSupabaseSession } from "@/services/supabase/middleware";

export async function proxy(request: NextRequest) {
  // 1. Refresh Supabase session cookies on every request
  const { user, supabaseResponse } = await updateSupabaseSession(request);

  // 2. Handle route protection based on auth state
  const authRedirect = handleAuthMiddleware(request, user);
  if (authRedirect) {
    return authRedirect;
  }

  return supabaseResponse;
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\..*).*)" ],
};
