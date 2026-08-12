/**
 * Calendar-date helpers that stay in the user's own day.
 *
 * <p>`new Date().toISOString().slice(0, 10)` is the obvious way to get "today" as `YYYY-MM-DD`
 * and it is wrong east of Greenwich: it converts to UTC first, so in Vietnam (UTC+7) it returns
 * *yesterday* between midnight and 07:00. Round-tripping is worse — parsing `"2026-08-12"` as
 * local midnight and formatting it back through UTC subtracts a day every single time:
 *
 * <pre>
 * addDays("2026-08-12", 0)  → "2026-08-11"   // before this module existed
 * addDays("2026-08-12", 13) → "2026-08-24"
 * </pre>
 *
 * <p>An allotment grid is nothing but dates, so an off-by-one there silently shows the wrong
 * nights' availability — a class of bug that survives a demo because every number looks
 * plausible; it is simply attached to the wrong day.
 *
 * <p>These are **calendar dates**, not instants: a hotel night belongs to a date, not to a moment
 * in time, so none of this converts through UTC at any point.
 */

/** Formats a `Date` as `YYYY-MM-DD` in the local calendar, never via UTC. */
export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** Today, in the user's own calendar. */
export function todayIso(): string {
  return toIsoDate(new Date());
}

/** Parses `YYYY-MM-DD` as local midnight. Explicit so no caller has to remember the pitfall. */
export function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split("-").map(Number);
  return new Date(year, month - 1, day);
}

/** Shifts a `YYYY-MM-DD` by whole days, staying in the local calendar. */
export function addDays(iso: string, days: number): string {
  const date = parseIsoDate(iso);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}

/** Whole days from `fromIso` to `toIso`; negative when `toIso` is earlier. */
export function daysBetween(fromIso: string, toIso: string): number {
  const from = parseIsoDate(fromIso);
  const to = parseIsoDate(toIso);
  // Both are local midnight, so the difference is whole days even across a DST boundary
  // once rounded — Vietnam has none, but the rounding costs nothing and travels.
  return Math.round((to.getTime() - from.getTime()) / 86_400_000);
}

/** Weekday and day/month labels for a grid column header. */
export function shortDateParts(iso: string): { weekday: string; day: string } {
  const date = parseIsoDate(iso);
  return {
    weekday: date.toLocaleDateString(undefined, { weekday: "short" }),
    day: date.toLocaleDateString(undefined, { day: "2-digit", month: "2-digit" }),
  };
}
