import Link from "next/link";
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
  type LucideIcon,
} from "lucide-react";

import { ROUTE_PATHS } from "@/app/routes/route_paths";

type DashboardLayoutProps = {
  children: React.ReactNode;
};

type NavItem = {
  href: string;
  label: string;
  Icon: LucideIcon;
};

const navigationItems: NavItem[] = [
  { href: ROUTE_PATHS.dashboard, label: "Dashboard", Icon: LayoutDashboard },
  { href: ROUTE_PATHS.identityAccess, label: "Identity Access", Icon: KeyRound },
  {
    href: ROUTE_PATHS.customerFeedback,
    label: "Customer Feedback",
    Icon: MessageSquareText,
  },
  { href: ROUTE_PATHS.leads, label: "Leads", Icon: Handshake },
  {
    href: ROUTE_PATHS.customerProfiles,
    label: "Customer Profiles",
    Icon: Users,
  },
  {
    href: ROUTE_PATHS.followUpTasks,
    label: "Follow-up Tasks",
    Icon: CalendarCheck,
  },
  {
    href: ROUTE_PATHS.salesPipeline,
    label: "Sales Pipeline",
    Icon: ChartNoAxesCombined,
  },
  { href: ROUTE_PATHS.deals, label: "Deals", Icon: BriefcaseBusiness },
  {
    href: ROUTE_PATHS.interactionTimeline,
    label: "Interaction Timeline",
    Icon: Clock,
  },
  { href: ROUTE_PATHS.quotations, label: "Quotations", Icon: ReceiptText },
  { href: ROUTE_PATHS.notifications, label: "Notifications", Icon: Bell },
  { href: ROUTE_PATHS.reminders, label: "Reminders", Icon: ClipboardCheck },
  { href: ROUTE_PATHS.sla, label: "SLA", Icon: Gauge },
  {
    href: ROUTE_PATHS.bookingConfirmation,
    label: "Booking Confirmation",
    Icon: FileText,
  },
  {
    href: ROUTE_PATHS.reservationStatus,
    label: "Reservation Status",
    Icon: Hotel,
  },
  {
    href: ROUTE_PATHS.operationalHandover,
    label: "Operational Handover",
    Icon: Workflow,
  },
  {
    href: ROUTE_PATHS.depositPayment,
    label: "Deposit Payment",
    Icon: CreditCard,
  },
  {
    href: ROUTE_PATHS.frontOfficeHandover,
    label: "Front Office Handover",
    Icon: Headphones,
  },
  { href: ROUTE_PATHS.reporting, label: "Reporting", Icon: FileText },
  { href: ROUTE_PATHS.aiAssistant, label: "AI Assistant", Icon: Bot },
];

export function DashboardLayout({ children }: DashboardLayoutProps) {
  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-950">
      <aside className="fixed inset-y-0 left-0 hidden w-72 border-r border-zinc-200 bg-white lg:block">
        <div className="flex h-16 items-center gap-3 border-b border-zinc-200 px-5">
          <div className="flex size-9 items-center justify-center rounded-lg bg-teal-600 text-white">
            <Home className="size-5" aria-hidden="true" />
          </div>
          <div>
            <p className="text-sm font-semibold text-zinc-950">Leadora</p>
            <p className="text-xs text-zinc-500">Workflow client</p>
          </div>
        </div>

        <nav className="h-[calc(100vh-4rem)] space-y-1 overflow-y-auto px-3 py-4">
          {navigationItems.map(({ href, label, Icon }) => (
            <Link
              key={href}
              href={href}
              className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-zinc-700 transition hover:bg-teal-50 hover:text-teal-800"
            >
              <Icon className="size-4 shrink-0" aria-hidden="true" />
              <span>{label}</span>
            </Link>
          ))}
        </nav>
      </aside>

      <div className="lg:pl-72">
        <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-zinc-200 bg-white px-4 sm:px-6 lg:px-8">
          <div>
            <p className="text-sm font-semibold text-zinc-950">Leadora</p>
            <p className="text-xs text-zinc-500">Follow-up and sales workflow</p>
          </div>
          <div className="flex items-center gap-2 text-sm text-zinc-600">
            <ShieldCheck className="size-4 text-teal-700" aria-hidden="true" />
            <span>Secure workspace</span>
          </div>
        </header>

        <main className="px-4 py-6 sm:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
