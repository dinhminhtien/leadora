"use client";

import React, { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
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
  Home,
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
    ],
  },
  {
    title: "Activities & Tasks",
    items: [
      { href: ROUTE_PATHS.followUpTasks, label: "Tasks", Icon: CalendarCheck },
      { href: ROUTE_PATHS.reminders, label: "Reminders", Icon: ClipboardCheck },
      { href: ROUTE_PATHS.interactionTimeline, label: "Timeline", Icon: Clock },
    ],
  },
  {
    title: "Hotel Operations",
    items: [
      { href: ROUTE_PATHS.customerProfiles, label: "Guest Profiles", Icon: Users },
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
  const { sidebarOpen, toggleSidebar } = useUiStore();
  const { user } = useAuthStore();
  const [isUserDropdownOpen, setIsUserDropdownOpen] = useState(false);
  const [isQuickAddOpen, setIsQuickAddOpen] = useState(false);

  // Quick Action Handler (Mock)
  const handleQuickAction = (type: string) => {
    alert(`Quick Add ${type} is supported in this MVP!`);
    setIsQuickAddOpen(false);
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800 flex">
      {/* Sidebar navigation */}
      <aside
        className={`fixed inset-y-0 left-0 z-30 flex flex-col border-r border-slate-200 bg-slate-900 text-slate-300 transition-all duration-300 ${
          sidebarOpen ? "w-64" : "w-16"
        }`}
      >
        {/* Brand/Logo Header */}
        <div className="flex h-16 items-center justify-between px-4 border-b border-slate-800 bg-slate-950">
          <div className="flex items-center gap-3 overflow-hidden">
            <div className="flex size-9 items-center justify-center rounded-lg bg-blue-600 text-white font-bold shrink-0 shadow-md">
              L
            </div>
            {sidebarOpen && (
              <div className="flex flex-col">
                <span className="text-sm font-semibold text-white tracking-wide">Leadora</span>
                <span className="text-[10px] text-blue-400 font-medium tracking-wider uppercase">Hotel CRM</span>
              </div>
            )}
          </div>
          {sidebarOpen && (
            <button
              onClick={toggleSidebar}
              className="p-1 rounded text-slate-400 hover:text-white hover:bg-slate-800 transition"
              title="Collapse Menu"
            >
              <ChevronLeft className="size-4" />
            </button>
          )}
        </div>

        {/* Scrollable Navigation */}
        <nav className="flex-1 space-y-4 overflow-y-auto px-3 py-4 custom-scrollbar">
          {navigationGroups.map((group, idx) => (
            <div key={idx} className="space-y-1">
              {sidebarOpen && (
                <p className="px-3 text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-2">
                  {group.title}
                </p>
              )}
              {group.items.map(({ href, label, Icon }) => {
                const isActive = pathname === href || pathname.startsWith(href + "/");
                return (
                  <Link
                    key={href}
                    href={href}
                    className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all ${
                      isActive
                        ? "bg-blue-600 text-white shadow-sm"
                        : "hover:bg-slate-800 hover:text-white"
                    }`}
                    title={label}
                  >
                    <Icon className={`size-4.5 shrink-0 ${isActive ? "text-white" : "text-slate-400"}`} />
                    {sidebarOpen && <span className="truncate">{label}</span>}
                  </Link>
                );
              })}
              {sidebarOpen && idx < navigationGroups.length - 1 && (
                <hr className="border-slate-800 my-3 opacity-50" />
              )}
            </div>
          ))}
        </nav>

        {/* Sidebar Footer */}
        <div className="p-3 border-t border-slate-800 bg-slate-950/50 flex flex-col gap-2">
          {!sidebarOpen && (
            <button
              onClick={toggleSidebar}
              className="flex w-full justify-center py-2 text-slate-400 hover:text-white transition"
              title="Expand Menu"
            >
              <ChevronRight className="size-5" />
            </button>
          )}
          {sidebarOpen && (
            <div className="flex items-center justify-between text-xs text-slate-500">
              <span className="flex items-center gap-1">
                <ShieldCheck className="size-3.5 text-emerald-500" />
                Secure Workspace
              </span>
              <span className="text-[10px] bg-slate-800 px-1.5 py-0.5 rounded text-slate-400">v1.0</span>
            </div>
          )}
        </div>
      </aside>

      {/* Main Content Area */}
      <div
        className={`flex-1 flex flex-col transition-all duration-300 ${
          sidebarOpen ? "pl-64" : "pl-16"
        }`}
      >
        {/* Top Header */}
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6 shadow-sm">
          {/* Left: Mobile Toggle or Current Route Name */}
          <div className="flex items-center gap-3">
            <button
              onClick={toggleSidebar}
              className="p-1 rounded text-slate-500 hover:text-slate-700 hover:bg-slate-100 lg:hidden"
            >
              <Menu className="size-5" />
            </button>
            <div className="hidden sm:block">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Leadora Workspace</span>
              <h2 className="text-sm font-bold text-slate-800 flex items-center gap-1.5 mt-0.5">
                <Sparkles className="size-3.5 text-blue-500" />
                Hotel Sales Dashboard
              </h2>
            </div>
          </div>

          {/* Center: Search Lead/Deals */}
          <div className="flex-1 max-w-md mx-6 hidden md:block">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search guests, corporate leads, active deals..."
                className="w-full rounded-full border border-slate-200 bg-slate-50 pl-10 pr-4 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-blue-500 focus:bg-white transition"
              />
            </div>
          </div>

          {/* Right: Actions (Quick Add, Notification, Profile) */}
          <div className="flex items-center gap-4">
            {/* Quick Add Button */}
            <div className="relative">
              <Button
                variant="success"
                size="sm"
                className="gap-1.5 rounded-full px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs transition-colors"
                onClick={() => setIsQuickAddOpen(!isQuickAddOpen)}
              >
                <Plus className="size-3.5" />
                <span>Quick Add</span>
              </Button>
              
              {isQuickAddOpen && (
                <>
                  <div className="fixed inset-0 z-20" onClick={() => setIsQuickAddOpen(false)} />
                  <div className="absolute right-0 mt-2 w-48 rounded-xl border border-slate-100 bg-white p-1.5 shadow-xl z-30 animate-in fade-in slide-in-from-top-2 duration-150">
                    <p className="px-2.5 py-1 text-[10px] font-semibold text-slate-400 uppercase tracking-wider">Create New</p>
                    <button
                      onClick={() => handleQuickAction("Lead")}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                    >
                      <Handshake className="size-3.5 text-blue-500" /> Add New Lead
                    </button>
                    <button
                      onClick={() => handleQuickAction("Deal")}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                    >
                      <BriefcaseBusiness className="size-3.5 text-emerald-500" /> Add New Deal
                    </button>
                    <button
                      onClick={() => handleQuickAction("Task")}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                    >
                      <CalendarCheck className="size-3.5 text-amber-500" /> Add Follow-up Task
                    </button>
                    <button
                      onClick={() => handleQuickAction("Guest Profile")}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                    >
                      <Users className="size-3.5 text-indigo-500" /> Create Guest Profile
                    </button>
                  </div>
                </>
              )}
            </div>

            {/* Notification Center */}
            <button
              className="relative rounded-full p-2 text-slate-500 hover:bg-slate-100 transition"
              title="Notifications"
            >
              <Bell className="size-4.5" />
              <span className="absolute right-1.5 top-1.5 size-2 rounded-full bg-red-500"></span>
            </button>

            {/* User Profile */}
            <div className="relative">
              <button
                onClick={() => setIsUserDropdownOpen(!isUserDropdownOpen)}
                className="flex items-center gap-2 rounded-full p-1 hover:bg-slate-100 transition"
              >
                <div className="flex size-7 items-center justify-center rounded-full bg-blue-600 text-white text-[11px] font-bold">
                  {user?.name?.slice(0, 2).toUpperCase() || "JD"}
                </div>
              </button>

              {isUserDropdownOpen && (
                <>
                  <div className="fixed inset-0 z-20" onClick={() => setIsUserDropdownOpen(false)} />
                  <div className="absolute right-0 mt-2 w-56 rounded-xl border border-slate-100 bg-white p-1.5 shadow-xl z-30 animate-in fade-in slide-in-from-top-2 duration-150">
                    <div className="px-2.5 py-2 border-b border-slate-100">
                      <p className="text-xs font-bold text-slate-800">{user?.name || "John Doe"}</p>
                      <p className="text-[10px] text-slate-400">{user?.email || "j.doe@leadora-hotels.com"}</p>
                      <div className="mt-1 flex gap-1">
                        <Badge variant="primary" className="text-[9px] py-0 px-1.5 font-semibold">
                          Sales Manager
                        </Badge>
                      </div>
                    </div>
                    <div className="p-1">
                      <button className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left text-xs text-slate-700 hover:bg-slate-50 transition">
                        <User className="size-3.5 text-slate-400" /> Profile Settings
                      </button>
                      <button className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left text-xs text-slate-700 hover:bg-slate-50 transition">
                        <Settings className="size-3.5 text-slate-400" /> Admin Console
                      </button>
                      <button className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left text-xs text-red-600 hover:bg-red-50 transition border-t border-slate-100 mt-1 pt-2">
                        <LogOut className="size-3.5" /> Logout Session
                      </button>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </header>

        {/* Dashboard Main Scrollable Workspace */}
        <main className="flex-1 overflow-y-auto px-6 py-6 custom-scrollbar bg-slate-50/50">
          {children}
        </main>
      </div>
    </div>
  );
}
