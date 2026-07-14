package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.5 — Quotation Outcome Report. Quotation counts by status, approval / customer-response
 * outcomes, expirations, and the quotation-to-booking conversion rate. Aggregated server-side over
 * quotations for an optional date range.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationOutcomeReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    private long total;

    // Key milestone counts (subset of the full status breakdown below).
    private long sent;
    private long approved;
    private long rejected;
    private long expired;
    private long accepted;
    private long converted;

    /** approved / (approved + rejected) — approval workflow success. */
    private double approvalRate;
    /** accepted / total — customer acceptance. */
    private double acceptanceRate;
    /** converted / total — quotation → booking conversion. */
    private double conversionRate;

    /** Full breakdown by status (only statuses with at least one quotation). */
    private List<StatusRow> byStatus;

    @Getter
    @Builder
    public static class StatusRow {
        private String status;  // enum name
        private String label;   // human label
        private long count;
    }
}
