export const ROUTE_PATHS = {
  login: "/login",
  forgotPassword: "/forgot-password",
  resetPassword: "/reset-password",
  feedback: "/feedback",
  dashboard: "/dashboard",
  dashboardStaff: "/dashboard/staff",
  dashboardManager: "/dashboard/manager",
  dashboardAdmin: "/dashboard/admin",
  identityAccess: "/identity-access",
  customerFeedback: "/customer-feedback",
  sentimentAnalytics: "/analytics/sentiment",
  leads: "/leads",
  // A lead opens in the drawer over the list — there is no full-page detail any
  // more. `/leads/{id}` still redirects here for links already in the wild, but
  // new links should skip that hop and point straight at the list.
  leadDetail: (id: string) => `/leads?lead=${encodeURIComponent(id)}`,
  customerProfiles: "/customer-profiles",
  followUpTasks: "/follow-up-tasks",
  manageFollowUpTasks: "/manage-follow-up-tasks",
  // Blueprint §10.15 — the calendar is a first-class surface, not only the
  // Tasks module's calendar view. Reads the existing task/reminder/booking APIs.
  calendar: "/calendar",
  salesPipeline: "/sales-pipeline",
  deals: "/deals",
  interactionTimeline: "/interaction-timeline",
  quotations: "/quotations",
  quotationCreate: "/quotations/create",
  // No page exists at /quotations/[id] — using this 404s. Route to `quotations`
  // (with a highlight param) or `quotationRevise` instead.
  quotationDetail: (id: string) => `/quotations/${id}`,
  quotationRevise: (id: string) => `/quotations/${id}/revise`,
  pendingApprovals: "/quotations/pending-approvals",
  notifications: "/notifications",
  reminders: "/reminders",
  sla: "/sla",
  bookingConfirmation: "/booking-confirmation",
  roomRequests: "/room-requests",
  reservationStatus: "/reservation-status",
  operationalHandover: "/operational-handover",
  depositPayment: "/deposit-payment",
  frontOfficeHandover: "/front-office-handover",
  reporting: "/reporting",
  aiAssistant: "/ai-assistant",
  profile: "/profile",
  activityLogs: "/activity-logs",
} as const;
