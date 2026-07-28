/**
 * Lead form validation and status-transition rules.
 *
 * **Why this file exists.** The same validation lived in three places — the
 * create drawer in `LeadListScreen`, the edit form in `LeadDetailScreen`, and
 * now the detail drawer. Three copies of "a phone must be 10–11 digits" is three
 * chances for them to disagree, and the user only finds out when one form
 * accepts what another rejects.
 *
 * Every rule here mirrors a server rule; none is invented:
 * - name/email/phone/company shapes match the create + update DTO constraints
 * - the status ladder matches `UpdateLeadUseCase.validateStatusTransition`
 */

import type { LeadStatus, UpdateLeadPayload } from "@/services/lead_service";

/* ------------------------------------------------------------------ *
 * Validation
 * ------------------------------------------------------------------ */

export type LeadFieldErrors = {
  fullName?: string;
  email?: string;
  phone?: string;
  companyName?: string;
};

/**
 * Letters in any script (Vietnamese included), spaces, and the punctuation real
 * names use. Digits and other symbols are rejected.
 */
const NAME_ALLOWED = /^[\p{L}\s.'-]+$/u;
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type ValidatableLead = {
  fullName?: string;
  email?: string;
  phone?: string;
  companyName?: string;
  isCorporate?: boolean;
};

export function validateLead(form: ValidatableLead): LeadFieldErrors {
  const errors: LeadFieldErrors = {};

  const name = form.fullName?.trim() ?? "";
  if (!name) {
    errors.fullName = "Full name is required";
  } else if (/\d/.test(name)) {
    errors.fullName = "Full name cannot contain numbers";
  } else if (!NAME_ALLOWED.test(name)) {
    errors.fullName = "Full name cannot contain special characters";
  }

  if (form.email && !EMAIL_SHAPE.test(form.email)) {
    errors.email = "Invalid email format (e.g. name@domain.com)";
  }

  if (form.phone) {
    const digits = form.phone.replace(/\s/g, "");
    if (/[^\d]/.test(digits)) {
      errors.phone = "Phone number can only contain digits (no letters or symbols)";
    } else if (!/^\d{10,11}$/.test(digits)) {
      errors.phone = "Phone number must be 10–11 digits";
    }
  }

  // BR-09's lead-side equivalent: an organization lead must name its company.
  if (form.isCorporate && !form.companyName?.trim()) {
    errors.companyName = "Company name is required for an organization";
  }

  return errors;
}

export function hasErrors(errors: LeadFieldErrors): boolean {
  return Object.keys(errors).length > 0;
}

/**
 * An empty assignee string must become `undefined`: the backend field is a UUID
 * and `""` fails to deserialize, while an absent value leaves the assignment
 * untouched.
 */
export function toUpdatePayload(form: UpdateLeadPayload): UpdateLeadPayload {
  return { ...form, assignedUserId: form.assignedUserId || undefined };
}

/* ------------------------------------------------------------------ *
 * Status ladder
 * ------------------------------------------------------------------ */

export const LEAD_PIPELINE: { status: LeadStatus; label: string }[] = [
  { status: "NEW", label: "New" },
  { status: "CONTACTED", label: "Contacted" },
  { status: "QUALIFIED", label: "Qualified" },
  { status: "CONVERTED", label: "Converted" },
];

export const LEAD_STATUS_LABEL: Record<LeadStatus, string> = {
  NEW: "New",
  CONTACTED: "Contacted",
  QUALIFIED: "Qualified",
  CONVERTED: "Converted",
  LOST: "Lost",
};

/**
 * One-directional flow: New → Contacted → Qualified. No skipping, no going back.
 * `CONVERTED` is reached only through the conversion flow, never the dropdown —
 * `UpdateLeadUseCase` rejects it with `LEAD_INVALID_TRANSITION` otherwise.
 */
const NEXT_STATUS: Record<LeadStatus, LeadStatus | null> = {
  NEW: "CONTACTED",
  CONTACTED: "QUALIFIED",
  QUALIFIED: null,
  CONVERTED: null,
  LOST: null,
};

/**
 * Status choices offered when editing: the current stage, the single next stage,
 * and Lost (an active lead can always be marked lost). Converted is terminal and
 * locked, matching BR-08.
 */
export function allowedStatusOptions(current: LeadStatus): LeadStatus[] {
  if (current === "CONVERTED") return ["CONVERTED"];
  const options: LeadStatus[] = [current];
  const next = NEXT_STATUS[current];
  if (next) options.push(next);
  if (current !== "LOST") options.push("LOST");
  return options;
}

/** Terminal states reject every edit server-side (BR-08 / lost is terminal). */
export function isLeadLocked(status?: string | null): boolean {
  const s = (status ?? "").toUpperCase();
  return s === "CONVERTED" || s === "LOST";
}

/**
 * BR-06 / the assignment gate: an unassigned lead is a draft that stays `NEW`
 * until a manager assigns it, and it cannot be converted
 * (`LEAD_UNASSIGNED`, 422).
 */
export function canConvertLead(lead: {
  status?: string | null;
  assignedUserId?: string | null;
}): boolean {
  if (isLeadLocked(lead.status)) return false;
  return !!lead.assignedUserId;
}
