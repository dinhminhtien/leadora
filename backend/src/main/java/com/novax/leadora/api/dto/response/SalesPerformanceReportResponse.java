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
 * UC-23.1 — Sales Performance Statistics Report. Aggregated server-side across leads, deals,
 * quotations, bookings and payments for an optional date range, with a per-rep breakdown.
 *
 * <p>{@code @JsonDeserialize(builder = ...)} is load-bearing, not decoration: this response is
 * cached in Redis as JSON, and a Lombok {@code @Builder} class exposes no constructor Jackson can
 * call and no setters to populate. Without it every cache read throws, the configured
 * {@code CacheErrorHandler} swallows it as a warning, and the cache silently never hits.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = SalesPerformanceReportResponse.SalesPerformanceReportResponseBuilder.class)
public class SalesPerformanceReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    // Leads
    private long leadsCreated;
    private long qualifiedLeads;         // leads that reached QUALIFIED (UC-23.1)
    private long leadsConverted;
    private double leadConversionRate;   // %

    // Deals — two time axes, deliberately.
    //
    // The counts below split into "opened in the period" (created_at) and "closed in the period"
    // (closed_at), because those answer different questions and mixing them is what made the old
    // win rate wrong. dealsTotal and dealsWon therefore do NOT belong to the same population: a
    // deal can be opened in one month and won in another.

    /** Deals *opened* in the period. */
    private long dealsTotal;
    /** Of the deals opened in the period, how many are still running. */
    private long dealsOpen;
    /** Expected revenue of the still-open deals opened in the period. */
    private BigDecimal pipelineValue;

    /** Deals *closed won* in the period, whenever they were opened. */
    private long dealsWon;
    /** Deals *closed lost* in the period. */
    private long dealsLost;
    /** % won / (won + lost), over deals closed in the period. */
    private double winRate;
    /** Expected revenue of the deals won in the period. */
    private BigDecimal wonValue;

    // Quotations
    private long quotationsCreated;
    private long quotationsAccepted;
    private double quotationAcceptanceRate; // %

    // Bookings & revenue
    private long bookingsConfirmed;
    /**
     * % of quotations raised in the period that went on to become a booking.
     *
     * <p>Measured as CONVERTED quotations over quotations created, so numerator and denominator are
     * the same population. The previous form divided confirmed bookings by created quotations —
     * two disjoint sets, which could exceed 100% and answered no question anyone asked.
     */
    private double quotationToBookingRate;
    private BigDecimal revenue;          // sum of PAID payments, placed by paid_at

    private List<RepRow> reps;

    @JsonPOJOBuilder(withPrefix = "")
    public static class SalesPerformanceReportResponseBuilder {
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = RepRow.RepRowBuilder.class)
    public static class RepRow {
        private String name;
        private long leads;
        private long dealsWon;
        private BigDecimal wonValue;
        private long bookings;
        private BigDecimal revenue;
        /** True for the synthetic row holding records with no assignee, so the UI can style it. */
        private boolean unassigned;

        @JsonPOJOBuilder(withPrefix = "")
        public static class RepRowBuilder {
        }
    }
}
