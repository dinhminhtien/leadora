package com.novax.leadora.application.usecase.chat.time;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

    /**
     * Bounds used when the range is open-ended.
     *
     * <p><b>Why sentinels rather than null.</b> An unbounded side was once passed as null and the
     * queries guarded it with {@code (:from IS NULL OR created_at >= :from)}. That reads well and
     * does not work: PostgreSQL has to know a parameter's type when it prepares the statement, and
     * a bare parameter whose only use is {@code ? IS NULL} gives it nothing to infer from, so every
     * such query failed with <i>could not determine data type of parameter</i>. It failed the same
     * way whether a date was supplied or not — the type is decided at prepare time, before any
     * value is bound — so the listings stopped returning anything at all.
     *
     * <p>Real bounds remove the problem instead of working around it: the parameter is always a
     * timestamp, so there is nothing to infer, and {@code created_at >= ?} stays sargable and can
     * still use the index. It also matches what {@code ReportingUtils} already does for the
     * Reporting module, so the two agree on what "no filter" means.
     */
    private static final OffsetDateTime UNBOUNDED_START =
            OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private static final OffsetDateTime UNBOUNDED_END =
            OffsetDateTime.of(2100, 12, 31, 23, 59, 59, 999_999_999, ZoneOffset.UTC);

    /** Start of the first day in the business timezone; a far-past sentinel when unbounded. */
    public OffsetDateTime start(ZoneId zone) {
        return from == null ? UNBOUNDED_START : from.atStartOfDay(zone).toOffsetDateTime();
    }

    /**
     * End of the last day in the business timezone; a far-future sentinel when unbounded.
     *
     * <p>Inclusive by construction ({@link LocalTime#MAX}), so callers compare with {@code <=} and
     * a record created at 23:59 on the last day is still inside the range.
     */
    public OffsetDateTime end(ZoneId zone) {
        return to == null ? UNBOUNDED_END : to.atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime();
    }
}
