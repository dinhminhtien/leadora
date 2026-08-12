package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.5 — Quotation Outcome Report.
 *
 * <p>Two sections, because the report answers two questions that a single time axis was conflating.
 * <b>Cohort</b> follows the quotations written in the period and asks what became of them.
 * <b>Activity</b> counts the approvals, replies, dispatches and conversions that landed in the
 * period whoever wrote them and whenever. On a created_at-only report the second question had no
 * answer at all: a quotation raised in June and converted in July appeared in neither month's
 * conversion figure.
 *
 * <p>Nullable rates and averages are null when nothing in the period could establish them. That is
 * not the same as zero, and {@link #dataGaps} says in words which ones are thin.
 *
 * <p>See {@link SalesPerformanceReportResponse} for why the Jackson builder wiring is required.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = QuotationOutcomeReportResponse.QuotationOutcomeReportResponseBuilder.class)
public class QuotationOutcomeReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    /** The zone the day boundaries were resolved in, so the period is unambiguous. */
    private String timezone;

    // ── Cohort: quotations created in the period ──────────────────────────────

    /**
     * Live quotations created in the period — revisions of them excluded.
     *
     * <p>BR-22 makes an edit a new version row, and the replaced row is <em>supposed</em> to be
     * marked SUPERSEDED. It frequently is not: whatever touches the row next leaves it at EXPIRED or
     * CLOSED, so filtering on that status let most replaced rows back into the denominator and one
     * negotiation was counted once per round. A row is excluded here when a revision pointing at it
     * exists, which is a fact no later status change can overwrite.
     */
    private long total;

    /** Rows dropped from {@link #total} because a revision replaced them, reported not hidden. */
    private long revisedAway;

    private long won;

    /** Dispatched to the customer and closed without a sale — a negotiation that was lost. */
    private long lost;

    /**
     * Terminal but never dispatched: rejected by the approver, or expired while still a draft.
     *
     * <p>Counted apart from {@link #lost} because no customer was ever involved. Folded together,
     * a rep who wrote twenty quotations and submitted none would score a near-zero win rate for
     * work nobody outside the company saw, and the lost value would carry the whole draft book.
     */
    private long abandoned;

    private long stillOpen;

    private BigDecimal wonValue;
    private BigDecimal lostValue;
    private BigDecimal abandonedValue;
    private BigDecimal openValue;

    /**
     * won / (won + lost) — null when nothing in the cohort was decided by a customer.
     *
     * <p>The denominator is the quotations a customer actually decided. Quotations still awaiting an
     * answer are excluded because they are not losses yet — including them drags the rate down
     * hardest in the most recent period, exactly when a manager is most likely to read it. Abandoned
     * quotations are excluded because no customer ever saw them. {@link #stillOpen} and
     * {@link #abandoned} carry both remainders, so nothing is hidden by either choice.
     */
    private Double winRate;

    /** Quotations of the cohort that reached CONVERTED. */
    private long cohortConverted;

    /** cohortConverted / total — quotation → booking for this batch of work. */
    private double conversionRate;

    /** Cohort quotations that ever cleared approval, read from {@code approved_at}. */
    private long cohortApproved;
    private long cohortNeverApproved;

    private long cohortSent;
    private long cohortNeverSent;

    /** Mean hours from writing a quotation to dispatching it; null when none was sent. */
    private Double avgHoursToSend;

    /** Dispatched, then expired without the customer ever replying — a follow-up that lapsed. */
    private long expiredNoReply;
    /** Expired after the customer had replied at least once. */
    private long expiredAfterReply;
    /** Expired while still a draft or awaiting approval — it never went out at all. */
    private long expiredNeverSent;

    /** Discount bands over the cohort, with the money each band carries. */
    private List<CountRow> discountBands;

    /** Full breakdown of the cohort by current status. */
    private List<StatusRow> byStatus;

    // ── Activity: what happened in the period ─────────────────────────────────

    /** Approval decisions recorded in the period, from {@code quotation_approval_history}. */
    private long decisions;
    private long decisionsApproved;
    private long decisionsRejected;

    /**
     * Decisions that sent the quotation back for revision.
     *
     * <p>Its own outcome, deliberately. The previous report had two branches, approved and rejected,
     * so a revision request vanished — and it is the decision that costs the sales rep the most time.
     */
    private long decisionsRevisionRequested;

    /**
     * decisionsApproved / decisions — first-pass approval rate; null when nobody decided anything.
     *
     * <p>Revision requests are in the denominator: they are decisions that did not approve. A rate
     * computed over approvals and rejections alone would read as 100% for a period in which every
     * quotation was sent back.
     */
    private Double firstPassApprovalRate;

    /** Quotations whose {@code approved_at} falls in the period. */
    private long approvalsStamped;

    /** Mean hours from writing to approval, over {@link #approvalsStamped}; null when none. */
    private Double avgHoursToApprove;

    /** Customer replies recorded in the period, from {@code quotation_customer_responses}. */
    private long replies;
    private long repliesAccepted;
    private long repliesRejected;
    private long repliesInterested;
    private long repliesNeedRevision;

    /** repliesAccepted / replies — null when there were no replies. */
    private Double replyAcceptanceRate;

    /**
     * Mean hours from dispatch to the customer's reply; null when no reply could be timed.
     *
     * <p>Only replies whose quotation has a {@code sent_at} can be timed — {@link #repliesTimed}
     * says how many that was, so a fast-looking average over two samples is visible as such.
     */
    private Double avgHoursToReply;
    private long repliesTimed;

    private long sentInPeriod;
    private long convertedInPeriod;
    private BigDecimal convertedValue;
    private long closedInPeriod;
    private long expiredInPeriod;

    /** Recorded loss reasons, most frequent first. Usually far thinner than the loss count. */
    private List<CountRow> lostReasons;

    // ── Transparency ──────────────────────────────────────────────────────────

    /** Plain-language notes on what this period could not establish. Empty when nothing is thin. */
    private List<String> dataGaps;

    /** Per-preparer breakdown — {@code quotations.created_by}, whoever wrote the document. */
    private List<StaffRow> staff;

    /**
     * True when {@link #staff} was capped and therefore does <em>not</em> sum to {@link #total}.
     *
     * <p>The UI tells the reader the column reconciles with the headline, which stops being true
     * past the cap. Rather than drop the claim for everyone, the claim is made conditional on this.
     */
    private boolean staffTruncated;

    @JsonPOJOBuilder(withPrefix = "")
    public static class QuotationOutcomeReportResponseBuilder {
    }

    /** A named bucket with a count and, where the metric has one, a money total. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = CountRow.CountRowBuilder.class)
    public static class CountRow {
        private String key;
        private String label;
        private long count;
        private BigDecimal value;

        @JsonPOJOBuilder(withPrefix = "")
        public static class CountRowBuilder {
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = StatusRow.StatusRowBuilder.class)
    public static class StatusRow {
        private String status;  // enum name
        private String label;   // human label
        private long count;

        @JsonPOJOBuilder(withPrefix = "")
        public static class StatusRowBuilder {
        }
    }

    /** One preparer's cohort outcomes. Rates are null when that person has nothing decided. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = StaffRow.StaffRowBuilder.class)
    public static class StaffRow {
        private String name;
        private long prepared;
        private long won;
        private long lost;
        private long abandoned;
        private Double winRate;
        private BigDecimal wonValue;
        private long sent;
        private Double avgHoursToSend;
        /** True for the row holding quotations with no recorded preparer. */
        private boolean unattributed;

        @JsonPOJOBuilder(withPrefix = "")
        public static class StaffRowBuilder {
        }
    }
}
