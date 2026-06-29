"use client";

import React, { useState, useEffect, useMemo } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useNotifications } from "@/features/notification/hooks/use_notifications";
import { getUserRole, canAccessPath, dashboardPathForRole } from "@/shared/auth/access";
import {
  Bell,
  Bot,
  BriefcaseBusiness,
  CalendarCheck,
  ChartNoAxesCombined,
  ClipboardCheck,
  Clock,
  CreditCard,
  FileText,
  Gauge,
  Handshake,
  Headphones,
  Hotel,
  KeyRound,
  LayoutDashboard,
  MessageSquareText,
  ReceiptText,
  ShieldCheck,
  Users,
  Workflow,
  Menu,
  ChevronLeft,
  ChevronRight,
  Search,
  Plus,
  LogOut,
  User,
  Settings,
  Sparkles,
  type LucideIcon,
} from "lucide-react";

import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { useUiStore } from "@/stores/ui_store";
import { useAuthStore } from "@/stores/auth_store";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { authService } from "@/services/auth_service";
import { supabaseAuthService } from "@/services/supabase_auth_service";
import { ToastContainer } from "@/components/ui/ToastContainer";

type DashboardLayoutProps = {
  children: React.ReactNode;
};

type NavItem = {
  href: string;
  label: string;
  Icon: LucideIcon;
};

type NavGroup = {
  title: string;
  items: NavItem[];
};

const navigationGroups: NavGroup[] = [
  {
    title: "Sales & Pipeline",
    items: [
      { href: ROUTE_PATHS.dashboard, label: "Dashboard", Icon: LayoutDashboard },
      { href: ROUTE_PATHS.leads, label: "Leads", Icon: Handshake },
      { href: ROUTE_PATHS.salesPipeline, label: "Pipeline Board", Icon: ChartNoAxesCombined },
      { href: ROUTE_PATHS.deals, label: "Deals List", Icon: BriefcaseBusiness },
      { href: ROUTE_PATHS.quotations, label: "Quotations", Icon: ReceiptText },
      { href: ROUTE_PATHS.pendingApprovals, label: "Pending Approvals", Icon: ShieldCheck },
    ],
  },
  {
    title: "Activities & Tasks",
    items: [
      { href: ROUTE_PATHS.manageFollowUpTasks, label: "Tasks", Icon: CalendarCheck },
      { href: ROUTE_PATHS.reminders, label: "Reminders", Icon: ClipboardCheck },
      { href: ROUTE_PATHS.interactionTimeline, label: "Timeline", Icon: Clock },
    ],
  },
  {
    title: "Hotel Operations",
    items: [
      { href: ROUTE_PATHS.customerProfiles, label: "Customer Profiles", Icon: Users },
      { href: ROUTE_PATHS.bookingConfirmation, label: "Bookings", Icon: FileText },
      { href: ROUTE_PATHS.reservationStatus, label: "Reservations", Icon: Hotel },
      { href: ROUTE_PATHS.operationalHandover, label: "Handovers", Icon: Workflow },
      { href: ROUTE_PATHS.frontOfficeHandover, label: "Front Shift", Icon: Headphones },
      { href: ROUTE_PATHS.depositPayment, label: "Payments", Icon: CreditCard },
    ],
  },
  {
    title: "Analytics & Config",
    items: [
      { href: ROUTE_PATHS.reporting, label: "Reporting", Icon: FileText },
      { href: ROUTE_PATHS.sla, label: "SLA Control", Icon: Gauge },
      { href: ROUTE_PATHS.customerFeedback, label: "Feedback", Icon: MessageSquareText },
      { href: ROUTE_PATHS.notifications, label: "Alerts", Icon: Bell },
      { href: ROUTE_PATHS.identityAccess, label: "User Access", Icon: KeyRound },
      { href: ROUTE_PATHS.aiAssistant, label: "AI Copilot", Icon: Bot },
    ],
  },
];

export function DashboardLayout({ children }: DashboardLayoutProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { sidebarOpen, toggleSidebar } = useUiStore();
  const { user, clearUser, isLoading } = useAuthStore();
  const { data: unreadNotifications } = useNotifications(true);
  const unreadCount = unreadNotifications?.length ?? 0;
  const [isUserDropdownOpen, setIsUserDropdownOpen] = useState(false);
  const [isQuickAddOpen, setIsQuickAddOpen] = useState(false);

  // ── Role + permission based access control (frontend) ──────────────────────
  const role = getUserRole(user);
  // Front Office gets a stripped-down desk: no sales search / quick-add.
  const isFrontOffice = role === "FO";
  const roleDashboard = dashboardPathForRole(role);
  const permissions = user?.permissions ?? [];
  const permKey = permissions.join(",");

  // Sidebar shows only the screens this user may open (permission-gated for
  // SALES/MANAGER, role-gated for ADMIN). The generic "Dashboard" link points at
  // the role-specific home so highlighting and navigation match.
  const visibleGroups = useMemo(() => {
    const seen = new Set<string>();
    return navigationGroups
      .map((group) => ({
        ...group,
        items: group.items
          .map((item) => ({
            ...item,
            href: item.href === ROUTE_PATHS.dashboard ? roleDashboard : item.href,
          }))
          .filter((item) => canAccessPath(role, item.href, permissions))
          // De-duplicate links that resolve to the same route (e.g. for FO the generic
          // "Dashboard" maps to the handover screen, which is also its own nav item).
          .filter((item) => {
            if (seen.has(item.href)) return false;
            seen.add(item.href);
            return true;
          }),
      }))
      .filter((group) => group.items.length > 0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [role, roleDashboard, permKey]);

  // Guard: a user landing on a route they may not access (deep link, stale tab,
  // or the bare /dashboard dispatcher) is sent to their own dashboard.
  useEffect(() => {
    if (isLoading || !user) return;
    if (pathname === ROUTE_PATHS.dashboard) {
      router.replace(roleDashboard);
    } else if (!canAccessPath(role, pathname, permissions)) {
      router.replace(roleDashboard);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoading, user, pathname, role, roleDashboard, permKey, router]);

  const handleLogout = async () => {
    setIsUserDropdownOpen(false);
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
  };

  // Quick Action Handler (Mock)
  const handleQuickAction = (type: string) => {
    alert(`Quick Add ${type} is supported in this MVP!`);
    setIsQuickAddOpen(false);
  };

  return (
    <div className="min-h-screen bg-background text-foreground flex">
      {/* Sidebar navigation: Fully Adaptive light/dark theme */}
      <aside
        className={`fixed inset-y-0 left-0 z-30 flex flex-col border-r border-zinc-200 dark:border-zinc-900 bg-zinc-50 dark:bg-zinc-950 text-zinc-500 dark:text-zinc-400 transition-all duration-300 ${sidebarOpen ? "w-52" : "w-16"
          }`}
      >
        {/* Brand/Logo Header */}
        <div className={`relative flex h-16 items-center border-b border-zinc-200 dark:border-zinc-900 bg-zinc-100/40 dark:bg-zinc-950 ${sidebarOpen ? "justify-between px-4" : "justify-center px-0"}`}>
          <button
            type="button"
            onClick={toggleSidebar}
            className={`flex items-center ${sidebarOpen ? "gap-3 px-3" : "w-full h-full justify-center px-0"} rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 transition`}
            title={sidebarOpen ? "Collapse Menu" : "Expand Menu"}
          >
            <div className="flex size-9 items-center justify-center rounded-xl bg-primary text-white font-bold shrink-0 shadow-lg shadow-primary/20">
              L
            </div>
            {sidebarOpen && (
              <div className="flex flex-col">
                <span className="text-xs font-bold text-zinc-800 dark:text-zinc-100 tracking-wider">Leadora</span>
                <span className="text-[9px] text-zinc-400 dark:text-zinc-500 font-semibold tracking-widest uppercase">Hotel CRM</span>
              </div>
            )}
          </button>
          {sidebarOpen && (
            <button
              type="button"
              onClick={toggleSidebar}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-2 rounded-full bg-white shadow-sm text-zinc-600 hover:text-zinc-900 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:text-zinc-100 transition cursor-pointer"
              title="Collapse Menu"
            >
              <ChevronLeft className="size-4" />
            </button>
          )}
        </div>

        {/* Scrollable Navigation */}
        <nav className="flex-1 space-y-4 overflow-y-auto px-3 py-4 custom-scrollbar">
          {visibleGroups.map((group, idx) => (
            <div key={idx} className="space-y-1">
              {sidebarOpen && (
                <p className="px-3 text-[9px] font-bold text-zinc-400 dark:text-zinc-600 uppercase tracking-widest mb-1.5">
                  {group.title}
                </p>
              )}
              {group.items.map(({ href, label, Icon }) => {
                const isActive = pathname === href || pathname.startsWith(href + "/");
                return (
                  <Link
                    key={href}
                    href={href}
                    className={`flex items-center gap-2.5 rounded-lg px-3 py-2 text-xs font-semibold transition-all relative ${isActive
                      ? "bg-zinc-200/60 text-zinc-900 border border-zinc-300/40 dark:bg-zinc-900 dark:text-zinc-100 dark:border-zinc-850 shadow-xs"
                      : "hover:bg-zinc-200/30 hover:text-zinc-900 dark:hover:bg-zinc-900/50 dark:hover:text-zinc-200 text-zinc-500 dark:text-zinc-400"
                      }`}
                    title={label}
                  >
                    {isActive && (
                      <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-4.5 bg-primary rounded-r-xl" />
                    )}
                    <Icon className={`size-4 shrink-0 ${isActive ? "text-primary" : "text-zinc-400 dark:text-zinc-500"}`} />
                    {sidebarOpen && <span className="truncate">{label}</span>}
                  </Link>
                );
              })}
              {sidebarOpen && idx < visibleGroups.length - 1 && (
                <hr className="border-zinc-200 dark:border-zinc-900 my-2.5 opacity-40" />
              )}
            </div>
          ))}
        </nav>

        {/* Sidebar Footer */}
        <div className="p-3 border-t border-zinc-200 dark:border-zinc-900 bg-zinc-100/20 dark:bg-zinc-950/40 flex flex-col gap-2">
          {!sidebarOpen && (
            <button
              onClick={toggleSidebar}
              className="flex w-full justify-center py-2 text-zinc-400 hover:text-zinc-800 dark:hover:text-zinc-200 transition cursor-pointer"
              title="Expand Menu"
            >
              <ChevronRight className="size-5" />
            </button>
          )}
          {sidebarOpen && (
            <div className="flex items-center justify-between text-[10px] text-zinc-400 dark:text-zinc-600 font-medium">
              <span className="flex items-center gap-1">
                <ShieldCheck className="size-3.5 text-emerald-500" />
                Secure Portal
              </span>
              <span className="bg-zinc-200 dark:bg-zinc-900 px-1.5 py-0.5 rounded text-zinc-500 font-semibold">v1.0</span>
            </div>
          )}
        </div>
      </aside>

      {/* Main Content Area */}
      <div
        className={`flex-1 flex flex-col transition-all duration-300 ${sidebarOpen ? "pl-52" : "pl-16"
          }`}
      >
        {/* Top Header */}
        <header className="sticky top-0 z-40 flex h-16 items-center justify-between border-b border-border bg-background/80 backdrop-blur-md px-6 shadow-xs">
          {/* Left: Mobile Toggle or Current Route Name */}
          <div className="flex items-center gap-3">
            <button
              onClick={toggleSidebar}
              className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted lg:hidden cursor-pointer"
            >
              <Menu className="size-5" />
            </button>
            <div className="hidden sm:block">
              <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Leadora Workspace</span>
              <h2 className="text-xs font-bold text-foreground flex items-center gap-1.5 mt-0.5">
                <Sparkles className="size-3.5 text-primary" />
                {isFrontOffice ? "Front Office Desk" : "Hotel Sales Dashboard"}
              </h2>
            </div>
          </div>

          {/* Center: Search Lead/Deals (hidden for Front Office) */}
          {!isFrontOffice && (
          <div className="flex-1 max-w-sm mx-6 hidden md:block">
            <div className="relative">
              <Search className="absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
              <input
                type="text"
                placeholder="Search guests, corporate leads, active deals..."
                className="w-full rounded-xl border border-border bg-muted/40 pl-10 pr-4 py-2 text-xs text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-primary focus:bg-background focus:ring-2 focus:ring-primary/10 transition shadow-[inset_0_1px_2px_rgba(0,0,0,0.015)]"
              />
            </div>
          </div>
          )}

          {/* Right: Actions (Quick Add, Notification, Profile) */}
          <div className="ml-auto flex items-center gap-3.5">
            {/* Quick Add Button (hidden for Front Office) */}
            {!isFrontOffice && (
            <div className="relative">
              <Button
                variant="primary"
                size="sm"
                className="rounded-lg shadow-xs"
                leftIcon={<Plus className="size-3.5" />}
                onClick={() => setIsQuickAddOpen(!isQuickAddOpen)}
              >
                Quick Add
              </Button>

              {isQuickAddOpen && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setIsQuickAddOpen(false)} />
                  <div className="absolute right-0 mt-2 w-48 rounded-xl border border-border bg-background p-1.5 shadow-lg z-50 animate-in fade-in slide-in-from-top-2 duration-150">
                    <p className="px-2.5 py-1 text-[9px] font-bold text-muted-foreground uppercase tracking-widest">Create New</p>
                    <button
                      onClick={() => handleQuickAction("Lead")}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium text-foreground hover:bg-muted transition cursor-pointer"
                    >
                      <Handshake className="size-3.5 text-indigo-500" /> Add New Lead
                    </button>
                    <button
                      onClick={() => handleQuickAction("Deal")}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium text-foreground hover:bg-muted transition cursor-pointer"
                    >
                      <BriefcaseBusiness className="size-3.5 text-emerald-500" /> Add New Deal
                    </button>
                    <button
                      onClick={() => handleQuickAction("Task")}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium text-foreground hover:bg-muted transition cursor-pointer"
                    >
                      <CalendarCheck className="size-3.5 text-amber-500" /> Add Follow-up Task
                    </button>
                    <button
                      onClick={() => handleQuickAction("Guest Profile")}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium text-foreground hover:bg-muted transition cursor-pointer"
                    >
                      <Users className="size-3.5 text-blue-500" /> Create Guest Profile
                    </button>
                  </div>
                </>
              )}
            </div>
            )}

            {/* Theme Toggle */}
            <ThemeToggle className="shadow-none border-none bg-transparent hover:bg-muted text-muted-foreground dark:hover:bg-muted/80" />

            {/* Notification Center */}
            <button
              onClick={() => router.push(ROUTE_PATHS.notifications)}
              className="relative rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-foreground transition cursor-pointer"
              title="Notifications"
            >
              <Bell className="size-4" />
              {unreadCount > 0 && (
                <span className="absolute right-2 top-2 size-1.5 rounded-full bg-danger"></span>
              )}
            </button>

            {/* User Profile */}
            <div className="relative">
              <button
                onClick={() => setIsUserDropdownOpen(!isUserDropdownOpen)}
                className="flex items-center gap-2 rounded-full p-0.5 hover:bg-muted transition cursor-pointer"
              >
                <div className="flex size-7 items-center justify-center rounded-full bg-primary text-white text-[10px] font-bold shadow-xs">
                  {user?.name ? user.name.slice(0, 2).toUpperCase() : ""}
                </div>
              </button>

              {isUserDropdownOpen && user && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setIsUserDropdownOpen(false)} />
                  <div className="absolute right-0 mt-2 w-56 rounded-xl border border-border bg-background p-1.5 shadow-lg z-50 animate-in fade-in slide-in-from-top-2 duration-150">
                    <div className="px-2.5 py-2 border-b border-border">
                      <p className="text-xs font-bold text-foreground">{user.name}</p>
                      <p className="text-[10px] text-muted-foreground">{user.email}</p>
                      <div className="mt-1.5 flex gap-1">
                        <Badge variant="primary" className="text-[9px] py-0 px-1.5 font-semibold">
                          {role === "ADMIN" ? "Admin" : role === "MANAGER" ? "Sales Manager" : role === "FO" ? "Front Office" : "Sales Staff"}
                        </Badge>
                      </div>
                    </div>
                    <div className="p-1">
                      <button className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left text-xs text-foreground hover:bg-muted transition cursor-pointer">
                        <User className="size-3.5 text-muted-foreground" /> Profile Settings
                      </button>
                      <button className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left text-xs text-foreground hover:bg-muted transition cursor-pointer">
                        <Settings className="size-3.5 text-muted-foreground" /> Admin Console
                      </button>
                      <button 
                        onClick={handleLogout}
                        className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left text-xs text-danger hover:bg-rose-500/5 transition border-t border-border mt-1 pt-2 cursor-pointer"
                      >
                        <LogOut className="size-3.5" /> Logout Session
                      </button>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </header>

        {/* Dashboard Main Scrollable Workspace with Dot Matrix Background */}
        <main className="flex-1 overflow-y-auto px-6 py-6 custom-scrollbar bg-background bg-dot-pattern">
          {children}
        </main>
      </div>
      <ToastContainer />
    </div>
  );
}
