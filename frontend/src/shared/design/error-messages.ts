/**
 * Error-code → human copy — Website UI Blueprint §3.13 and §12.
 *
 * > "Server errors surface `errorCode` from the API envelope; the UI maps to
 * >  friendly messages using a lookup table (all codes from spec §2.2 & 2.3)."
 *
 * **Why map at all when the backend already sends a message?** Two reasons the
 * blueprint calls out. First, several backend messages are written for an
 * engineer (`"Transition from PENDING to CONFIRMED is not allowed for this
 * actor."`) rather than a sales rep. Second, one message is still Vietnamese
 * (`PAYMENT_CONFLICT`, spec §12.1) while the rest of the product is English —
 * §10.11 asks for an English display string with the server text preserved
 * underneath in `details`.
 *
 * **The server text is never discarded.** `resolveApiError` returns both, and
 * the UI shows the friendly line with the raw server message available as
 * secondary detail. Nothing here changes a status, a rule or a workflow — it
 * changes wording only.
 */

import { parseApiError } from "@/services/api_error";

export type ErrorCopy = {
  /** Short title for a section- or route-level error card. */
  title: string;
  /** One sentence the user can act on. */
  message: string;
  /** Optional nudge toward the thing that unblocks them. */
  hint?: string;
  /** Whether retrying the identical request could plausibly succeed. */
  retriable?: boolean;
};

/**
 * Codes are grouped by the module that raises them so a reader can find the
 * entry beside its sibling rules.
 */
const ERROR_COPY: Record<string, ErrorCopy> = {
  // ── Generic envelope codes ────────────────────────────────────────────────
  VALIDATION_ERROR: {
    title: "Check the details",
    message: "Some fields need attention before this can be saved.",
  },
  MALFORMED_REQUEST: {
    title: "Couldn't read that request",
    message: "The form sent something the server could not parse. Please retry.",
    retriable: true,
  },
  INVALID_ARGUMENT: {
    title: "Check the details",
    message: "One of the values supplied is not valid.",
  },
  ACCESS_DENIED: {
    title: "No access",
    message: "You do not have permission to do this.",
    hint: "Ask an administrator to grant the required permission.",
  },
  RESOURCE_NOT_FOUND: {
    title: "Not found",
    message: "That record no longer exists, or was never visible to you.",
  },
  ALREADY_PROCESSED: {
    title: "Someone got there first",
    message:
      "This record was just changed by someone else. Refresh to see the current state.",
    retriable: true,
  },
  DATA_CONSTRAINT_VIOLATION: {
    title: "Couldn't save",
    message:
      "A value is too long, required, or already in use. Review the form and try again.",
  },
  BUSINESS_RULE_VIOLATION: {
    title: "Not allowed right now",
    message: "This action isn't permitted in the record's current state.",
  },
  UPLOAD_TOO_LARGE: {
    title: "File too large",
    message: "The file exceeds the 5 MB limit.",
  },
  INTERNAL_SERVER_ERROR: {
    title: "Server problem",
    message: "Something went wrong on our end. Please try again shortly.",
    retriable: true,
  },

  // ── Auth / account ────────────────────────────────────────────────────────
  ACCOUNT_LOCKED: {
    title: "Account locked",
    message: "Your account has been locked.",
    hint: "Contact your administrator to unlock it.",
  },
  ACCOUNT_NOT_PROVISIONED: {
    title: "No account here yet",
    message: "This email isn't set up in Leadora.",
    hint: "Ask an administrator to add your account.",
  },
  INCORRECT_CURRENT_PASSWORD: {
    title: "Wrong current password",
    message: "The current password you entered doesn't match.",
  },
  SAME_PASSWORD: {
    title: "Pick a different password",
    message: "The new password must differ from your current one.",
  },
  WEAK_PASSWORD: {
    title: "Password too weak",
    message:
      "Use at least one lowercase letter, one uppercase letter, one digit and one symbol.",
  },
  // Split from WEAK_PASSWORD because the two failures need different advice: adding a symbol
  // does not fix a four-character password, and the complexity wording sent users looking for
  // the wrong problem.
  PASSWORD_TOO_SHORT: {
    title: "Password too short",
    message: "Use at least 6 characters.",
  },

  // ── Leads ─────────────────────────────────────────────────────────────────
  DUPLICATE_LEAD: {
    title: "This lead already exists",
    message: "Another lead already uses this email or phone number.",
    hint: "Open the existing lead instead of creating a second one.",
  },
  LEAD_ALREADY_CONVERTED: {
    title: "Already converted",
    message: "This lead has already become a customer.",
  },
  LEAD_LOST: {
    title: "Lead is closed",
    message: "A lost lead can't be converted.",
    hint: "Reopen it first if the customer came back.",
  },
  LEAD_UNASSIGNED: {
    title: "Assign an owner first",
    message: "A lead must belong to a sales rep before its status can change.",
  },
  LEAD_NOT_QUALIFIED: {
    title: "Not qualified yet",
    message:
      "Leads convert once qualified — or with a manager's documented approval.",
  },
  LEAD_LOCKED: {
    title: "Locked record",
    message: "Converted leads are kept as history and can no longer be edited.",
  },
  LEAD_INVALID_TRANSITION: {
    title: "Can't move to that status",
    message: "Leads advance one stage at a time: New → Contacted → Qualified.",
  },
  LEAD_NOT_READY_FOR_FOLLOW_UP: {
    title: "Missing follow-up details",
    message:
      "A lead in active follow-up needs a phone or email, a source and an interested service.",
  },
  INVALID_FILTER: {
    title: "Filter not understood",
    message: "One of the filters couldn't be applied.",
  },

  // ── Customers ─────────────────────────────────────────────────────────────
  DUPLICATE_CUSTOMER_EMAIL: {
    title: "Email already used",
    message: "Another customer already has this email address.",
  },
  DUPLICATE_CUSTOMER_PHONE: {
    title: "Phone already used",
    message: "Another customer already has this phone number.",
  },
  CUSTOMER_COMPANY_REQUIRED: {
    title: "Company name required",
    message: "A corporate customer must name its company.",
  },

  // ── Quotations ────────────────────────────────────────────────────────────
  NO_MANAGER_AVAILABLE: {
    title: "No manager to approve",
    message:
      "This discount needs approval, but no manager account exists to give it.",
  },
  QUOTATION_NOT_REVISABLE: {
    title: "Can't revise this version",
    message: "Only draft, sent, interested, rejected or revision-pending quotations can be revised.",
  },
  QUOTATION_INVALID_STATUS: {
    title: "Wrong status for this action",
    message: "The quotation isn't in a state that allows this.",
  },
  INVALID_RECIPIENT_CONTACT: {
    title: "Recipient details missing",
    message: "Sending by email needs a valid recipient email address.",
  },
  LOST_REASON_REQUIRED: {
    title: "Reason required",
    message: "Record why the customer rejected this quotation.",
  },

  // ── Deals ─────────────────────────────────────────────────────────────────
  DEAL_STATE_CONFLICT: {
    title: "Deal is closed",
    message: "A closed deal can't be changed.",
  },
  WORKFLOW_CONSTRAINT_VIOLATION: {
    title: "Blocked by the workflow",
    message: "Another record in this deal's chain prevents the change.",
  },
  ROLE_RESTRICTION: {
    title: "Manager action",
    message: "Only a manager or admin can assign work to someone else.",
  },

  // ── Bookings & payments ───────────────────────────────────────────────────
  BOOKING_TRANSITION_INVALID: {
    title: "Not your transition to make",
    message: "Your role can't move a booking between these two statuses.",
  },
  PAYMENT_CONFLICT: {
    title: "Payment already taken",
    message:
      "This booking has a paid payment, so it can't be cancelled or rejected.",
    hint: "Arrange the refund outside the system first.",
  },
  INVALID_DATES: {
    title: "Check the dates",
    message: "Check-out must be after check-in.",
  },
  CUSTOMER_MISSING: {
    title: "Customer missing",
    message: "This record needs a customer before it can continue.",
  },

  // ── Tasks ─────────────────────────────────────────────────────────────────
  TASK_COMPLETION_NOTE_REQUIRED: {
    title: "Add a completion note",
    message: "Record what happened before closing this task.",
  },
  TASK_ALREADY_RESOLVED: {
    title: "Already closed",
    message: "This task has already been completed or cancelled.",
  },
  INVALID_SCHEDULE: {
    title: "Check the schedule",
    message: "The end time must be later than the start time.",
  },

  // ── Room confirmation gate ────────────────────────────────────────────────
  ROOM_NOT_REQUESTED: {
    title: "Rooms not requested",
    message: "Ask the Reservation team to confirm these rooms first.",
  },
  ROOM_PENDING_CONFIRMATION: {
    title: "Waiting on Reservation",
    message: "The Reservation team hasn't answered this room request yet.",
  },
  ROOM_REJECTED: {
    title: "Rooms unavailable",
    message: "The Reservation team could not confirm these rooms.",
  },
  ROOM_CONFIRMATION_STALE: {
    title: "Confirmation out of date",
    message:
      "The room type, dates or quantity changed after the confirmation was given.",
    hint: "Raise a fresh room request.",
  },
  ROOM_HOLD_EXPIRED: {
    title: "Room hold expired",
    message: "The hold the Reservation team promised has lapsed.",
  },
};

/** Copy used when the server sent no recognised code. */
const GENERIC: ErrorCopy = {
  title: "Something went wrong",
  message: "Please try again.",
  retriable: true,
};

export type ResolvedApiError = ErrorCopy & {
  errorCode?: string;
  status?: number;
  /** The backend's own message, kept verbatim for support and for `details`. */
  serverMessage: string;
  fieldErrors?: Record<string, string | string[]>;
};

/**
 * The single entry point screens use. Combines the transport-level parse from
 * `services/api_error.ts` with the blueprint's copy table.
 */
export function resolveApiError(error: unknown): ResolvedApiError {
  const parsed = parseApiError(error);
  const mapped = parsed.errorCode ? ERROR_COPY[parsed.errorCode] : undefined;

  // No code but a real network failure — say so plainly rather than blaming input.
  const isOffline = !parsed.status && !parsed.errorCode;

  const base: ErrorCopy =
    mapped ??
    (isOffline
      ? {
          title: "You're offline",
          message: "Can't reach the server. Check your connection and try again.",
          retriable: true,
        }
      : {
          title: statusTitle(parsed.status),
          message: parsed.message,
          retriable: isRetriableStatus(parsed.status),
        });

  return {
    ...GENERIC,
    ...base,
    errorCode: parsed.errorCode,
    status: parsed.status,
    serverMessage: parsed.message,
    fieldErrors: parsed.fieldErrors,
    retriable: base.retriable ?? isRetriableStatus(parsed.status),
  };
}

function statusTitle(status?: number): string {
  if (!status) return "Something went wrong";
  if (status === 401) return "Session expired";
  if (status === 403) return "No access";
  if (status === 404) return "Not found";
  if (status === 409) return "Conflict";
  if (status === 422) return "Not allowed right now";
  if (status >= 500) return "Server problem";
  return "Request failed";
}

/**
 * A 403 or 404 will not fix itself, so offering "Retry" there only teaches the
 * user the button does nothing (§3.13). Retry is offered for transport faults,
 * 5xx, rate limits and timeouts.
 */
function isRetriableStatus(status?: number): boolean {
  if (!status) return true; // network / unknown
  if (status >= 500) return true;
  return status === 408 || status === 429;
}

/** Convenience for toasts: the friendly one-liner. */
export function apiErrorCopy(error: unknown): string {
  return resolveApiError(error).message;
}
