package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;

/**
 * The single definition of what became of a quotation, used by every report that publishes a
 * quotation figure.
 *
 * <p>The reporting queries do not classify. They emit the three raw facts a decision needs — the
 * status, whether it was ever dispatched, and whether a revision replaced it — encoded in the
 * aggregate bucket, and the rule is applied here. Writing the rule in SQL would mean writing it
 * once per report, in a second language, which is exactly how the three reports came to disagree.
 */
public final class QuotationOutcomeClassifier {

    /** Separates the three facts inside an aggregate bucket: {@code STATUS|SENT|REPLACED}. */
    public static final String BUCKET_SEPARATOR = "|";
    /** Bucket token for a quotation that has a dispatch timestamp. */
    public static final String SENT = "SENT";
    /** Bucket token for a quotation that was never dispatched. */
    public static final String UNSENT = "UNSENT";
    /** Bucket token for a quotation a newer version points at. */
    public static final String REPLACED = "REPLACED";
    /** Bucket token for a quotation no revision replaced. */
    public static final String CURRENT = "CURRENT";

    private QuotationOutcomeClassifier() {
    }

    /**
     * @param status   the quotation's current status — it says where the row stands, never what
     *                 happened, because it is overwritten as the quotation advances. A null status
     *                 reads as still in flight rather than throwing: a value added to the enum and
     *                 not yet considered here should leave one figure unclassified, not take a
     *                 whole management report down
     * @param sent     whether a dispatch was ever recorded; separates a lost negotiation from work
     *                 that never left the building
     * @param replaced whether a revision points at this row, which no later status change undoes
     */
    public static QuotationOutcome classify(QuotationStatus status, boolean sent, boolean replaced) {
        if (replaced || status == QuotationStatus.SUPERSEDED) {
            return QuotationOutcome.SUPERSEDED;
        }
        if (status == null) {
            return QuotationOutcome.OPEN;
        }
        return switch (status) {
            case ACCEPTED, CONVERTED -> QuotationOutcome.WON;
            // Terminal without a sale. Whether it is a loss or an abandonment turns on one fact:
            // did a customer ever see it. An approver rejection and an expired draft both land here
            // with no dispatch, and neither is evidence about the team's ability to win business.
            case REJECTED, EXPIRED, CLOSED -> sent ? QuotationOutcome.LOST : QuotationOutcome.ABANDONED;
            default -> QuotationOutcome.OPEN;
        };
    }

    /** Classifies one aggregate bucket of the form {@code STATUS|SENT|REPLACED}. */
    public static QuotationOutcome classifyBucket(String bucket) {
        return classify(statusOf(bucket), isSent(bucket), isReplaced(bucket));
    }

    /** The status half of an aggregate bucket, or null when it names no known status. */
    public static QuotationStatus statusOf(String bucket) {
        try {
            return QuotationStatus.valueOf(split(bucket)[0]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isSent(String bucket) {
        String[] parts = split(bucket);
        return parts.length > 1 && SENT.equals(parts[1]);
    }

    public static boolean isReplaced(String bucket) {
        String[] parts = split(bucket);
        return parts.length > 2 && REPLACED.equals(parts[2]);
    }

    /** Builds a bucket the way the queries do — used by tests to keep both sides honest. */
    public static String bucket(QuotationStatus status, boolean sent, boolean replaced) {
        return (status == null ? "" : status.name())
                + BUCKET_SEPARATOR + (sent ? SENT : UNSENT)
                + BUCKET_SEPARATOR + (replaced ? REPLACED : CURRENT);
    }

    private static String[] split(String bucket) {
        return bucket == null ? new String[] { "" } : bucket.split("\\" + BUCKET_SEPARATOR, -1);
    }
}
