/**
 * Navigation information architecture — Website UI Blueprint §4.1.
 *
 * **One source of truth, three consumers.** The sidebar, the ⌘K command palette
 * (§3.8) and the `g`-prefixed go-to shortcuts (§6.1) all read this list. Before
 * the redesign the sidebar owned its own literal, so a route added to the nav
 * was invisible to search and had no shortcut. Declaring the IA once means a new
 * module appears in all three places automatically.
 *
 * Every entry points at a route that already exists in `route_paths.ts`, and
 * visibility is still decided by `canAccessPath` — this file describes *where
 * things live*, never *who may see them*.
 */

import type { LucideIcon } from "lucide-react";
import {
  BedDouble,
  Bell,
  BrainCircuit,
  BriefcaseBusiness,
  CalendarCheck,
  CalendarDays,
  ChartNoAxesCombined,
  ClipboardCheck,
  Clock,
  CreditCard,
  FileText,
  Gauge,
  Handshake,
  Headphones,
  History,
  Hotel,
  KeyRound,
  LayoutDashboard,
  MessageSquareText,
  ReceiptText,
  ShieldCheck,
  Users,
  Workflow,
} from "lucide-react";

import { ROUTE_PATHS } from "@/app/routes/route_paths";

export type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Optional one-line description shown in the command palette. */
  hint?: string;
  /** `g`-prefix shortcut key from §6.1, e.g. `l` → `g l`. */
  shortcut?: string;
  /** Which unread/pending counter (if any) badges this item. */
  badge?: "notifications";
  badgeText?: string;
};

export type NavGroup = {
  title: string;
  items: NavItem[];
};

/**
 * Group order and membership follow §4.1 exactly.
 *
 * Note `Calendar` is new in this redesign (§10.15 defines `/calendar` as a
 * first-class surface). It consumes the existing task, reminder and booking
 * endpoints — no new API.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    title: "Sales & Pipeline",
    items: [
      {
        href: ROUTE_PATHS.dashboard,
        label: "Dashboard",
        icon: LayoutDashboard,
        hint: "Your role home",
        shortcut: "h",
      },
      {
        href: ROUTE_PATHS.leads,
        label: "Leads",
        icon: Handshake,
        hint: "Inbound opportunities",
        shortcut: "l",
      },
      {
        href: ROUTE_PATHS.salesPipeline,
        label: "Pipeline Board",
        icon: ChartNoAxesCombined,
        hint: "Deals by stage",
        shortcut: "p",
      },
      {
        href: ROUTE_PATHS.deals,
        label: "Deals",
        icon: BriefcaseBusiness,
        hint: "Deal register",
        shortcut: "d",
      },
      {
        href: ROUTE_PATHS.quotations,
        label: "Quotations",
        icon: ReceiptText,
        hint: "Price proposals",
      },
      {
        href: ROUTE_PATHS.pendingApprovals,
        label: "Pending Approvals",
        icon: ShieldCheck,
        hint: "Discounts awaiting your decision",
      },
      {
        href: ROUTE_PATHS.contracts,
        label: "Contracts",
        icon: FileText,
        hint: "Legal commercial agreements",
      },
    ],
  },
  {
    title: "Activities",
    items: [
      {
        href: ROUTE_PATHS.manageFollowUpTasks,
        label: "Follow-up Tasks",
        icon: CalendarCheck,
        hint: "Plan and close follow-ups",
        shortcut: "t",
      },
      {
        href: ROUTE_PATHS.reminders,
        label: "Reminders",
        icon: ClipboardCheck,
        hint: "Time-based nudges",
        shortcut: "r",
      },
      {
        href: ROUTE_PATHS.calendar,
        label: "Calendar",
        icon: CalendarDays,
        hint: "Month, week, day and agenda",
        shortcut: "c",
      },
      {
        href: ROUTE_PATHS.interactionTimeline,
        label: "Timeline",
        icon: Clock,
        hint: "Every customer touch",
      },
    ],
  },
  {
    title: "Hotel Operations",
    items: [
      {
        href: ROUTE_PATHS.customerProfiles,
        label: "Customers",
        icon: Users,
        hint: "Guest and company profiles",
      },
      {
        href: ROUTE_PATHS.roomRequests,
        label: "Room Requests",
        icon: BedDouble,
        hint: "Reservation inbox",
      },
      {
        href: ROUTE_PATHS.bookingConfirmation,
        label: "Bookings",
        icon: FileText,
        hint: "Booking requests and confirmations",
      },
      {
        href: ROUTE_PATHS.reservationStatus,
        label: "Reservations",
        icon: Hotel,
        hint: "Check-in and check-out",
      },
      {
        href: ROUTE_PATHS.operationalHandover,
        label: "Handovers",
        icon: Workflow,
        hint: "Sales to Front Office",
      },
      {
        href: ROUTE_PATHS.frontOfficeHandover,
        label: "Front Office Desk",
        icon: Headphones,
        hint: "Arrival readiness",
      },
      {
        href: ROUTE_PATHS.depositPayment,
        label: "Payments",
        icon: CreditCard,
        hint: "Deposits and settlements",
      },
    ],
  },
  {
    title: "Analytics & Config",
    items: [
      {
        href: ROUTE_PATHS.reporting,
        label: "Reporting",
        icon: FileText,
        hint: "Performance and pipeline analytics",
      },
      {
        href: ROUTE_PATHS.sla,
        label: "SLA Control",
        icon: Gauge,
        hint: "Monitor, rules and compliance",
      },
      {
        href: ROUTE_PATHS.customerFeedback,
        label: "Feedback",
        icon: MessageSquareText,
        hint: "Guest reviews",
      },
      {
        href: ROUTE_PATHS.sentimentAnalytics,
        label: "GX Insights",
        icon: BrainCircuit,
        hint: "ABSA guest experience analytics",
        badgeText: "AI",
      },
      {
        href: ROUTE_PATHS.notifications,
        label: "Alerts",
        icon: Bell,
        hint: "Notification centre",
        badge: "notifications",
      },
      {
        href: ROUTE_PATHS.identityAccess,
        label: "User & Access",
        icon: KeyRound,
        hint: "Accounts, roles and permissions",
      },
      {
        href: ROUTE_PATHS.activityLogs,
        label: "Activity Log",
        icon: History,
        hint: "Audit trail of pipeline changes",
      },
    ],
  },
];

/** Flat list — used by the command palette and shortcut resolver. */
export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((g) => g.items);

/** `g`-prefix shortcut map from §6.1 (`g l` → Leads, `g t` → Tasks, …). */
export const GO_TO_SHORTCUTS: Record<string, string> = NAV_ITEMS.reduce(
  (acc, item) => {
    if (item.shortcut) acc[item.shortcut] = item.href;
    return acc;
  },
  {} as Record<string, string>,
);

/**
 * §4.5 Quick Add. Each entry deep-links to the module that owns the create
 * flow — the blueprint's "opens the module's Create modal in place" is honoured
 * by the destination screen opening its own drawer via the `?new=1` param.
 */
export type QuickAddItem = {
  label: string;
  href: string;
  icon: LucideIcon;
  /** Permission-gated exactly like the destination route. */
  requires: string;
};

export const QUICK_ADD_ITEMS: QuickAddItem[] = [
  { label: "New lead", href: `${ROUTE_PATHS.leads}?new=1`, icon: Handshake, requires: ROUTE_PATHS.leads },
  { label: "New deal", href: `${ROUTE_PATHS.deals}?new=1`, icon: BriefcaseBusiness, requires: ROUTE_PATHS.deals },
  { label: "New quotation", href: ROUTE_PATHS.quotationCreate, icon: ReceiptText, requires: ROUTE_PATHS.quotations },
  { label: "New task", href: `${ROUTE_PATHS.manageFollowUpTasks}?new=1`, icon: CalendarCheck, requires: ROUTE_PATHS.manageFollowUpTasks },
  { label: "New reminder", href: `${ROUTE_PATHS.reminders}?new=1`, icon: ClipboardCheck, requires: ROUTE_PATHS.reminders },
  { label: "New customer", href: `${ROUTE_PATHS.customerProfiles}?new=1`, icon: Users, requires: ROUTE_PATHS.customerProfiles },
];
