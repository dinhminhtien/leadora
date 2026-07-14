"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole, dashboardPathForRole } from "@/shared/auth/access";

/**
 * `/dashboard` is a dispatcher: it forwards the user to their role-specific
 * dashboard (staff / manager / admin). Kept as a stable entry point so old
 * links, the login redirect fallback, and the OAuth callback all resolve here.
 */
export default function DashboardDispatcherPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const isLoading = useAuthStore((s) => s.isLoading);

  useEffect(() => {
    if (isLoading) return;
    router.replace(dashboardPathForRole(getUserRole(user)));
  }, [isLoading, user, router]);

  return (
    <div className="flex items-center justify-center h-64 gap-2 text-slate-400">
      <Loader2 className="size-5 animate-spin" /> Loading your dashboard…
    </div>
  );
}
