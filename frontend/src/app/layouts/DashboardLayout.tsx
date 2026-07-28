"use client";

/**
 * Authenticated app shell — Website UI Blueprint §4.
 *
 * Composes `AppSidebar` + `AppTopbar` + `CommandPalette` + `NotificationCenter`
 * + `ShortcutsOverlay` and owns the cross-cutting concerns that must not move:
 *
 * - **Route guard.** `/dashboard` always replaces to the role home, and any path
 *   failing `canAccessPath` replaces to the role home. Unchanged.
 * - **Logout order.** Clear the local Supabase session and the auth store
 *   *first*, then fire the two sign-out calls under `allSettled`, then navigate.
 *   A failing network call must never trap a user inside a signed-in shell.
 * - **Avatar resolution.** The `local-storage-avatar://` pseudo-scheme is
 *   resolved here because the layout owns the user object.
 * - **Notification deep-links.** Preserved verbatim in `notification-center.tsx`.
 *
 * Everything that changed is presentation: the shell is now token-driven,
 * collapsible to a rail, off-canvas on mobile, and keyboard-navigable.
 */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

import { ROUTE_PATHS } from "@/app/routes/route_paths";
import {
  canAccessPath,
  dashboardPathForRole,
  getUserRole,
} from "@/shared/auth/access";
import { useAuthStore } from "@/stores/auth_store";
import { useUiStore } from "@/stores/ui_store";
import { authService } from "@/services/auth_service";
import { supabaseAuthService } from "@/services/supabase_auth_service";
import { ToastContainer } from "@/components/ui/ToastContainer";
import { FloatingAssistant } from "@/features/ai_assistant/components/FloatingAssistant";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { AppTopbar } from "@/components/layout/app-topbar";
import { CommandPalette } from "@/components/layout/command-palette";
import { NotificationCenter } from "@/components/layout/notification-center";
import { ShortcutsOverlay } from "@/components/layout/shortcuts-overlay";
import { useGlobalShortcuts } from "@/shared/hooks/use_global_shortcuts";
import {
  useMarkAllRead,
  useMarkNotificationRead,
  useNotifications,
  useUnreadNotificationCount,
} from "@/features/notification/hooks/use_notifications";
import { cn } from "@/lib/utils";
import { getAvatarSource } from "@/shared/utils/avatar";

type DashboardLayoutProps = {
  children: React.ReactNode;
};

export function DashboardLayout({ children }: DashboardLayoutProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { sidebarOpen, toggleSidebar } = useUiStore();
  const { user, clearUser, isLoading } = useAuthStore();

  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);

  const { data: unreadCount = 0 } = useUnreadNotificationCount();
  // Second argument is `poll` — the preview list refreshes only while the
  // popover is open, exactly as the previous shell did.
  const { data: notifPreview, isLoading: notifLoading } = useNotifications(
    { size: 8 },
    notifOpen,
  );
  const markNotificationRead = useMarkNotificationRead();
  const markAllRead = useMarkAllRead();

  const role = getUserRole(user);
  const roleDashboard = dashboardPathForRole(role);
  const permissions = useMemo(() => user?.permissions ?? [], [user?.permissions]);
  const permKey = permissions.join(",");

  const avatarSource = useMemo(
    () => getAvatarSource(user?.avatarUrl, user?.id),
    [user?.avatarUrl, user?.id],
  );

  // ── Route guard (behaviour unchanged) ────────────────────────────────────
  useEffect(() => {
    if (isLoading || !user) return;
    if (pathname === ROUTE_PATHS.dashboard) {
      router.replace(roleDashboard);
    } else if (!canAccessPath(role, pathname, permissions)) {
      router.replace(roleDashboard);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoading, user, pathname, role, roleDashboard, permKey, router]);

  // Transient surfaces close themselves at the point of navigation rather than
  // being reset here on `pathname`: the sidebar's links call `onMobileClose`,
  // and the notification centre calls `onOpenChange(false)` before it pushes.
  // Closing at the source is both cheaper (no extra render per route change)
  // and more honest — the component that navigates owns the dismissal.

  const handleLogout = useCallback(async () => {
    supabaseAuthService.clearLocalSession();
    clearUser();

    try {
      await Promise.allSettled([
        authService.logout(),
        supabaseAuthService.signOut(),
      ]);
    } catch (e) {
      console.warn("Failed to complete services logout", e);
    }

    router.push(ROUTE_PATHS.login || "/login");
  }, [clearUser, router]);

  // Focus whichever toolbar search the active screen registered (§6.1 `/`).
  const focusToolbarSearch = useCallback(() => {
    const el = document.querySelector<HTMLInputElement>("[data-toolbar-search]");
    el?.focus();
    el?.select();
  }, []);

  useGlobalShortcuts({
    role,
    permissions,
    onOpenPalette: () => setPaletteOpen(true),
    onOpenShortcuts: () => setShortcutsOpen((v) => !v),
    onFocusSearch: focusToolbarSearch,
    enabled: !isLoading && !!user,
  });

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* §6.4 — skip link, visible only on focus. */}
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-md focus:bg-brand-500 focus:px-3 focus:py-2 focus:text-[13px] focus:font-semibold focus:text-brand-foreground"
      >
        Skip to content
      </a>

      <AppSidebar
        role={role}
        permissions={permissions}
        pathname={pathname}
        roleDashboard={roleDashboard}
        expanded={sidebarOpen}
        onToggle={toggleSidebar}
        mobileOpen={mobileNavOpen}
        onMobileClose={() => setMobileNavOpen(false)}
        onOpenPalette={() => setPaletteOpen(true)}
        unreadCount={unreadCount}
      />

      {/* Content column shifts to clear the sidebar at md and above. */}
      <div
        className={cn(
          "flex min-h-screen min-w-0 flex-col",
          "transition-[padding] duration-[240ms] ease-[cubic-bezier(0.2,0,0,1)]",
          sidebarOpen ? "md:pl-64" : "md:pl-[72px]",
        )}
      >
        <AppTopbar
          user={user}
          role={role}
          permissions={permissions}
          avatarSource={avatarSource}
          onOpenMobileNav={() => setMobileNavOpen(true)}
          onOpenPalette={() => setPaletteOpen(true)}
          onOpenShortcuts={() => setShortcutsOpen(true)}
          onLogout={handleLogout}
          notificationSlot={
            <NotificationCenter
              notifications={notifPreview?.content ?? []}
              unreadCount={unreadCount}
              isLoading={notifLoading}
              open={notifOpen}
              onOpenChange={setNotifOpen}
              onMarkRead={(id, read) => markNotificationRead.mutate({ id, read })}
              onMarkAllRead={() => markAllRead.mutate()}
            />
          }
        />

        <main
          id="main-content"
          className="min-w-0 flex-1 px-4 py-6 lg:px-8 lg:py-8"
        >
          {children}
        </main>
      </div>

      <CommandPalette
        open={paletteOpen}
        onOpenChange={setPaletteOpen}
        role={role}
        permissions={permissions}
      />
      <ShortcutsOverlay open={shortcutsOpen} onOpenChange={setShortcutsOpen} />

      <ToastContainer />
      {/* Lia — draggable floating assistant, available on every dashboard screen. */}
      <FloatingAssistant />
    </div>
  );
}
