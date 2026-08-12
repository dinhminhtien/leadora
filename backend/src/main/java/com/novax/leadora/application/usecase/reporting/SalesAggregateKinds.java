package com.novax.leadora.application.usecase.reporting;

/**
 * The discriminators emitted by {@code DealRepository.salesPerformanceAggregates}.
 *
 * <p>Two use cases now read that statement — the Sales Performance report and the rep scorecard —
 * and a discriminator is a contract between the SQL and everything that parses it. Left as string
 * literals in each reader, renaming a branch in the query would compile fine and silently zero a
 * KPI in whichever file was forgotten.
 */
public final class SalesAggregateKinds {

    private SalesAggregateKinds() {
    }

    /** Leads raised in the period, bucketed by their current status. */
    public static final String LEAD = "LEAD";
    /** Leads that reached QUALIFIED during the period, dated by the event. */
    public static final String LEAD_QUALIFIED = "LEAD_QUALIFIED";
    /** Conversions that happened during the period. */
    public static final String LEAD_CONVERTED = "LEAD_CONVERTED";
    /** Of the leads raised in the period, those that have ever converted. */
    public static final String LEAD_COHORT_CONVERTED = "LEAD_COHORT_CONVERTED";
    /** Deals opened in the period, bucketed by status. */
    public static final String DEAL_OPENED = "DEAL_OPENED";
    /** Deals closed in the period, bucketed by status. */
    public static final String DEAL_CLOSED = "DEAL_CLOSED";
    /**
     * Quotations raised in the period, bucketed by {@code STATUS|SENT|REPLACED}.
     *
     * <p>The bucket carries facts, not a verdict: the status alone cannot say whether a quotation
     * was replaced by a revision or ever reached a customer, and those two facts decide whether it
     * belongs in a rate at all. {@code QuotationOutcomeClassifier} turns the bucket into an outcome
     * so that rule lives in one place instead of once per report.
     */
    public static final String QUOTATION = "QUOTATION";
    /** Bookings confirmed in the period, dated by the confirmation event. */
    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    /** Payments marked PAID in the period. */
    public static final String REVENUE = "REVENUE";

    public static final String BUCKET_QUALIFIED = "QUALIFIED";
    public static final String BUCKET_CONVERTED = "CONVERTED";
    public static final String BUCKET_CONFIRMED = "CONFIRMED";
    public static final String BUCKET_PAID = "PAID";
}
