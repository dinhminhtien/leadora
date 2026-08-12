/**
 * Canonical status → tone bindings — Website UI Blueprint §2.7.
 *
 * > "Status is a first-class citizen. Every entity has a canonical status pill.
 * >  Colour and shape are consistent across the whole app." (§1, principle 2)
 *
 * **Why this file exists.** Before the redesign each screen carried its own
 * `STATUS_CONFIG` literal, so `QUALIFIED` was teal on the lead list, emerald on
 * the lead detail and green-500 in the pipeline card. A reader had to re-learn
 * the colour language on every screen. The bindings below are declared exactly
 * once and the blueprint forbids varying them per screen.
 *
 * **This is presentation only.** No status value is invented, renamed, removed
 * or reordered — every key here already exists in `Website_System_Spec.md`.
 * Tasks in particular keep exactly `OPEN / COMPLETED / CANCELLED`; `OVERDUE` is
 * a *computed* visual flag (`status === OPEN && endAt < now`), never a stored
 * status, and is therefore rendered as a separate badge beside the pill rather
 * than replacing it (§10.14.6).
 */

export type StatusTone =
  | "primary"
  | "success"
  | "warning"
  | "danger"
  | "info"
  | "muted"
  | "teal";

/** Every status-bearing domain the UI renders a pill for. */
export type StatusDomain =
  | "task"
  | "lead"
  | "dealStatus"
  | "dealStage"
  | "quotation"
  | "booking"
  | "inventory"
  | "roomRequest"
  | "handover"
  | "readiness"
  | "payment"
  | "sla"
  | "reminder"
  | "customer"
  | "customerType"
  | "user"
  | "review"
  | "customerResponse";

type ToneMap = Record<string, StatusTone>;

/**
 * §2.7 canonical bindings. Keys are the exact wire values emitted by the
 * backend enums; nothing is aliased so a typo fails visibly rather than
 * silently falling back to a plausible-looking colour.
 */
const DOMAIN_TONES: Record<StatusDomain, ToneMap> = {
  // Exactly three statuses. OVERDUE is computed and handled by `<OverdueBadge>`.
  task: {
    OPEN: "primary",
    COMPLETED: "success",
    CANCELLED: "muted",
  },

  lead: {
    NEW: "info",
    CONTACTED: "primary",
    QUALIFIED: "success",
    CONVERTED: "success",
    LOST: "danger",
  },

  dealStatus: {
    OPEN: "primary",
    WON: "success",
    LOST: "danger",
  },

  // Rendered as a numbered progress bar (§2.7) — tones drive the step markers.
  dealStage: {
    INQUIRY: "muted",
    QUALIFICATION: "info",
    QUOTATION_SENT: "primary",
    NEGOTIATION: "primary",
    PENDING_CONFIRMATION: "warning",
    BOOKING_CONFIRMED: "teal",
    CLOSED_WON: "success",
    CLOSED_LOST: "danger",

    // The deal screens render friendly stage labels rather than wire values
    // ("Proposal" for QUOTATION_SENT, "Contract" for PENDING_CONFIRMATION,
    // "Confirmed" for BOOKING_CONFIRMED). Aliasing them here keeps a single
    // tone table instead of a second one that could drift out of step.
    PROPOSAL: "primary",
    CONTRACT: "warning",
    CONFIRMED: "teal",
  },

  quotation: {
    DRAFT: "muted",
    PENDING_APPROVAL: "warning",
    APPROVED: "success",
    ACCEPTED: "success",
    SENT: "primary",
    INTERESTED: "primary",
    REJECTED: "danger",
    EXPIRED: "muted",
    SUPERSEDED: "muted",
    CLOSED: "muted",
    CONVERTED: "success",
    PENDING_REVISION: "warning",
    PENDING_CUSTOMER_RESPONSE: "primary",
    ACCEPTED_BY_CUSTOMER: "success",
    BOOKING_REQUEST: "success",
  },

  booking: {
    PENDING: "warning",
    CONFIRMED: "primary",
    CHECKED_IN: "success",
    CHECKED_OUT: "muted",
    CANCELLED: "danger",
    REJECTED: "danger",
    NO_SHOW: "danger",
  },

  inventory: {
    ALLOCATED: "primary",
    AVAILABLE: "success",
    RELEASED: "muted",
  },

  roomRequest: {
    PENDING: "warning",
    CONFIRMED: "success",
    REJECTED: "danger",
    SUPERSEDED: "muted",
  },

  handover: {
    DRAFT: "muted",
    SUBMITTED: "info",
    ACKNOWLEDGED: "primary",
    READY: "success",
  },

  readiness: {
    PENDING_REVIEW: "warning",
    REVIEWED: "info",
    READY_FOR_ARRIVAL: "success",
    NEED_CLARIFICATION: "danger",
  },

  payment: {
    PENDING: "warning",
    PAID: "success",
    FAILED: "danger",
    CANCELLED: "muted",
    EXPIRED: "muted",
  },

  sla: {
    ACTIVE: "primary",
    BREACHED: "danger",
    RESOLVED: "success",
    // Display statuses surfaced by GET /sla/monitoring
    WITHIN_SLA: "success",
    WARNING: "warning",
  },

  reminder: {
    PENDING: "warning",
    DONE: "success",
    OVERDUE: "danger",
    CANCELLED: "muted",
  },

  customer: {
    ACTIVE: "success",
    INACTIVE: "muted",
  },

  customerType: {
    INDIVIDUAL: "info",
    CORPORATE: "teal",
  },

  user: {
    ACTIVE: "success",
    INACTIVE: "muted",
    LOCKED: "danger",
  },

  review: {
    PENDING: "warning",
    REVIEWED: "success",
    DISMISSED: "muted",
  },

  customerResponse: {
    ACCEPTED: "success",
    REJECTED: "danger",
    PENDING: "warning",
    INTERESTED: "primary",
    NEED_REVISION: "warning",
  },
};

/**
 * A few statuses read better with a word other than the raw enum. Only where
 * the enum name is genuinely unclear to a user — never to rename a concept.
 */
const LABEL_OVERRIDES: Partial<Record<StatusDomain, Record<string, string>>> = {
  dealStage: {
    QUOTATION_SENT: "Quotation sent",
    PENDING_CONFIRMATION: "Pending confirmation",
    BOOKING_CONFIRMED: "Booking confirmed",
    CLOSED_WON: "Closed won",
    CLOSED_LOST: "Closed lost",
  },
  readiness: {
    READY_FOR_ARRIVAL: "Ready for arrival",
    NEED_CLARIFICATION: "Needs clarification",
    PENDING_REVIEW: "Pending review",
  },
  booking: { NO_SHOW: "No show", CHECKED_IN: "Checked in", CHECKED_OUT: "Checked out" },
  sla: { WITHIN_SLA: "Within SLA" },
  quotation: {
    PENDING_APPROVAL: "Pending approval",
    PENDING_REVISION: "Pending revision",
    PENDING_CUSTOMER_RESPONSE: "Pending customer response",
    ACCEPTED_BY_CUSTOMER: "Accepted by customer",
    BOOKING_REQUEST: "Booking request",
  },
};

/** The tone for a status, or `muted` when the value is unknown to this build. */
export function statusTone(domain: StatusDomain, value?: string | null): StatusTone {
  if (!value) return "muted";
  return DOMAIN_TONES[domain]?.[value.toUpperCase()] ?? "muted";
}

/**
 * `PENDING_APPROVAL` → `Pending approval`.
 *
 * Sentence case, not Title Case: the blueprint's pill is `text-caption` at
 * weight 600, and Title Case at that size reads as shouting next to the row's
 * body copy.
 */
export function humanizeStatus(
  value?: string | null,
  domain?: StatusDomain,
): string {
  if (!value) return "—";
  const upper = value.toUpperCase();
  const override = domain && LABEL_OVERRIDES[domain]?.[upper];
  if (override) return override;
  const words = upper.replace(/_/g, " ").toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * Statuses that mean "this record is finished" — used to de-emphasise a row
 * (§10.14.6: COMPLETED is reduced-emphasis, CANCELLED is muted + strikethrough).
 */
export function isTerminalStatus(domain: StatusDomain, value?: string | null): boolean {
  if (!value) return false;
  const tone = statusTone(domain, value);
  return tone === "muted" || tone === "success";
}

/**
 * The computed OVERDUE flag — the single definition used by the list, board,
 * calendar and detail surfaces so they can never disagree.
 *
 * Mirrors the backend rule exactly: `status === OPEN && endAt < now`. It is
 * deliberately a function of the record rather than a stored field, because the
 * server does not persist it either (`TaskStatus` has three values).
 */
export function isTaskOverdue(task: {
  status?: string | null;
  endAt?: string | null;
  isOverdue?: boolean | null;
}): boolean {
  // Trust the server's computed flag when it sent one.
  if (typeof task.isOverdue === "boolean") return task.isOverdue;
  if (task.status?.toUpperCase() !== "OPEN" || !task.endAt) return false;
  const due = new Date(task.endAt).getTime();
  return Number.isFinite(due) && due < Date.now();
}
