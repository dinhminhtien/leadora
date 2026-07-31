/**
 * The one phone rule the web uses, mirroring `LeadFieldLimits.PHONE_PATTERN`
 * on the server.
 *
 * **Why this file exists.** There were three rules, and the strictest one was
 * winning silently:
 *
 * - `Input.tsx` (`phoneOnly`) truncated typing at 10 digits and demanded a
 *   Vietnamese mobile prefix,
 * - `lead-rules.ts` accepted 10–11 digits,
 * - `LeadEditDrawer.tsx` carried its own copy of the 10–11 rule.
 *
 * Because the component capped input at 10 characters, the 10–11 rule could
 * never actually see an eleven-digit number — the looser rule was unreachable
 * code. A rule that another layer makes unreachable is worse than a wrong rule:
 * it reads as intentional and does nothing.
 *
 * The server now accepts 10 or 11 digits with no prefix constraint, so a
 * landline (`028…`), an eleven-digit number, and a number taken down from a
 * corporate switchboard all record. The trade-off is deliberate: `1234567890`
 * passes too. This checks the shape of the field, not whether the number
 * reaches anyone.
 */

/** 10 or 11 digits. Blank is handled by callers and means "no phone". */
export const PHONE_PATTERN = /^\d{10,11}$/;

/** Longest value the inputs allow, so typing cannot outrun the rule. */
export const PHONE_MAX_DIGITS = 11;

export const PHONE_MESSAGE = "Phone number must be 10 or 11 digits";

/**
 * Strips the separators people type and folds the international prefix onto the
 * national one — `+84 912 345 678` and `0912345678` are the same number, and
 * only the second is storable.
 */
export function normalizePhone(value: string | null | undefined): string {
  const compact = (value ?? "").replace(/[\s.\-()]/g, "");
  return compact.startsWith("+84") ? `0${compact.slice(3)}` : compact;
}

/** The validation message for [value], or `null` when it is acceptable. */
export function validatePhone(value: string | null | undefined): string | null {
  const digits = normalizePhone(value);
  if (!digits) return null; // optional — the phone-or-email rule is separate
  if (/\D/.test(digits)) {
    return "Phone number can only contain digits (no letters or symbols)";
  }
  return PHONE_PATTERN.test(digits) ? null : PHONE_MESSAGE;
}
