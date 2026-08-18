package com.novax.leadora.application.usecase.reporting;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The UC-23.1 filter set: a period, optionally one rep, optionally one lead segment.
 *
 * <p>The segment fields describe the *lead* a record originated from. For leads that is direct; for
 * deals, quotations, bookings and payments it resolves through the customer, so "corporate leads
 * from the Website" means "records belonging to customers who came in as a corporate website lead".
 * That indirection is worth stating out loud, because a booking has no source of its own and any
 * other reading of the filter would be invented.
 *
 * @param dateFrom          inclusive day, business time zone; null = no lower bound
 * @param dateTo            inclusive day, business time zone; null = no upper bound
 * @param assignedUserId    narrow to one rep; null = whole team
 * @param source            lead source, exact match; null = any
 * @param interestedService lead's interested service, exact match; null = any
 * @param corporate         corporate vs individual leads; null = both
 */
public record SalesPerformanceFilter(
        LocalDate dateFrom,
        LocalDate dateTo,
        UUID assignedUserId,
        String source,
        String interestedService,
        Boolean corporate) {

    public static SalesPerformanceFilter period(LocalDate dateFrom, LocalDate dateTo) {
        return new SalesPerformanceFilter(dateFrom, dateTo, null, null, null, null);
    }

    /**
     * True when no segment filter was supplied, letting the query skip the segment lookup entirely.
     * Computed here rather than in SQL so the common case — the screen as it opens — reads as a
     * single boolean rather than three null checks per branch.
     */
    public boolean segmentOff() {
        return blank(source) && blank(interestedService) && corporate == null;
    }

    /**
     * Cache key. Every filter has to appear, or two different reports share one cache entry.
     *
     * <p>The separator is escaped rather than merely chosen. {@code source} and
     * {@code interestedService} arrive as free-form query parameters, so a plain join lets a caller
     * shift the boundary between two fields and land on a key that already belongs to a different
     * filter set — and be served that report instead of their own.
     */
    public String cacheKey() {
        return String.join("_",
                esc(str(dateFrom)), esc(str(dateTo)), esc(str(assignedUserId)),
                esc(str(source)), esc(str(interestedService)), esc(str(corporate)));
    }

    /** Doubling the separator inside each part makes the join injective. */
    private static String esc(String part) {
        return part.replace("_", "__");
    }

    /** Hibernate binds these straight into {@code CAST(? AS uuid/text/boolean)}; null stays null. */
    public String ownerIdParam() {
        return assignedUserId == null ? null : assignedUserId.toString();
    }

    public String sourceParam() {
        return blank(source) ? null : source;
    }

    public String serviceParam() {
        return blank(interestedService) ? null : interestedService;
    }

    public String corporateParam() {
        return corporate == null ? null : corporate.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
