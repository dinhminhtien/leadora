"use client";

import { DashboardScreen } from "@/features/reporting/screens/DashboardScreen";

/**
 * Sales Staff home (UC-8/9/10/11): the personal sales pipeline dashboard —
 * my leads, deals, follow-up tasks and quotation activity. Record-level scoping
 * to the acting user is enforced by the backend.
 */
export function StaffDashboardScreen() {
  return <DashboardScreen />;
}
