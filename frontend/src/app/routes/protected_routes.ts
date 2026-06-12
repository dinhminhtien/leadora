import { ROUTE_PATHS } from "@/app/routes/route_paths";

export const PROTECTED_ROUTES = [
  ROUTE_PATHS.dashboard,
  ROUTE_PATHS.identityAccess,
  ROUTE_PATHS.customerFeedback,
  ROUTE_PATHS.leads,
  ROUTE_PATHS.customerProfiles,
  ROUTE_PATHS.followUpTasks,
  ROUTE_PATHS.salesPipeline,
  ROUTE_PATHS.deals,
  ROUTE_PATHS.interactionTimeline,
  ROUTE_PATHS.quotations,
  ROUTE_PATHS.notifications,
  ROUTE_PATHS.reminders,
  ROUTE_PATHS.sla,
  ROUTE_PATHS.bookingConfirmation,
  ROUTE_PATHS.reservationStatus,
  ROUTE_PATHS.operationalHandover,
  ROUTE_PATHS.depositPayment,
  ROUTE_PATHS.frontOfficeHandover,
  ROUTE_PATHS.reporting,
  ROUTE_PATHS.aiAssistant,
] as const;
