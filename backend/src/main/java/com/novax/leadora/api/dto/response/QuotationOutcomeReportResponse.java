package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.5 — Quotation Outcome Report. Quotation counts by status, approval and customer-response
 * outcomes, expirations, and the quotation → booking conversion rate.
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

    /**
     * Live quotations in the period — superseded revisions excluded.
     *
     * <p>BR-22 makes an edit after approval a new version row and marks the previous one
     * SUPERSEDED. Counting those keeps the denominator growing with every negotiation round while
     * the outcome count cannot, so a heavily negotiated month would show falling acceptance for no
     * commercial reason.
     */
    private long total;

    /** Superseded revisions in the period, reported separately rather than hidden. */
    private long superseded;

    // Current-status snapshot (subset of the full breakdown below).
    private long sent;
    private long rejected;
    private long expired;
    private long accepted;
    private long converted;

    /**
     * Quotations that ever cleared approval, counted from {@code approved_at}.
     *
     * <p>Not the number sitting at status APPROVED: sending overwrites that status, so the count of
     * rows still parked there measures a dispatch backlog, not approvals.
     */
    private long approved;

    /** Quotations the approver turned down (REJECTED and never approved). */
    private long rejectedByApprover;

    /** approved / (approved + rejectedByApprover) — the approval workflow's success rate. */
    private double approvalRate;

    /**
     * (accepted + converted) / total — customer acceptance.
     *
     * <p>CONVERTED counts: a quotation that became a booking is the strongest form of acceptance,
     * and excluding it made this report contradict the acceptance figure on UC-23.1.
     */
    private double acceptanceRate;

    /** converted / total — quotation → booking conversion. */
    private double conversionRate;

    /** Full breakdown by status (only statuses with at least one quotation). */
    private List<StatusRow> byStatus;

    @JsonPOJOBuilder(withPrefix = "")
    public static class QuotationOutcomeReportResponseBuilder {
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
}
