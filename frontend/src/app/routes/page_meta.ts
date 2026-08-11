/**
 * Canonical page identity — title, subtitle and breadcrumb for every route.
 *
 * Website UI Blueprint §4.3 requires every management screen to open with the
 * same header block: `Group › Page` breadcrumb, an h1 title, and one sentence of
 * subtitle explaining what the screen is for. Before this file each screen
 * hand-rolled its own `<h1>`, so titles drifted from the sidebar labels and most
 * screens had no subtitle at all.
 *
 * Two rules make the drift impossible to reintroduce:
 *
 * 1. **Breadcrumbs are derived, never typed.** `crumbsFor()` looks the route up
 *    in `NAV_GROUPS`, so the parent crumb is by construction the same string the
 *    sidebar shows. Regrouping the sidebar moves every breadcrumb with it.
 * 2. **Copy lives here, not in the screen.** One place to review the product
 *    voice across the whole app.
 *
 * Screens spread the entry and supply only what is page-local — the actions:
 *
 * ```tsx
 * <PageHeader {...PAGE_META.leads} actions={<Button…/>} />
 * ```
 */

import { NAV_GROUPS } from "@/app/routes/navigation";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import type { Crumb } from "@/components/ui/page-header";

export type PageMeta = {
  crumbs: Crumb[];
  title: string;
  subtitle: string;
};

/**
 * Breadcrumb trail for a nav route: `[group, page]`.
 *
 * The group crumb is deliberately href-less — nav groups are headings in the
 * sidebar, not destinations, and a crumb that looks clickable but isn't is worse
 * than plain text.
 *
 * Routes outside the sidebar (quotation builder, profile, Lia) pass their own
 * trail through `meta()` instead.
 */
export function crumbsFor(href: string): Crumb[] {
  for (const group of NAV_GROUPS) {
    const item = group.items.find((i) => i.href === href);
    if (item) return [{ label: group.title }, { label: item.label }];
  }
  return [];
}

/**
 * Builds one entry. `trail` overrides the derived breadcrumb for routes that do
 * not appear in the sidebar; the last crumb is always the page itself.
 */
function meta(
  href: string,
  title: string,
  subtitle: string,
  trail?: Crumb[],
): PageMeta {
  return { crumbs: trail ?? crumbsFor(href), title, subtitle };
}

export const PAGE_META = {
  /* ── Sales & Pipeline ─────────────────────────────────────────────── */

  leads: meta(
    ROUTE_PATHS.leads,
    "Leads",
    "Capture and qualify every sales opportunity.",
  ),

  salesPipeline: meta(
    ROUTE_PATHS.salesPipeline,
    "Pipeline Board",
    "Move deals through each stage and see exactly what is stuck.",
  ),

  deals: meta(
    ROUTE_PATHS.deals,
    "Deals",
    "Track every hospitality opportunity through the sales pipeline.",
  ),

  quotations: meta(
    ROUTE_PATHS.quotations,
    "Quotations",
    "Create, approve and manage customer quotations.",
  ),

  pendingApprovals: meta(
    ROUTE_PATHS.pendingApprovals,
    "Pending Approvals",
    "Review the discount and policy exceptions waiting on your decision.",
  ),

  quotationCreate: meta(
    ROUTE_PATHS.quotationCreate,
    "New Quotation",
    "Build a quotation from a deal, then send it for approval.",
    [
      { label: "Sales & Pipeline" },
      { label: "Quotations", href: ROUTE_PATHS.quotations },
      { label: "New Quotation" },
    ],
  ),

  quotationRevise: meta(
    ROUTE_PATHS.quotations,
    "Revise Quotation",
    "Create a new version — the approved version stays intact for audit.",
    [
      { label: "Sales & Pipeline" },
      { label: "Quotations", href: ROUTE_PATHS.quotations },
      { label: "Revise" },
    ],
  ),

  /* ── Activities ───────────────────────────────────────────────────── */

  followUpTasks: meta(
    ROUTE_PATHS.manageFollowUpTasks,
    "Follow-up Tasks",
    "Plan, execute, hand over and close the work behind every lead, customer and deal.",
  ),

  reminders: meta(
    ROUTE_PATHS.reminders,
    "Reminders",
    "Time-based nudges that stop follow-ups from slipping past their due date.",
  ),

  calendar: meta(
    ROUTE_PATHS.calendar,
    "Calendar",
    "Follow-up tasks, reminders and stays on one timeline.",
  ),

  interactionTimeline: meta(
    ROUTE_PATHS.interactionTimeline,
    "Timeline",
    "Complete interaction history across every business object.",
  ),

  /* ── Hotel Operations ─────────────────────────────────────────────── */

  customerProfiles: meta(
    ROUTE_PATHS.customerProfiles,
    "Customers",
    "Manage individual and corporate customer relationships.",
  ),

  roomRequests: meta(
    ROUTE_PATHS.roomRequests,
    "Room Requests",
    // Keeps the operational gate the old screen copy explained: Sales is blocked
    // until Reservation answers, which is the whole point of this inbox.
    "Sales cannot quote or book until you confirm the rooms — check availability, then answer here.",
  ),

  bookingConfirmation: meta(
    ROUTE_PATHS.bookingConfirmation,
    "Bookings",
    "Coordinate reservation requests and operational handover.",
  ),

  reservationStatus: meta(
    ROUTE_PATHS.reservationStatus,
    "Reservations",
    "Track arrivals, check-in and check-out across today's stays.",
  ),

  operationalHandover: meta(
    ROUTE_PATHS.operationalHandover,
    "Handovers",
    "Pass every confirmed booking from Sales to Front Office with full context.",
  ),

  frontOfficeHandover: meta(
    ROUTE_PATHS.frontOfficeHandover,
    "Front Office Desk",
    "Confirm arrival readiness for each guest before they reach the desk.",
  ),

  depositPayment: meta(
    ROUTE_PATHS.depositPayment,
    "Payments",
    "Track deposits and payment confirmation.",
  ),

  /* ── Analytics & Config ───────────────────────────────────────────── */

  reporting: meta(
    ROUTE_PATHS.reporting,
    "Reporting",
    "Pipeline, conversion and performance analytics across the sales floor.",
  ),

  sla: meta(
    ROUTE_PATHS.sla,
    "SLA Control",
    "Configure response rules and monitor breach compliance in real time.",
  ),

  customerFeedback: meta(
    ROUTE_PATHS.customerFeedback,
    "Feedback",
    "Review and moderate the feedback guests submit after their stay.",
  ),

  sentimentAnalytics: meta(
    ROUTE_PATHS.sentimentAnalytics,
    "Sentiment AI",
    "Aspect-based sentiment analysis and customer satisfaction trends.",
  ),

  notifications: meta(
    ROUTE_PATHS.notifications,
    "Alerts",
    "Every system alert routed to you, in one place.",
  ),

  identityAccess: meta(
    ROUTE_PATHS.identityAccess,
    "User & Access",
    "Manage accounts, roles and permission assignments.",
  ),

  activityLogs: meta(
    ROUTE_PATHS.activityLogs,
    "Activity Log",
    "Immutable audit trail of every change made to a pipeline record.",
  ),

  /* ── Outside the sidebar ──────────────────────────────────────────── */

  profile: meta(
    ROUTE_PATHS.profile,
    "My Profile",
    "Manage your account details, password and notification preferences.",
    [{ label: "Account" }, { label: "My Profile" }],
  ),

  aiAssistant: meta(
    ROUTE_PATHS.aiAssistant,
    "Lia Assistant",
    "Ask about your CRM data — read-only and scoped to what you may view.",
    [{ label: "Assistant" }, { label: "Lia" }],
  ),
} as const satisfies Record<string, PageMeta>;
