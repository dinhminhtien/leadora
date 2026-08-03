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
import { validatePhone } from "@/shared/utils/phone";

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
export const NAME_ALLOWED = /^[\p{L}\s.'-]+$/u;
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type ValidatableLead = {
  fullName?: string;
  email?: string;
  phone?: string;
  companyName?: string;
  isCorporate?: boolean;
};

/**
 * The one lead form validator.
 *
 * <p><b>There were two, and they disagreed about the rule that matters most.</b>
 * A near-copy called `validateLeadForm` lived in `LeadEditDrawer.tsx` and served
 * the create drawer and the edit drawer; this one served the inline edit on the
 * detail overview. Same name rules, same phone rule — but only the copy checked
 * that a lead has a phone *or* an email, so the same lead could be saved from
 * the detail page with neither and be refused from the create drawer. The
 * server refuses both (`LEAD_NOT_REACHABLE`, 422); the forms simply disagreed
 * about when to say so.
 *
 * <p>That is precisely the drift this file's header describes, grown back in a
 * fourth place. `LeadEditDrawer` now re-exports this function rather than
 * carrying its own.
 */
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

  const phoneError = validatePhone(form.phone);
  if (phoneError) errors.phone = phoneError;

  // A lead with neither is a name nobody can follow up (`LeadContactPolicy`).
  // Reported on both fields so whichever one the user is looking at explains
  // itself — and only when neither is malformed, so the specific "invalid
  // email" message is not overwritten by the general one.
  if (!form.email?.trim() && !form.phone?.trim() && !errors.email && !errors.phone) {
    errors.email = "Enter an email or a phone number";
    errors.phone = "Enter an email or a phone number";
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
 * BR-05 / BR-06 — whether this lead may advance a stage right now, and what is stopping it.
 *
 * <p>Two surfaces ask the same question from different sources. The Edit form asks about the
 * *form*, so the answer changes as the user types and the status option unlocks under their
 * fingers; the Overview quick-action asks about the *saved lead*, because there is no form open to
 * fix anything in. Feeding both from one function is the point: they were about to be a fourth and
 * fifth copy of "a lead needs a source and an interested service", which is the drift this file
 * exists to stop.
 *
 * <p>The refusals mirror `UpdateLeadUseCase` exactly — `LEAD_UNASSIGNED` (dòng 198) and
 * `LEAD_NOT_READY_FOR_FOLLOW_UP` (`assertQualifyingDetailsPresent`). Anything this returns as
 * allowed, the server accepts.
 */
export type LeadStatusGate = {
  /** The one stage the lead may move to, or `null` when it is at the end of the ladder. */
  next: LeadStatus | null;
  /** BR-05 fields still missing, in the wording used inside the UI copy. */
  missingForFollowUp: string[];
  /** BR-06 — a lead nobody owns is a draft and cannot change status at all. */
  unassigned: boolean;
  /** True when a *forward* move would be refused. Marking Lost is never blocked. */
  forwardBlocked: boolean;
};

export function leadStatusGate(lead: {
  status: LeadStatus;
  email?: string | null;
  phone?: string | null;
  source?: string | null;
  interestedService?: string | null;
  assignedUserId?: string | null;
}): LeadStatusGate {
  const missingForFollowUp = [
    !lead.phone?.trim() && !lead.email?.trim() ? "phone or email" : null,
    !lead.source?.trim() ? "source" : null,
    !lead.interestedService?.trim() ? "interested service" : null,
  ].filter(Boolean) as string[];

  const unassigned = !lead.assignedUserId;

  return {
    next: NEXT_STATUS[lead.status],
    missingForFollowUp,
    unassigned,
    forwardBlocked: unassigned || missingForFollowUp.length > 0,
  };
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
