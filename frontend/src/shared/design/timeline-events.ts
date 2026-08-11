/**
 * Canonical timeline event vocabulary — Website UI Blueprint §9.8.
 *
 * **The problem this fixes.** Every timeline in the product had grown its own
 * icon map: the interaction timeline declared one twice inside a single file,
 * the activity log had `getEntityStyle`, and the customer history timeline drew
 * *every* task event with the same `CheckCircle2`. The result was that a task
 * completion, a quotation approval and a payment all looked alike, so the icon
 * column carried no information — the one job it has.
 *
 * One registry, one meaning per event. A reader learns the vocabulary once and
 * can then scan any history in the product.
 *
 * Icons are named here but not imported, so this stays a plain data module that
 * a server component can read; `timelineEventIcon()` in `timeline-icons.tsx`
 * resolves a name to the actual Lucide component.
 */

import type { StatusTone } from "@/shared/design/status-tokens";

/**
 * Every kind of thing that can appear on a timeline.
 *
 * Grouped by what a reader is actually asking when they scan a history: *which
 * business object*, *what happened to it*, and *how did someone communicate*.
 */
export type TimelineEventKind =
  // ── Business objects ────────────────────────────────────────────────
  | "lead"
  | "customer"
  | "deal"
  | "quotation"
  | "booking"
  | "payment"
  | "task"
  | "reminder"
  | "handover"
  | "feedback"
  | "roomRequest"
  // ── Things that happen to them ──────────────────────────────────────
  | "created"
  | "statusChange"
  | "assignment"
  | "conversion"
  | "approval"
  | "rejection"
  | "completion"
  | "cancellation"
  | "escalation"
  | "slaBreach"
  // ── Communication ───────────────────────────────────────────────────
  | "call"
  | "email"
  | "meeting"
  | "note"
  | "comment"
  // ── System ──────────────────────────────────────────────────────────
  | "file"
  | "notification"
  | "ai"
  | "audit"
  | "system";

export type TimelineEventSpec = {
  /** Lucide icon name, resolved by `timelineEventIcon()`. */
  icon: string;
  /** Short label for the event chip. */
  label: string;
  /** Semantic tone — drives the icon medallion and chip colours. */
  tone: StatusTone;
};

/**
 * The registry.
 *
 * Two rules kept it honest while filling this in:
 *
 * 1. **No icon appears twice.** If two events share a glyph, the timeline cannot
 *    distinguish them, which is the bug this replaces. The one deliberate pair
 *    is `task`/`completion` — a task *object* versus the act of completing
 *    anything — and they are differentiated by tone and by never co-occurring in
 *    the same column.
 * 2. **Tone follows meaning, not decoration.** Anything that blocks or fails is
 *    `danger`; anything awaiting a human is `warning`; anything that advanced
 *    the pipeline is `success`.
 */
export const TIMELINE_EVENTS: Record<TimelineEventKind, TimelineEventSpec> = {
  // Business objects — each gets the same glyph it has in the sidebar, so the
  // nav and the history teach the same association.
  lead:        { icon: "Handshake",         label: "Lead",         tone: "info" },
  customer:    { icon: "Users",             label: "Customer",     tone: "primary" },
  deal:        { icon: "BriefcaseBusiness", label: "Deal",         tone: "primary" },
  quotation:   { icon: "ReceiptText",       label: "Quotation",    tone: "teal" },
  booking:     { icon: "BedDouble",         label: "Booking",      tone: "success" },
  payment:     { icon: "Banknote",          label: "Payment",      tone: "success" },
  task:        { icon: "CalendarCheck",     label: "Task",         tone: "info" },
  reminder:    { icon: "AlarmClock",        label: "Reminder",     tone: "warning" },
  handover:    { icon: "Workflow",          label: "Handover",     tone: "teal" },
  feedback:    { icon: "Star",              label: "Feedback",     tone: "warning" },
  roomRequest: { icon: "Hotel",             label: "Room request", tone: "info" },

  // Lifecycle events.
  created:      { icon: "Plus",            label: "Created",     tone: "muted" },
  statusChange: { icon: "ArrowRightLeft",  label: "Status",      tone: "info" },
  assignment:   { icon: "UserCog",         label: "Assigned",    tone: "info" },
  conversion:   { icon: "GitBranch",       label: "Converted",   tone: "success" },
  approval:     { icon: "ShieldCheck",     label: "Approved",    tone: "success" },
  rejection:    { icon: "ShieldX",         label: "Rejected",    tone: "danger" },
  completion:   { icon: "CheckCircle2",    label: "Completed",   tone: "success" },
  cancellation: { icon: "XCircle",         label: "Cancelled",   tone: "muted" },
  escalation:   { icon: "TrendingUp",      label: "Escalated",   tone: "danger" },
  slaBreach:    { icon: "Gauge",           label: "SLA breach",  tone: "danger" },

  // Communication.
  call:    { icon: "Phone",          label: "Call",    tone: "success" },
  email:   { icon: "Mail",           label: "Email",   tone: "primary" },
  meeting: { icon: "CalendarDays",   label: "Meeting", tone: "info" },
  note:    { icon: "FileText",       label: "Note",    tone: "warning" },
  comment: { icon: "MessageSquare",  label: "Comment", tone: "muted" },

  // System.
  file:         { icon: "Paperclip", label: "File",         tone: "muted" },
  notification: { icon: "Bell",      label: "Notification", tone: "info" },
  ai:           { icon: "Bot",       label: "Assistant",    tone: "teal" },
  audit:        { icon: "History",   label: "Audit",        tone: "muted" },
  system:       { icon: "Cpu",       label: "System",       tone: "muted" },
};

/**
 * Maps a backend activity/entity type string onto a timeline event.
 *
 * Server vocabularies differ per module (`LEAD_CONVERTED`, `quotation`,
 * `PAYMENT_DEPOSIT`…), so this normalises loosely: exact key first, then a
 * substring pass over the action verbs. Anything unrecognised lands on `system`
 * rather than borrowing another event's icon — an unknown event should look
 * unknown, not masquerade as a task.
 */
export function timelineEventKind(
  rawType?: string | null,
  fallback: TimelineEventKind = "system",
): TimelineEventKind {
  if (!rawType) return fallback;
  const type = rawType.toUpperCase();

  // Verb matches first: `LEAD_CONVERTED` is a conversion, not a lead chip.
  const verbs: [string, TimelineEventKind][] = [
    ["CONVERT", "conversion"],
    ["APPROVE", "approval"],
    ["REJECT", "rejection"],
    ["COMPLETE", "completion"],
    ["CANCEL", "cancellation"],
    ["ESCALAT", "escalation"],
    ["SLA", "slaBreach"],
    ["ASSIGN", "assignment"],
    ["REASSIGN", "assignment"],
    ["STATUS", "statusChange"],
    ["STAGE", "statusChange"],
    ["CREATE", "created"],
  ];
  for (const [needle, kind] of verbs) {
    if (type.includes(needle)) return kind;
  }

  const objects: [string, TimelineEventKind][] = [
    ["QUOTATION", "quotation"],
    ["BOOKING", "booking"],
    ["PAYMENT", "payment"],
    ["DEPOSIT", "payment"],
    ["HANDOVER", "handover"],
    ["FEEDBACK", "feedback"],
    ["REMINDER", "reminder"],
    ["ROOM", "roomRequest"],
    ["CUSTOMER", "customer"],
    ["LEAD", "lead"],
    ["DEAL", "deal"],
    ["TASK", "task"],
    ["CALL", "call"],
    ["EMAIL", "email"],
    ["MEETING", "meeting"],
    ["NOTE", "note"],
    ["COMMENT", "comment"],
    ["FILE", "file"],
    ["DOCUMENT", "file"],
    ["NOTIF", "notification"],
    ["AI", "ai"],
    ["CHAT", "ai"],
    ["LOGIN", "audit"],
    ["LOGOUT", "audit"],
    ["PASSWORD", "audit"],
    ["ACCESS", "audit"],
    ["USER", "audit"],
  ];
  for (const [needle, kind] of objects) {
    if (type.includes(needle)) return kind;
  }

  return fallback;
}

export function timelineEventSpec(kind: TimelineEventKind): TimelineEventSpec {
  return TIMELINE_EVENTS[kind];
}
