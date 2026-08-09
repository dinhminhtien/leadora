package com.novax.leadora.application.usecase.chat.time;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * A calendar period a chat question is asking about, plus the words to describe it back.
 *
 * <p>Deliberately expressed in {@link LocalDate}, not instants: "today" is a property of the
 * business calendar, not of the server's clock. The conversion to instants happens once, in
 * {@link #start(ZoneId)} / {@link #end(ZoneId)}, against the business timezone — see
 * {@link ChatClock} for why that distinction is not academic.
 *
 * <p>{@link #allTime()} is the neutral value, used when a question names no period at all. It is
 * not "the widest range" but "no filter": both bounds stay null and every query drops its date
 * predicate, so the plan is exactly what it was before this feature existed.
 *
 * @param from  inclusive first day, null for unbounded
 * @param to    inclusive last day, null for unbounded
 * @param label how to name this period in the prompt, e.g. {@code "today (2026-08-09)"}
 */
public record ChatDateRange(LocalDate from, LocalDate to, String label) {

    private static final ChatDateRange ALL_TIME = new ChatDateRange(null, null, "all time");

    /** No date filter at all — the behaviour for a question that names no period. */
    public static ChatDateRange allTime() {
        return ALL_TIME;
    }

    public boolean isAllTime() {
        return from == null && to == null;
    }

    /** Start of the first day in the business timezone, or null when unbounded. */
    public OffsetDateTime start(ZoneId zone) {
        return from == null ? null : from.atStartOfDay(zone).toOffsetDateTime();
    }

    /**
     * End of the last day in the business timezone, or null when unbounded.
     *
     * <p>Inclusive by construction ({@link LocalTime#MAX}), so callers compare with {@code <=} and
     * a record created at 23:59 on the last day is still inside the range.
     */
    public OffsetDateTime end(ZoneId zone) {
        return to == null ? null : to.atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime();
    }
}
