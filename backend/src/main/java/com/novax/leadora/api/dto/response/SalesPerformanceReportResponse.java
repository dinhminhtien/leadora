package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.1 — Sales Performance Statistics Report. Aggregated server-side across leads, deals,
 * quotations, bookings and payments for an optional date range, with a per-rep breakdown.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SalesPerformanceReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    // Leads
    private long leadsCreated;
    private long leadsConverted;
    private double leadConversionRate;   // %

    // Deals
    private long dealsTotal;
    private long dealsOpen;
    private long dealsWon;
    private long dealsLost;
    private double winRate;              // % won / (won + lost)
    private BigDecimal wonValue;
    private BigDecimal pipelineValue;    // expected revenue of OPEN deals

    // Quotations
    private long quotationsCreated;
    private long quotationsAccepted;
    private double quotationAcceptanceRate; // %

    // Bookings & revenue
    private long bookingsConfirmed;
    private BigDecimal revenue;          // sum of PAID payments

    private List<RepRow> reps;

    @Getter
    @Builder
    public static class RepRow {
        private String name;
        private long leads;
        private long dealsWon;
        private BigDecimal wonValue;
        private long bookings;
        private BigDecimal revenue;
    }
}
