import type { Status, StatusTone } from "@/shared/types/status";

export const STATUS_LABELS: Record<Status, string> = {
  draft: "Draft",
  pending: "Pending",
  active: "Active",
  completed: "Completed",
  cancelled: "Cancelled",
  overdue: "Overdue",
};

export const STATUS_TONES: Record<Status, StatusTone> = {
  draft: "neutral",
  pending: "warning",
  active: "info",
  completed: "success",
  cancelled: "danger",
  overdue: "danger",
};
