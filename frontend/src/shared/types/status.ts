export type Status =
  | "draft"
  | "pending"
  | "active"
  | "completed"
  | "cancelled"
  | "overdue";

export type TaskStatus = "OPEN" | "COMPLETED" | "CANCELLED";

export type StatusTone = "neutral" | "info" | "success" | "warning" | "danger";

/**
 * Get UI badge tone for task status.
 * OVERDUE is calculated dynamically (task.status == OPEN && isOverdue == true)
 */
export function getTaskStatusTone(status: TaskStatus, isOverdue?: boolean): StatusTone {
  if (isOverdue && status === "OPEN") return "danger";
  if (status === "OPEN") return "warning";
  if (status === "COMPLETED") return "success";
  if (status === "CANCELLED") return "neutral";
  return "neutral";
}

/**
 * Get UI label for task status.
 * Shows "Overdue" if task is OPEN and past due.
 */
export function getTaskStatusLabel(status: TaskStatus, isOverdue?: boolean): string {
  if (isOverdue && status === "OPEN") return "Overdue";
  if (status === "OPEN") return "Open";
  if (status === "COMPLETED") return "Completed";
  if (status === "CANCELLED") return "Cancelled";
  return status;
}

export type TaskTimelineState = "upcoming" | "ongoing" | "overdue" | "completed" | "cancelled";

/**
 * Compute the dynamic timeline sub-state of a task based on current time.
 *
 * OPEN + now < startAt           → upcoming
 * OPEN + startAt <= now <= endAt → ongoing
 * OPEN + now > endAt             → overdue
 * COMPLETED                      → completed
 * CANCELLED                      → cancelled
 */
export function getTaskTimelineState(
  status: TaskStatus,
  startAt?: string | null,
  endAt?: string | null
): TaskTimelineState {
  if (status === "COMPLETED") return "completed";
  if (status === "CANCELLED") return "cancelled";
  const now = new Date();
  if (endAt && new Date(endAt) < now) return "overdue";
  if (startAt && new Date(startAt) > now) return "upcoming";
  return "ongoing";
}

export function getTaskTimelineLabel(state: TaskTimelineState): string {
  switch (state) {
    case "upcoming": return "Upcoming";
    case "ongoing":  return "Ongoing";
    case "overdue":  return "Overdue";
    case "completed": return "Completed";
    case "cancelled": return "Cancelled";
  }
}

export function getTaskTimelineTone(state: TaskTimelineState): StatusTone {
  switch (state) {
    case "upcoming":  return "info";
    case "ongoing":   return "warning";
    case "overdue":   return "danger";
    case "completed": return "success";
    case "cancelled": return "neutral";
  }
}

